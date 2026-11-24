package quallo;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.*;
import static quallo.Model.*;
import static quallo.StoreTest.check;

public final class DesktopTest {
    private static final String STUDIO = "Test studio";
    private static final String PROJECT = "Test game";
    private static final String FIRST = "Build the checkpoint";
    private static final String SECOND = "Fix controller input";
    private static final LocalDate DUE = YearMonth.now().atDay(15);

    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("quallo-desktop-test-");
        try (Store store = new Store(data.resolve("app"))) {
            StudioWindow app = edt(() -> {
                Theme.setup();
                StudioWindow window = new StudioWindow(store, store.load());
                window.setVisible(true);
                return window;
            });
            try {
                Path captures = args.length == 0 ? null : Path.of(args[0]);
                Robot robot = new Robot();
                robot.setAutoDelay(18);
                setup(app, captures);
                editCards(app, data);
                views(app);
                moveAndAutomate(app, robot);
                organize(app);
                exportAndRestore(app, data);
                crossProject(app);
            } finally {
                edt(() -> {
                    for (Window window : Window.getWindows()) window.dispose();
                    return null;
                });
            }
        } finally {
            try (var files = Files.walk(data)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        System.out.println("Desktop workflow checks passed.");
    }

    private static void setup(StudioWindow app, Path captures) throws Exception {
        check(edt(() -> app.state.workspaces.isEmpty() && app.board == null), "empty first launch");
        screenshot(captures, "empty-projects", app);
        click(app, "Open tutorial");
        check(edt(() -> app.section.equals("Tutorial")), "Tutorial opens from the empty Projects screen");
        screenshot(captures, "tutorial", app);
        click(app, "Mark complete");
        check(edt(() -> app.store.load().completedLessons.contains("scope")), "tutorial progress is persisted");
        click(app, "Mark incomplete");
        click(app, "Open Projects");
        click(app.navigation, "Tutorial");
        check(edt(() -> app.section.equals("Tutorial")), "Tutorial is available in the main sidebar");
        click(app, "Open Projects");
        click(app, "New board");
        JDialog dialog = dialog("New project board");
        input(dialog, "Workspace name", STUDIO);
        input(dialog, "Board name", PROJECT);
        click(dialog, "OK");
        check(edt(() -> app.board.name.equals(PROJECT) && app.board.lanes.isEmpty() && app.board.labels.isEmpty()
            && app.board.people.isEmpty() && app.board.fields.isEmpty()), "new board has no starter content");

        click(app.navigation, STUDIO);
        check(edt(() -> findButton(app.navigation, PROJECT) == null && app.board.name.equals(PROJECT)), "collapse only hides sidebar contents");
        check(edt(() -> app.store.load().workspaces.getFirst().collapsed), "workspace collapse is persisted");
        click(app.navigation, STUDIO);
        check(edt(() -> findButton(app.navigation, PROJECT) != null), "expand restores board entry");
        click(app, "Add a list");
        configureList("Add list", "Backlog", "", false);
        click(app, "List");
        configureList("Add list", "Review", "1", false);
        click(app, "List");
        configureList("Add list", "Shipped", "", true);
        check(edt(() -> app.board.lanes.size() == 3 && app.board.lanes.get(1).limit == 1 && app.board.lanes.getLast().done),
            "lists, WIP limit and completion behavior");
        tool(app, "Star board");
        check(edt(() -> app.store.load().firstBoard().favorite), "starred board is saved");
        tool(app, "Remove from starred");

        addBoardOption(app, "Manage labels", "Board labels", "Label", "QA");
        addBoardOption(app, "Manage local assignees", "Local assignees", "Assignee", "Me");
        addBoardOption(app, "Manage custom fields", "Custom fields", "Custom field", "Build");
        check(edt(() -> app.board.labels.containsKey("QA") && app.board.people.contains("Me") && app.board.fields.contains("Build")),
            "board labels, roster and custom fields");
        System.out.println("  Empty startup, tutorial, workspace, boards and list controls passed.");
    }

    private static void configureList(String title, String name, String limit, boolean done) throws Exception {
        JDialog prompt = dialog(title);
        input(prompt, "Name", name);
        input(prompt, "WIP limit", limit);
        if (done) click(prompt, "Complete incoming cards");
        click(prompt, "OK");
    }

    private static void addBoardOption(StudioWindow app, String command, String manager, String editor, String name) throws Exception {
        menu(app, "Board settings", command);
        click(dialog(manager), "Add");
        JDialog create = dialog(editor);
        input(create, "Name", name);
        click(create, "OK");
        click(dialog(manager), "Done");
    }

    private static void editCards(StudioWindow app, Path data) throws Exception {
        click(app, "New card");
        JDialog editor = dialog("New card");
        namedText(editor, "Card title", FIRST);
        click(editor, "Create card");
        click(app, "New card");
        JDialog card = dialog("New card");
        namedText(card, "Card title", SECOND);
        input(card, "Due (YYYY-MM-DD)", "invalid date");
        click(card, "Create card");
        click(dialog("Quallo"), "OK");
        check(edt(() -> app.board.activeCards().size() == 1), "invalid date keeps draft out of the board");
        edt(() -> {
            require(card, JTextArea.class, area -> area.isEditable()).setText("Controller, build \"1\".\nRetest on Linux.");
            return null;
        });
        combo(card, "Type", "Bug");
        combo(card, "Priority", "High");
        input(card, "Start (YYYY-MM-DD)", DUE.minusDays(3).toString());
        input(card, "Due (YYYY-MM-DD)", DUE.toString());
        input(card, "Milestone", "Vertical slice");
        input(card, "Estimate (hours)", "3");
        click(card, "Watch in My work");
        click(card, "QA");
        click(card, "Me");
        input(card, "Build", "1.2.3");

        selectTab(card, "Checklists");
        click(card, "Add checklist");
        JDialog checklist = dialog("Add checklist");
        check(edt(() -> field(checklist, "Name").getText().isEmpty()), "checklist name starts empty");
        input(checklist, "Name", "Regression steps");
        click(checklist, "OK");
        click(card, "Item");
        JDialog item = dialog("Checklist item");
        input(item, "Item", "Retest at the checkpoint");
        combo(item, "Assignee", "Me");
        input(item, "Due (YYYY-MM-DD)", DUE.toString());
        click(item, "Save");
        click(card, "Retest at the checkpoint");

        selectTab(card, "Dependencies");
        click(card, "Add dependency");
        click(dialog("Add dependency"), "OK");
        selectTab(card, "Files");
        Path attachment = Files.writeString(data.resolve("test-report.txt"), "Test report content");
        click(card, "Attach files");
        chooseFile(attachment, true);
        await("attachment display", () -> edt(() -> findButton(card, "Save a copy") != null));
        Path exported = data.resolve("saved-report.txt");
        click(card, "Save a copy");
        chooseFile(exported, false);
        await("exported attachment", () -> Files.isRegularFile(exported));
        check(Files.readString(exported).equals("Test report content"), "downloaded attachment bytes");
        selectTab(card, "Discussion & activity");
        edt(() -> {
            require(card, JTextArea.class, area -> area.isEditable()).setText("Verified a controller issue.");
            return null;
        });
        click(card, "Add comment");
        click(card, "Create card");
        Card created = edt(() -> card(app, SECOND));
        check(created.type.equals("Bug") && created.priority.equals("High") && created.due.equals(DUE)
            && created.start.equals(DUE.minusDays(3)) && created.estimate == 3 && created.milestone.equals("Vertical slice")
            && created.watching && created.assignees.contains("Me") && created.labels.contains("QA")
            && created.fields.get("Build").equals("1.2.3"), "card metadata");
        check(created.checklistDone() == 1 && created.checklists.getFirst().items.getFirst().owner.equals("Me")
            && created.attachments.size() == 1 && created.comments.size() == 1 && created.dependencies.size() == 1
            && created.events.size() >= 2, "checklists, files, comments, activity and dependencies");
        System.out.println("  Card fields, checklist, dependency, attachment upload/download and discussion passed.");
    }

    private static void views(StudioWindow app) throws Exception {
        click(app, "Table");
        check(edt(() -> require(app, JTable.class, table -> true).getRowCount() == 2), "table rows");
        click(app, "Calendar");
        check(edt(() -> findButton(app, SECOND) != null), "calendar displays due task");
        tool(app, "Next month");
        check(edt(() -> findButton(app, SECOND) == null), "calendar navigation");
        click(app, "Today");
        click(app, "Timeline");
        check(edt(() -> find(app, JButton.class, item -> SECOND.equals(item.getAccessibleContext().getAccessibleName())) != null),
            "timeline renders a dated task");
        click(app, "Overview");
        check(edt(() -> find(app, JLabel.class, item -> "Blocked".equals(item.getText())) != null), "overview metrics");
        click(app, "Board");
        edt(() -> { app.search.setText("controller"); return null; });
        await("search results", () -> edt(() -> find(app.page, JComponent.class, item ->
            FIRST.equals(item.getAccessibleContext() == null ? null : item.getAccessibleContext().getAccessibleName())) == null));
        check(edt(() -> app.matchingCards().size() == 1), "search matches card content");
        edt(() -> { app.search.setText(""); return null; });
        click(app, "Filters");
        JDialog filter = dialog("Filter tasks");
        combo(filter, "Status", "Blocked");
        click(filter, "Apply");
        check(edt(() -> app.matchingCards().size() == 1 && app.matchingCards().getFirst().title.equals(SECOND)), "blocked filter");
        click(app, "Filters •");
        click(dialog("Filter tasks"), "Clear all");
        click(app, "My work");
        check(edt(() -> require(app, JTable.class, table -> true).getRowCount() == 1), "assigned and watched work");
        click(app, "Projects");
        click(app, "Workspace table");
        check(edt(() -> require(app, JTable.class, table -> true).getRowCount() == 2), "workspace-wide table");
        click(app.navigation, PROJECT);
        System.out.println("  Board, Table, Calendar, Timeline, Overview, My work, search and filters passed.");
    }

    private static void moveAndAutomate(StudioWindow app, Robot robot) throws Exception {
        menu(app, "Board settings", "Automation rules");
        click(dialog("Local automation"), "Add rule");
        JDialog rule = dialog("Add automation rule");
        combo(rule, "When a card enters", "Shipped");
        combo(rule, "Action", "Add label");
        combo(rule, "Label for Add label", "QA");
        click(rule, "OK");
        click(dialog("Local automation"), "Done");
        Point from = edt(() -> point(app, FIRST));
        Point to = edt(() -> require(app, AbstractButton.class, button -> "Shipped list options".equals(button.getToolTipText()))
            .getLocationOnScreen());
        to.translate(-40, 78);
        robot.mouseMove(from.x, from.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int step = 1; step <= 24; step++) robot.mouseMove(from.x + (to.x - from.x) * step / 24, from.y + (to.y - from.y) * step / 24);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        check(edt(() -> card(app, FIRST).complete && card(app, FIRST).labels.contains("QA")
            && app.board.laneOf(card(app, FIRST)).name.equals("Shipped")), "native drag between lists applies status and rule");

        click(app, "Inbox");
        namedText(app, "Capture title", "Investigate a stutter");
        click(app, "Capture");
        check(edt(() -> app.state.inbox.activeCards().size() == 1), "quick capture");
        click(app, "Move to board");
        JDialog move = dialog("Move card to a project board");
        combo(move, "List", "Review");
        click(move, "OK");
        check(edt(() -> app.state.inbox.activeCards().isEmpty()
            && app.board.laneOf(card(app, "Investigate a stutter")).name.equals("Review")), "file Inbox card onto a board");
        click(app.navigation, PROJECT);
        check(edt(() -> find(app.page, JLabel.class, item -> "1/1".equals(item.getText())) != null), "WIP count after move");
        System.out.println("  Drag-and-drop, local rules, Inbox capture and filing passed.");
    }

    private static void organize(StudioWindow app) throws Exception {
        JDialog edit = edit(app, SECOND);
        menuButton(edit, "Actions", "Save a copy as template");
        menu(app, "Board settings", "Card templates");
        click(dialog("Card templates"), "Create card");
        JDialog fromTemplate = dialog("Create card from template");
        input(fromTemplate, "Card title", "Verify controller on Windows");
        click(fromTemplate, "OK");
        check(edt(() -> card(app, "Verify controller on Windows").checklistTotal() == 1), "card template copies checklist");
        edit = edit(app, "Verify controller on Windows");
        menuButton(edit, "Actions", "Archive card");
        check(edt(() -> app.board.activeCards().stream().noneMatch(card -> card.title.equals("Verify controller on Windows"))), "archive card");
        menu(app, "Board settings", "Archive and restore");
        click(dialog("Board archive"), "Restore");
        check(edt(() -> app.board.activeCards().stream().anyMatch(card -> card.title.equals("Verify controller on Windows"))), "restore card");

        menu(app, "Backlog list options", "Sort cards");
        JDialog sort = dialog("Sort list");
        combo(sort, "Sort by", "Title");
        click(sort, "OK");
        menu(app, "Backlog list options", "Copy list with cards");
        JDialog copy = dialog("Copy list");
        input(copy, "Name", "Regression log");
        click(copy, "OK");
        menu(app, "Regression log list options", "Move list right");
        check(edt(() -> app.board.lanes.get(2).name.equals("Regression log")), "copy and reorder list");
        menu(app, "Regression log list options", "Archive list");
        click(dialog("Archive list"), "OK");
        menu(app, "Board settings", "Archive and restore");
        click(dialog("Board archive"), "Restore");
        check(edt(() -> !app.board.lanes.get(2).archived), "archive and restore list");

        menu(app, "Board settings", "Save board as template");
        JDialog template = dialog("Save board as template");
        input(template, "Name", "Regression template");
        click(template, "OK");
        click(app, "Projects");
        click(app, "Use template");
        JDialog fromBoardTemplate = dialog("Create a board copy");
        input(fromBoardTemplate, "Name", "Release candidate");
        click(fromBoardTemplate, "OK");
        check(edt(() -> app.board.name.equals("Release candidate") && app.board.rules.size() == 1
            && app.board.activeCards().size() >= 4), "board template copies cards and maps automation");
        menu(app, "Board settings", "Archive board");
        click(dialog("Archive board"), "OK");
        click(app, "Archived boards");
        click(dialog("Archived boards"), "Restore");
        check(edt(() -> app.state.workspaces.getFirst().boards.stream()
            .anyMatch(board -> board.name.equals("Release candidate") && !board.archived)), "archive and restore board");
        click(app.navigation, PROJECT);
        System.out.println("  Templates, list sorting/movement, card/list/board archive and restore passed.");
    }

    private static void exportAndRestore(StudioWindow app, Path data) throws Exception {
        Path csv = data.resolve("cards.csv");
        menu(app, "Board settings", "Export cards as CSV");
        chooseFile(csv, false);
        click(dialog("Message"), "OK");
        String report = Files.readString(csv);
        check(report.contains("Fix controller input") && report.contains("build \"\"1\"\".\nRetest on Linux."),
            "CSV has card rows and quotes multiline descriptions");
        Path zip = data.resolve("studio.zip");
        tool(app, "Settings and backup");
        click(dialog("Quallo settings"), "Export backup");
        chooseFile(zip, false);
        click(dialog("Message"), "OK");
        check(Files.size(zip) > 0, "workspace backup file created");
        tool(app, "Settings and backup");
        click(dialog("Quallo settings"), "Restore backup");
        chooseFile(zip, false);
        click(dialog("Restore Quallo backup"), "OK");
        click(dialog("Message"), "OK");
        check(edt(() -> app.state.firstBoard().name.equals(PROJECT)
            && app.state.firstBoard().activeCards().stream().anyMatch(card -> card.title.equals(SECOND) && !card.attachments.isEmpty())), "restored workspace content");
        System.out.println("  CSV export and workspace backup/restore passed.");
    }

    private static void crossProject(StudioWindow app) throws Exception {
        click(app, "Projects");
        click(app, "Workspace");
        input(dialog("New workspace"), "Workspace name", "Shipping");
        click(dialog("New workspace"), "OK");
        tool(app, "Workspace settings for Shipping");
        click(dialog("Workspace settings"), "Rename");
        input(dialog("Rename workspace"), "Name", "Ship room");
        click(dialog("Rename workspace"), "OK");

        click(app.navigation, "Release candidate");
        menu(app, "Board settings", "Move board to workspace");
        click(dialog("Move board"), "OK");
        check(edt(() -> app.state.workspace(app.board).name.equals("Ship room")), "move board to renamed workspace");
        menu(app, "Board settings", "Board details");
        JDialog details = dialog("Board details");
        input(details, "Name", "Release validation");
        click(details, "OK");

        click(app.navigation, PROJECT);
        menuButton(edit(app, SECOND), "Actions", "Duplicate card");
        check(edt(() -> !card(app, SECOND + " copy").attachments.isEmpty()), "duplicate card preserves files");
        menuButton(edit(app, SECOND + " copy"), "Actions", "Move to another board");
        JDialog move = dialog("Move card to a project board");
        combo(move, "Board", "Ship room / Release validation");
        combo(move, "List", "Review");
        click(move, "OK");
        check(edt(() -> app.board.activeCards().stream().noneMatch(card -> card.title.equals(SECOND + " copy"))), "cross-board move removes source");
        click(app.navigation, "Release validation");
        check(edt(() -> app.board.laneOf(card(app, SECOND + " copy")).name.equals("Review")), "cross-board move reaches selected list");

        menuButton(edit(app, SECOND + " copy"), "Actions", "Archive card");
        menu(app, "Board settings", "Archive and restore");
        click(dialog("Board archive"), "Delete permanently");
        click(dialog("Board archive"), "OK");
        check(edt(() -> app.board.lanes.stream().flatMap(lane -> lane.cards.stream())
            .noneMatch(card -> card.title.equals(SECOND + " copy"))), "delete an archived card permanently");

        tool(app, "Settings and backup");
        JDialog settings = dialog("Quallo settings");
        input(settings, "Display name", "Morgan");
        click(settings, "Save profile");
        check(edt(() -> app.store.load().author.equals("Morgan") && app.board.people.contains("Morgan")
            && card(app, SECOND).assignees.contains("Morgan")), "profile rename updates local assignments");

        Lane lane = edt(() -> app.board.firstLane());
        edt(() -> {
            for (int i = 0; i < 45; i++) addCard(app.board, lane, "Pagination task " + i, app.state.author);
            app.changed();
            return null;
        });
        AbstractButton more = edt(() -> require(app.page, AbstractButton.class, button -> button.getText().startsWith("Show ")));
        click(app.page, more.getText());
        check(edt(() -> find(app.page, JComponent.class, item -> item.getAccessibleContext() != null
            && "Pagination task 44".equals(item.getAccessibleContext().getAccessibleName())) != null), "show more cards on a large list");
        System.out.println("  Multiple workspaces, board settings, duplicate/move/delete, profile and pagination passed.");
    }

    private static Card card(StudioWindow app, String title) {
        return app.board.activeCards().stream().filter(card -> card.title.equals(title)).findFirst().orElseThrow();
    }

    private static JDialog edit(StudioWindow app, String title) throws Exception {
        edt(() -> {
            Card card = card(app, title);
            SwingUtilities.invokeLater(() -> app.editCard(app.board, card));
            return null;
        });
        return dialog("Card details");
    }

    private static void menu(Container owner, String tooltip, String action) throws Exception {
        tool(owner, tooltip);
        menuItem(action);
    }

    private static void menuButton(Container owner, String label, String action) throws Exception {
        click(owner, label);
        menuItem(action);
    }

    private static void menuItem(String action) throws Exception {
        edt(() -> {
            for (MenuElement element : MenuSelectionManager.defaultManager().getSelectedPath()) {
                if (element.getComponent() instanceof JPopupMenu menu) {
                    JMenuItem item = find(menu, JMenuItem.class, button -> action.equals(button.getText()));
                    if (item == null) continue;
                    MenuSelectionManager.defaultManager().clearSelectedPath();
                    SwingUtilities.invokeLater(() -> item.doClick(0));
                    return null;
                }
            }
            throw new AssertionError("Menu item missing: " + action);
        });
    }

    private static void click(Container owner, String text) throws Exception {
        edt(() -> {
            AbstractButton button = findButton(owner, text);
            check(button != null, "button visible " + text);
            SwingUtilities.invokeLater(() -> button.doClick(0));
            return null;
        });
    }

    private static void tool(Container owner, String tooltip) throws Exception {
        edt(() -> {
            AbstractButton button = require(owner, AbstractButton.class, item -> tooltip.equals(item.getToolTipText()));
            SwingUtilities.invokeLater(() -> button.doClick(0));
            return null;
        });
    }

    private static void input(Container owner, String label, String value) throws Exception {
        edt(() -> { field(owner, label).setText(value); return null; });
    }

    private static JTextField field(Container owner, String label) {
        return (JTextField) formField(owner, label);
    }

    private static Component formField(Container owner, String label) {
        return require(owner, JLabel.class, item -> label.equals(item.getText()) && item.getLabelFor() != null).getLabelFor();
    }

    private static void namedText(Container owner, String name, String value) throws Exception {
        edt(() -> {
            require(owner, JTextField.class, item -> name.equals(item.getAccessibleContext().getAccessibleName())).setText(value);
            return null;
        });
    }

    private static void combo(Container owner, String label, String value) throws Exception {
        edt(() -> {
            JComboBox<?> box = (JComboBox<?>) formField(owner, label);
            for (int i = 0; i < box.getItemCount(); i++) {
                if (value.equals(Objects.toString(box.getItemAt(i)))) { box.setSelectedIndex(i); return null; }
            }
            throw new AssertionError("Missing " + value + " in " + label);
        });
    }

    private static void selectTab(Container owner, String title) throws Exception {
        edt(() -> {
            JTabbedPane tabs = require(owner, JTabbedPane.class, item -> item.indexOfTab(title) >= 0);
            tabs.setSelectedIndex(tabs.indexOfTab(title));
            return null;
        });
    }

    private static void chooseFile(Path file, boolean multi) throws Exception {
        await("file chooser", () -> edt(() -> visibleChooser() != null));
        edt(() -> {
            JFileChooser chooser = visibleChooser();
            if (multi) chooser.setSelectedFiles(new java.io.File[]{file.toFile()});
            else chooser.setSelectedFile(file.toFile());
            chooser.approveSelection();
            return null;
        });
    }

    private static JFileChooser visibleChooser() {
        for (Window window : Window.getWindows()) {
            if (!window.isShowing()) continue;
            JFileChooser chooser = find(window, JFileChooser.class, item -> true);
            if (chooser != null) return chooser;
        }
        return null;
    }

    private static Point point(Container owner, String title) {
        JComponent tile = require(owner, JComponent.class, item -> item.getAccessibleContext() != null
            && title.equals(item.getAccessibleContext().getAccessibleName()) && item.getTransferHandler() != null);
        Point point = tile.getLocationOnScreen();
        point.translate(40, 35);
        return point;
    }

    private static JDialog dialog(String title) throws Exception {
        await("dialog " + title, () -> edt(() -> visibleDialog(title) != null));
        return edt(() -> visibleDialog(title));
    }

    private static JDialog visibleDialog(String title) {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog dialog && dialog.isShowing() && title.equals(dialog.getTitle())) return dialog;
        }
        return null;
    }

    private static AbstractButton findButton(Container owner, String label) {
        return find(owner, AbstractButton.class, item -> label.equals(item.getText()));
    }

    private static <T extends Component> T require(Container owner, Class<T> type, Predicate<T> predicate) {
        T found = find(owner, type, predicate);
        check(found != null, "Missing " + type.getSimpleName());
        return found;
    }

    private static <T extends Component> T find(Container owner, Class<T> type, Predicate<T> predicate) {
        for (Component item : owner.getComponents()) {
            if (type.isInstance(item) && item.isShowing() && predicate.test(type.cast(item))) return type.cast(item);
            if (item instanceof Container branch) {
                T match = find(branch, type, predicate);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void screenshot(Path folder, String name, Window window) throws Exception {
        if (folder == null) return;
        Files.createDirectories(folder);
        BufferedImage image = edt(() -> {
            BufferedImage render = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D canvas = render.createGraphics();
            window.printAll(canvas);
            canvas.dispose();
            return render;
        });
        ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    }

    private static void await(String description, Callable<Boolean> condition) throws Exception {
        for (int i = 0; i < 60; i++) {
            if (condition.call()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private static <T> T edt(Callable<T> call) throws Exception {
        FutureTask<T> task = new FutureTask<>(call);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
