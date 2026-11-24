package quallo;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import quallo.CardEditor.Choice;
import static quallo.CardEditor.*;
import static quallo.Model.*;
import static quallo.Theme.*;

final class BoardsUi {
    static JComponent projects(StudioWindow app) {
        JPanel content = stack();
        pad(content, 26, 28, 28, 28);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Views.title("Projects", "Manage your game boards, templates, and archived work."), BorderLayout.CENTER);
        JPanel buttons = row(7);
        buttons.add(button("Archived boards", "archive", () -> workspaceArchive(app)));
        buttons.add(button("Workspace", "plus", () -> newWorkspace(app)));
        top.add(buttons, BorderLayout.EAST);
        line(content, top);
        for (Workspace space : app.state.workspaces) {
            gap(content, 23);
            JPanel label = new JPanel(new BorderLayout());
            label.setOpaque(false);
            label.add(text(space.name, 18, true, TEXT), BorderLayout.WEST);
            label.add(button("New board", "plus", () -> createBoard(app, space)), BorderLayout.EAST);
            line(content, label);
            gap(content, 8);
            JPanel boards = stack();
            for (Board board : space.boards.stream().filter(item -> !item.archived).toList()) {
                JPanel tile = new Surface(new BorderLayout(13, 0), SURFACE, LINE, 13);
                pad(tile, 10, 13, 10, 10);
                tile.add(glyph(board.template ? "grid" : "board", color(board.color), 23), BorderLayout.WEST);
                JPanel info = stack();
                line(info, text(board.name, 15, true, TEXT));
                if (!board.description.isBlank()) line(info, text(BoardCanvas.shortText(board.description.replace('\n', ' '), 80), 12, false, MUTED));
                tile.add(info, BorderLayout.CENTER);
                JPanel actions = row(6);
                if (board.template) {
                    actions.add(button("Use template", "plus", () -> cloneBoard(app, space, board, false)));
                    actions.add(button("Archive", "archive", () -> { board.archived = true; app.changed(); }).ghost());
                } else {
                    int count = board.activeCards().size();
                    actions.add(badge(count + (count == 1 ? " card" : " cards"), ACCENT));
                    actions.add(button("Open", "arrow", () -> app.chooseBoard(board)));
                }
                tile.add(actions, BorderLayout.EAST);
                line(boards, tile);
                gap(boards, 7);
            }
            line(content, boards);
        }
        if (app.state.workspaces.isEmpty()) {
            gap(content, 16);
            line(content, primary("Create a workspace", "plus", () -> newWorkspace(app)));
        }
        return scroll(content);
    }

    static void newWorkspace(StudioWindow app) {
        String name = Forms.prompt(app, "New workspace", "Workspace name", "");
        if (name.isBlank()) return;
        app.state.workspaces.add(new Workspace(name));
        app.changed();
    }

    static void workspaceMenu(StudioWindow app, Workspace space) {
        Object[] actions = space.boards.isEmpty() ? new Object[]{"Rename", "New board", "Remove empty workspace", "Cancel"}
            : new Object[]{"Rename", "New board", "Cancel"};
        int choice = JOptionPane.showOptionDialog(app, space.name, "Workspace settings", JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
        if (choice == 0) {
            String name = Forms.prompt(app, "Rename workspace", "Name", space.name);
            if (!name.isBlank()) { space.name = name; app.changed(); }
        } else if (choice == 1) createBoard(app, space);
        else if (choice == 2 && space.boards.isEmpty() && app.confirm("Remove " + space.name + "?", "Remove workspace")) {
            app.state.workspaces.remove(space);
            app.changed();
        }
    }

    static void createBoard(StudioWindow app, Workspace preselected) {
        JTextField name = field("", 23);
        JTextArea description = area("", 3, 25);
        JComboBox<String> starter = Forms.combo(new String[]{"Game production", "Simple Kanban", "Empty board"}, "Game production");
        JComboBox<Choice<Workspace>> spaces = choices(app.state.workspaces, space -> space.name, preselected);
        JPanel form = app.state.workspaces.isEmpty()
            ? Forms.form("Board name", name, "Description", scroll(description), "Starter layout", starter)
            : Forms.form("Board name", name, "Description", scroll(description), "Workspace", spaces, "Starter layout", starter);
        while (Forms.accept(app, "New project board", form)) {
            if (name.getText().isBlank()) {
                app.showError(new IllegalArgumentException("Enter a board name."));
                continue;
            }
            Workspace space = choice(spaces);
            if (space == null) { space = new Workspace("My Studio"); app.state.workspaces.add(space); }
            Board board = switch (Forms.value(starter)) {
                case "Empty board" -> new Board(name.getText().strip());
                case "Simple Kanban" -> newBoard(name.getText().strip(), false);
                default -> newBoard(name.getText().strip(), true);
            };
            board.description = description.getText().strip();
            board.people.add(app.state.author);
            space.boards.add(board);
            app.chooseBoard(board);
            return;
        }
    }

    static void boardMenu(StudioWindow app, JComponent anchor) {
        Board board = app.board;
        showMenu(anchor, menu -> {
            action(menu, "Board details", () -> editBoard(app, board));
            action(menu, "Manage labels", () -> manageLabels(app, board));
            action(menu, "Manage local assignees", () -> manageNames(app, board, true));
            action(menu, "Manage custom fields", () -> manageNames(app, board, false));
            action(menu, "Automation rules", () -> rules(app, board));
            menu.addSeparator();
            action(menu, "Card templates", () -> cardTemplates(app, board));
            action(menu, "Archive and restore", () -> boardArchive(app, board));
            action(menu, "Duplicate board", () -> cloneBoard(app, app.state.workspace(board), board, false));
            action(menu, "Save board as template", () -> cloneBoard(app, app.state.workspace(board), board, true));
            action(menu, "Move board to workspace", () -> moveBoard(app, board));
            action(menu, "Export cards as CSV", () -> Views.exportCsv(app));
            menu.addSeparator();
            action(menu, "Archive board", () -> {
                if (!app.confirm("Archive " + board.name + " and its lists?", "Archive board")) return;
                board.archived = true;
                app.board = app.initialBoard();
                app.state.selectedBoard = app.board == null ? "" : app.board.id;
                app.openSection("Projects");
                app.changed();
            });
        });
    }

    private static void editBoard(StudioWindow app, Board board) {
        JTextField name = field(board.name, 25);
        JTextArea description = area(board.description, 4, 32);
        JComboBox<String> accent = colorChoices(board.color);
        JComboBox<String> art = Forms.combo(new String[]{"Color", "Nightfall illustration"},
            board.cover.equals(SAMPLE_COVER) ? "Nightfall illustration" : "Color");
        JPanel form = Forms.form("Name", name, "Description", scroll(description), "Accent", accent, "Cover", art);
        if (Forms.accept(app, "Board details", form) && !name.getText().isBlank()) {
            board.name = name.getText().strip();
            board.description = description.getText().strip();
            board.color = Forms.value(accent);
            board.cover = "Nightfall illustration".equals(Forms.value(art)) ? SAMPLE_COVER : "";
            app.changed();
        }
    }

    private static void moveBoard(StudioWindow app, Board board) {
        Workspace origin = app.state.workspace(board);
        List<Workspace> spaces = app.state.workspaces.stream().filter(space -> space != origin).toList();
        if (spaces.isEmpty()) {
            JOptionPane.showMessageDialog(app, "Create a second workspace first.");
            return;
        }
        JComboBox<Choice<Workspace>> targets = choices(spaces, space -> space.name, spaces.getFirst());
        if (Forms.accept(app, "Move board", Forms.form("Destination workspace", targets))) {
            origin.boards.remove(board);
            choice(targets).boards.add(board);
            app.changed();
        }
    }

    private static void cloneBoard(StudioWindow app, Workspace space, Board original, boolean template) {
        String title = Forms.prompt(app, template ? "Save board as template" : "Create a board copy",
            "Name", original.template ? original.name : original.name + (template ? " template" : " copy"));
        if (title.isBlank()) return;
        Board board = copyBoard(original, title, app.state.author);
        board.template = template;
        space.boards.add(board);
        if (template) app.changed();
        else app.chooseBoard(board);
    }

    static void editLane(StudioWindow app, Lane lane) {
        JTextField name = field(lane == null ? "" : lane.name, 23);
        JComboBox<String> accent = colorChoices(lane == null ? COLORS.getFirst() : lane.color);
        JTextField limit = field(lane == null || lane.limit == 0 ? "" : String.valueOf(lane.limit), 6);
        JCheckBox done = Forms.check("Complete incoming cards", lane != null && lane.done);
        JPanel form = Forms.form("Name", name, "Color", accent, "WIP limit", limit, "Completion", done);
        while (Forms.accept(app, lane == null ? "Add list" : "List settings", form)) {
            try {
                String title = name.getText().strip();
                if (title.isBlank()) throw new IllegalArgumentException("Enter a list name.");
                int max = limit.getText().isBlank() ? 0 : Integer.parseInt(limit.getText().strip());
                if (max < 0) throw new NumberFormatException();
                Lane updated = lane == null ? new Lane(title, Forms.value(accent)) : lane;
                updated.name = title;
                updated.color = Forms.value(accent);
                updated.limit = max;
                updated.done = done.isSelected();
                if (lane == null) app.board.lanes.add(updated);
                app.changed();
                return;
            } catch (NumberFormatException error) {
                app.showError(new IllegalArgumentException("Enter a positive whole number or leave the WIP limit blank."));
            } catch (IllegalArgumentException error) { app.showError(error); }
        }
    }

    static void laneMenu(StudioWindow app, Lane lane, JComponent anchor) {
        Board board = app.board;
        showMenu(anchor, menu -> {
            action(menu, "List settings", () -> editLane(app, lane));
            action(menu, "Move list left", () -> shift(app, lane, -1));
            action(menu, "Move list right", () -> shift(app, lane, 1));
            action(menu, "Sort cards", () -> sort(app, lane));
            action(menu, "Copy list with cards", () -> copyLane(app, lane));
            action(menu, "Add card from template", () -> cardTemplates(app, board, lane));
            menu.addSeparator();
            action(menu, "Archive list", () -> {
                if (app.confirm("Archive " + lane.name + " and its cards?", "Archive list")) {
                    lane.archived = true;
                    app.changed();
                }
            });
        });
    }

    private static void shift(StudioWindow app, Lane lane, int delta) {
        List<Lane> lists = app.board.lanes;
        int from = lists.indexOf(lane);
        for (int i = from + delta; i >= 0 && i < lists.size(); i += delta) {
            if (!lists.get(i).archived) {
                java.util.Collections.swap(lists, from, i);
                app.changed();
                return;
            }
        }
    }

    private static void sort(StudioWindow app, Lane lane) {
        JComboBox<String> by = Forms.combo(new String[]{"Title", "Due date", "Priority"}, "Due date");
        if (!Forms.accept(app, "Sort list", Forms.form("Sort by", by))) return;
        java.util.Comparator<Card> comparator = switch (Forms.value(by)) {
            case "Priority" -> java.util.Comparator.<Card>comparingInt(card -> PRIORITIES.indexOf(card.priority)).reversed();
            case "Due date" -> java.util.Comparator.comparing(card -> card.due, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            default -> java.util.Comparator.comparing(card -> card.title, String.CASE_INSENSITIVE_ORDER);
        };
        lane.cards.sort(comparator);
        app.changed();
    }

    private static void copyLane(StudioWindow app, Lane old) {
        String name = Forms.prompt(app, "Copy list", "Name", old.name + " copy");
        if (name.isBlank()) return;
        Lane lane = new Lane(name, old.color);
        lane.done = old.done;
        lane.limit = old.limit;
        for (Card card : old.cards) {
            if (card.archived || card.template) continue;
            Card copy = copyCard(card, card.title, app.state.author);
            copy.complete = lane.done;
            lane.cards.add(copy);
        }
        app.board.lanes.add(app.board.lanes.indexOf(old) + 1, lane);
        app.changed();
    }

    private static JComboBox<String> colorChoices(String selection) {
        List<String> options = new ArrayList<>(COLORS);
        if (!options.contains(selection)) options.add(selection);
        JComboBox<String> combo = Forms.combo(options.toArray(String[]::new), selection);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setIcon(new Glyph("dot", color(String.valueOf(value)), 17));
                return this;
            }
        });
        return combo;
    }

    private static void manageLabels(StudioWindow app, Board board) {
        manage(app, "Board labels", () -> new ArrayList<>(board.labels.keySet()),
            previous -> {
                JTextField name = field(previous == null ? "" : previous, 20);
                JComboBox<String> color = colorChoices(board.labels.getOrDefault(previous, COLORS.getFirst()));
                if (!Forms.accept(app, "Label", Forms.form("Name", name, "Color", color))) return;
                String label = name.getText().strip();
                if (!unique(app, label, previous, board.labels.keySet())) return;
                if (previous != null && !previous.equals(label)) {
                    board.labels.remove(previous);
                    eachCard(board, card -> replace(card.labels, previous, label));
                    for (int i = 0; i < board.rules.size(); i++) {
                        Rule rule = board.rules.get(i);
                        if (rule.action().equals("Add label") && rule.value().equals(previous)) {
                            board.rules.set(i, new Rule(rule.laneId(), rule.action(), label));
                        }
                    }
                }
                board.labels.put(label, Forms.value(color));
            }, label -> {
                board.labels.remove(label);
                eachCard(board, card -> card.labels.remove(label));
                board.rules.removeIf(rule -> rule.action().equals("Add label") && rule.value().equals(label));
            });
    }

    private static void manageNames(StudioWindow app, Board board, boolean people) {
        var values = people ? board.people : board.fields;
        String entity = people ? "Assignee" : "Custom field";
        manage(app, people ? "Local assignees" : "Custom fields", () -> new ArrayList<>(values),
            previous -> {
                String name = Forms.prompt(app, entity, "Name", previous == null ? "" : previous);
                if (name.isBlank() || !unique(app, name, previous, values)) return;
                if (name.equals(previous)) return;
                if (previous != null) {
                    if (people) renameAssignee(board, previous, name);
                    else eachCard(board, card -> {
                        String value = card.fields.remove(previous);
                        if (value != null) card.fields.put(name, value);
                    });
                    values.remove(previous);
                }
                values.add(name);
            }, name -> {
                values.remove(name);
                if (people) renameAssignee(board, name, "");
                else eachCard(board, card -> card.fields.remove(name));
            });
    }

    private static boolean unique(StudioWindow app, String name, String previous, java.util.Collection<String> values) {
        if (name.isBlank() || values.contains(name) && !name.equals(previous)) {
            app.showError(new IllegalArgumentException("Enter a unique, non-empty name."));
            return false;
        }
        return true;
    }

    private static void renameAssignee(Board board, String old, String value) {
        eachCard(board, card -> {
            replace(card.assignees, old, value);
            card.checklists.forEach(list -> list.items.forEach(item -> { if (item.owner.equals(old)) item.owner = value; }));
        });
    }

    private static void eachCard(Board board, Consumer<Card> use) {
        board.lanes.forEach(lane -> lane.cards.forEach(use));
    }

    private static void replace(java.util.Set<String> items, String old, String value) {
        if (items.remove(old) && !value.isEmpty()) items.add(value);
    }

    private static void manage(StudioWindow app, String title, java.util.function.Supplier<List<String>> data,
                               Consumer<String> edit, Consumer<String> remove) {
        while (true) {
            JList<String> list = simpleList(data.get());
            Object[] actions = {"Add", "Edit", "Remove", "Done"};
            int result = JOptionPane.showOptionDialog(app, listBox(list), title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, actions, actions[0]);
            if (result < 0 || result == 3) return;
            String selected = list.getSelectedValue();
            if (result == 0) edit.accept(null);
            if (result == 1 && selected != null) edit.accept(selected);
            if (result == 2 && selected != null && app.confirm("Remove " + selected + " from this board and its cards?", title)) {
                remove.accept(selected);
            }
            app.changed();
        }
    }

    private static void rules(StudioWindow app, Board board) {
        while (true) {
            JList<Rule> list = simpleList(board.rules);
            list.setCellRenderer((component, rule, index, selected, focus) -> new DefaultListCellRenderer()
                .getListCellRendererComponent(component, "When a card enters " + (board.lane(rule.laneId()) == null
                    ? "a removed list" : board.lane(rule.laneId()).name) + " → " + rule.action() +
                    (rule.value().isBlank() ? "" : " " + rule.value()), index, selected, focus));
            int result = JOptionPane.showOptionDialog(app, listBox(list), "Local automation", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, new Object[]{"Add rule", "Remove", "Done"}, "Add rule");
            if (result < 0 || result == 2) return;
            if (result == 1) board.rules.remove(list.getSelectedValue());
            if (result == 0) addRule(app, board);
            app.changed();
        }
    }

    private static void addRule(StudioWindow app, Board board) {
        var lanes = board.lanes.stream().filter(lane -> !lane.archived).toList();
        if (lanes.isEmpty()) { app.showError(new IllegalArgumentException("Add a list before creating a rule.")); return; }
        var lane = choices(lanes, candidate -> candidate.name, lanes.getFirst());
        var action = Forms.combo(new String[]{"Complete", "Reopen", "Add label"}, "Complete");
        var label = Forms.optional(board.labels.keySet(), "");
        JPanel form = Forms.form("When a card enters", lane, "Action", action, "Label for Add label", label);
        if (!Forms.accept(app, "Add automation rule", form)) return;
        String value = Forms.value(action).equals("Add label") ? Forms.value(label) : "";
        if (Forms.value(action).equals("Add label") && value.isEmpty()) {
            app.showError(new IllegalArgumentException("Select a label for this rule."));
            return;
        }
        Rule rule = new Rule(choice(lane).id, Forms.value(action), value);
        if (!board.rules.contains(rule)) board.rules.add(rule);
    }

    private static void workspaceArchive(StudioWindow app) {
        List<Archived> entries = new ArrayList<>();
        app.state.workspaces.forEach(space -> space.boards.stream().filter(board -> board.archived)
            .forEach(board -> entries.add(new Archived(space.name + " / " + board.name, () -> board.archived = false,
                () -> { space.boards.remove(board); if (app.board == board) app.board = app.initialBoard(); }))));
        archive(app, "Archived boards", entries);
    }

    private static void boardArchive(StudioWindow app, Board board) {
        List<Archived> entries = new ArrayList<>();
        for (Lane lane : board.lanes) {
            if (lane.archived) entries.add(new Archived("List / " + lane.name, () -> lane.archived = false,
                () -> { lane.cards.forEach(card -> removeReferences(board, card)); board.rules.removeIf(rule -> rule.laneId().equals(lane.id)); board.lanes.remove(lane); }));
            if (!lane.archived) lane.cards.stream().filter(card -> card.archived).forEach(card -> entries.add(new Archived(
                lane.name + " / " + card.title, () -> { card.archived = false; card.log(app.state.author, "Restored the card"); },
                () -> { lane.cards.remove(card); removeReferences(board, card); })));
        }
        archive(app, "Board archive", entries);
    }

    private static void removeReferences(Board board, Card card) {
        eachCard(board, other -> other.dependencies.remove(card.id));
    }

    private record Archived(String name, Runnable restore, Runnable delete) {
        @Override public String toString() { return name; }
    }

    private static void archive(StudioWindow app, String title, List<Archived> entries) {
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(app, "Your archive is empty.", title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        var list = simpleList(entries);
        int result = JOptionPane.showOptionDialog(app, listBox(list), title, JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, new Object[]{"Restore", "Delete permanently", "Close"}, "Restore");
        Archived entry = list.getSelectedValue();
        if (entry == null) return;
        if (result == 0) entry.restore.run();
        else if (result == 1 && app.confirm("Permanently delete " + entry.name + "?", title)) entry.delete.run();
        else return;
        app.changed();
    }

    private static void cardTemplates(StudioWindow app, Board board) {
        cardTemplates(app, board, board.firstLane());
    }

    private static void cardTemplates(StudioWindow app, Board board, Lane preselected) {
        List<Card> templates = board.lanes.stream().filter(lane -> !lane.archived).flatMap(lane -> lane.cards.stream())
            .filter(card -> card.template && !card.archived).toList();
        if (templates.isEmpty() || preselected == null) {
            JOptionPane.showMessageDialog(app, "Use Actions in a card to save it as a template.");
            return;
        }
        var template = choices(templates, card -> card.title, templates.getFirst());
        var lanes = choices(board.lanes.stream().filter(lane -> !lane.archived).toList(), lane -> lane.name, preselected);
        var form = Forms.form("Template", template, "Destination list", lanes);
        int result = JOptionPane.showOptionDialog(app, form, "Card templates", JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, new Object[]{"Create card", "Remove template", "Cancel"}, "Create card");
        Card source = choice(template);
        if (result == 0) {
            String name = Forms.prompt(app, "Create card from template", "Card title", source.title);
            if (name.isBlank()) return;
            Lane target = choice(lanes);
            Card copy = copyCard(source, name, app.state.author);
            copy.complete = target.done;
            target.cards.add(copy);
            applyRules(board, target, copy);
        } else if (result == 1 && app.confirm("Archive " + source.title + "?", "Remove template")) source.archived = true;
        else return;
        app.changed();
    }

    static void settings(StudioWindow app) {
        JTextField name = field(app.state.author, 23);
        JPanel form = Forms.form("Display name", name, "Data folder", text(app.store.directory.toString(), 12, false, MUTED));
        Object[] options = {"Save profile", "Export backup", "Restore backup", "Close"};
        int result = JOptionPane.showOptionDialog(app, form, "Quallo settings", JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (result == 0 && !name.getText().isBlank()) {
            String old = app.state.author, current = name.getText().strip();
            if (old.equals(current)) return;
            app.state.author = current;
            app.state.workspaces.forEach(space -> space.boards.forEach(board -> {
                replace(board.people, old, current);
                renameAssignee(board, old, current);
            }));
            app.changed();
        } else if (result == 1) backup(app);
        else if (result == 2) restore(app);
    }

    private static void backup(StudioWindow app) {
        Path target = chooseFile(app, true, "Quallo backup", "zip", "Quallo-backup.zip");
        if (target == null) return;
        FormsUi.runTask(app, "Writing workspace backup…", () -> { app.store.backup(app.state, target); return target; },
            path -> JOptionPane.showMessageDialog(app, "Backup saved to " + path), app::showError);
    }

    private static void restore(StudioWindow app) {
        Path source = chooseFile(app, false, "Quallo backup", "zip", "");
        if (source == null || !app.confirm("Replace the current workspace with this backup? Quallo will save a recovery copy beside its data folder.", "Restore Quallo backup")) return;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS"));
        Path recover = app.store.directory.resolveSibling("Quallo-before-restore-" + stamp + ".zip");
        FormsUi.runTask(app, "Restoring workspace backup…", () -> {
            app.store.backup(app.state, recover);
            return app.store.restore(source);
        }, state -> {
            app.state = state;
            app.board = app.initialBoard();
            app.openSection("Project");
            app.changed();
            JOptionPane.showMessageDialog(app, "Workspace restored. Recovery copy saved to " + recover);
        }, app::showError);
    }

    static Path chooseFile(StudioWindow app, boolean save, String title, String extension, String suggested) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(title, extension));
        if (save) chooser.setSelectedFile(new java.io.File(suggested));
        int result = save ? chooser.showSaveDialog(app) : chooser.showOpenDialog(app);
        if (result != JFileChooser.APPROVE_OPTION) return null;
        Path path = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (save && !path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) {
            path = path.resolveSibling(path.getFileName() + "." + extension);
        }
        if (save && path.startsWith(app.store.directory.normalize())) {
            app.showError(new IOException("Choose a location outside the Quallo data folder."));
            return null;
        }
        return save && Files.exists(path) && !app.confirm("Replace the existing file?", title) ? null : path;
    }

    private static <T> JList<T> simpleList(List<T> choices) {
        DefaultListModel<T> model = new DefaultListModel<>();
        model.addAll(choices);
        JList<T> list = new JList<>(model);
        list.setFixedCellHeight(28);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (!choices.isEmpty()) list.setSelectedIndex(0);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                putClientProperty("html.disable", true);
                return super.getListCellRendererComponent(list, value, index, selected, focus);
            }
        });
        return list;
    }

    private static JScrollPane listBox(JList<?> list) {
        JScrollPane pane = scroll(list);
        pane.setPreferredSize(new Dimension(430, 190));
        return pane;
    }
}
