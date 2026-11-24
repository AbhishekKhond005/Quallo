package quallo;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import static quallo.Model.*;
import quallo.Theme.Button;
import static quallo.Theme.*;

final class CardEditor extends JDialog {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm").withZone(ZoneId.systemDefault());
    private final StudioWindow app;
    private final Board board;
    private final Lane initialLane;
    private final Card original, draft;
    private final JTextField title, starts, due, milestone, estimate;
    private final JTextArea description;
    private final JComboBox<String> type, priority;
    private final JComboBox<Choice<Lane>> destination;
    private final JCheckBox complete, watching;
    private final Map<String, JCheckBox> labels = new LinkedHashMap<>();
    private final Map<String, JCheckBox> people = new LinkedHashMap<>();
    private final Map<String, JTextField> extras = new LinkedHashMap<>();
    private final JPanel checklistBody = stack(), fileBody = stack(), conversation = stack(), dependencyBody = stack();
    private final JTextArea commentInput = area("", 3, 28);
    private final JTabbedPane tabs = new JTabbedPane();

    static void open(StudioWindow app, Board board, Lane lane, Card card) {
        new CardEditor(app, board, lane, card).setVisible(true);
    }

    private CardEditor(StudioWindow app, Board board, Lane lane, Card card) {
        super(app, card == null ? "New card" : "Card details", true);
        this.app = app;
        this.board = board;
        initialLane = lane;
        original = card;
        draft = card == null ? new Card("") : editableCopy(card);
        if (card == null) draft.complete = lane.done;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(645, 550));
        setSize(760, Math.min(735, app.getHeight() - 25));
        setLocationRelativeTo(app);
        JPanel wrapper = panel(new BorderLayout(0, 14), BASE);
        pad(wrapper, 17, 19, 15, 19);
        setContentPane(wrapper);

        title = field(draft.title, 28);
        title.setFont(font(21, true));
        title.getAccessibleContext().setAccessibleName("Card title");
        JPanel heading = stack();
        line(heading, text(board.name + "  /  " + lane.name, 12, true, ACCENT));
        gap(heading, 8);
        JLabel titleLabel = text("Card title", 12, true, MUTED);
        titleLabel.setLabelFor(title);
        line(heading, titleLabel);
        gap(heading, 4);
        line(heading, title);
        wrapper.add(heading, BorderLayout.NORTH);

        description = area(draft.description, 5, 32);
        type = Forms.combo(TYPES.toArray(String[]::new), draft.type);
        priority = Forms.combo(PRIORITIES.toArray(String[]::new), draft.priority);
        starts = field(Forms.date(draft.start), 14);
        due = field(Forms.date(draft.due), 14);
        milestone = field(draft.milestone, 22);
        estimate = field(draft.estimate == 0 ? "" : String.valueOf(draft.estimate), 10);
        complete = Forms.check("Complete", draft.complete);
        watching = Forms.check("Watch in My work", draft.watching);
        destination = choices(board.lanes.stream().filter(item -> !item.archived || item == lane).toList(),
            item -> item.name, lane);

        styleTabs(tabs);
        tabs.addTab("Details", tab(details()));
        tabs.addTab("Checklists", tab(checklistBody));
        tabs.addTab("Dependencies", tab(dependencyBody));
        tabs.addTab("Files", tab(fileBody));
        tabs.addTab("Discussion & activity", tab(conversation));
        refreshChecklists();
        refreshFiles();
        refreshDiscussion();
        refreshDependencies();
        wrapper.add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JPanel end = row(7);
        end.add(button("Cancel", null, this::dispose).ghost());
        Button save = primary(card == null ? "Create card" : "Save changes", "check", () -> commit(null));
        end.add(save);
        footer.add(end, BorderLayout.EAST);
        if (card != null) {
            Button actions = new Button("Actions", "menu");
            actions.addActionListener(event -> showMenu(actions, this::cardActions));
            footer.add(actions, BorderLayout.WEST);
        }
        wrapper.add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
        SwingUtilities.invokeLater(title::requestFocusInWindow);
    }

    private JPanel details() {
        JPanel details = stack();
        line(details, Forms.form("Description", scroll(description), "List", destination, "Type", type,
            "Priority", priority, "Start (YYYY-MM-DD)", starts, "Due (YYYY-MM-DD)", due,
            "Milestone", milestone, "Estimate (hours)", estimate, "Status", complete, "Following", watching));
        addSelections(details, "Labels", board.labels.keySet(), draft.labels, labels);
        addSelections(details, "Assignees", board.people, draft.assignees, people);
        if (!board.fields.isEmpty()) {
            heading(details, "Custom fields");
            List<Object> entries = new ArrayList<>();
            for (String name : board.fields) {
                JTextField input = field(draft.fields.getOrDefault(name, ""), 21);
                extras.put(name, input);
                entries.add(name);
                entries.add(input);
            }
            line(details, Forms.form(entries.toArray()));
        }
        return details;
    }

    private void addSelections(JPanel parent, String name, Iterable<String> options, java.util.Set<String> selected,
                               Map<String, JCheckBox> checks) {
        heading(parent, name);
        boolean empty = true;
        JPanel buttons = new JPanel(new GridLayout(0, 2, 6, 2));
        buttons.setOpaque(false);
        for (String option : options) {
            JCheckBox check = Forms.check(option, selected.contains(option));
            checks.put(option, check);
            buttons.add(check);
            empty = false;
        }
        line(parent, empty ? text("Add options in Board settings.", 12, false, QUIET) : buttons);
    }

    private void refreshChecklists() {
        checklistBody.removeAll();
        if (draft.checklists.isEmpty()) line(checklistBody, text("Add a checklist to break this work into steps.", 14, false, MUTED));
        for (Checklist list : draft.checklists) {
            heading(checklistBody, list.title + "  ·  " + list.items.stream().filter(item -> item.done).count() + "/" + list.items.size());
            for (Item item : list.items) {
                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setOpaque(false);
                JCheckBox done = Forms.check(item.title, item.done);
                done.addActionListener(event -> { item.done = done.isSelected(); refreshChecklists(); });
                row.add(done, BorderLayout.CENTER);
                JPanel tools = row(4);
                if (!item.owner.isBlank() || item.due != null) {
                    tools.add(text(String.join("  ", item.owner, Forms.date(item.due)).strip(), 12, false, MUTED));
                }
                tools.add(button("Edit", null, () -> editItem(list, item)).ghost());
                row.add(tools, BorderLayout.EAST);
                line(checklistBody, row);
            }
            JPanel actions = row(5);
            actions.add(button("Item", "plus", () -> editItem(list, null)).ghost());
            actions.add(button("Rename", null, () -> {
                String name = Forms.prompt(this, "Rename checklist", "Name", list.title);
                if (!name.isBlank()) { list.title = name; refreshChecklists(); }
            }).ghost());
            actions.add(button("Remove list", null, () -> {
                draft.checklists.remove(list);
                refreshChecklists();
            }).ghost());
            line(checklistBody, actions);
        }
        gap(checklistBody, 12);
        line(checklistBody, primary("Add checklist", "plus", () -> {
            String name = Forms.prompt(this, "Add checklist", "Name", "Acceptance criteria");
            if (!name.isBlank()) { draft.checklists.add(new Checklist(name)); refreshChecklists(); }
        }));
        refresh(checklistBody);
    }

    private void editItem(Checklist list, Item item) {
        JTextField name = field(item == null ? "" : item.title, 24);
        JTextField date = field(item == null ? "" : Forms.date(item.due), 14);
        JComboBox<String> owner = Forms.optional(board.people, item == null ? "" : item.owner);
        Object[] options = item == null ? new Object[]{"Save", "Cancel"} : new Object[]{"Save", "Cancel", "Remove"};
        JPanel form = Forms.form("Item", name, "Assignee", owner, "Due (YYYY-MM-DD)", date);
        while (true) {
            int result = JOptionPane.showOptionDialog(this, form, "Checklist item", JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            if (result == 2) { list.items.remove(item); refreshChecklists(); return; }
            if (result != 0) return;
            try {
                if (name.getText().isBlank()) throw new IllegalArgumentException("Enter a checklist item name.");
                LocalDate dueDate = Forms.date(date);
                Item entry = item == null ? new Item(name.getText().strip()) : item;
                entry.title = name.getText().strip();
                entry.owner = Forms.value(owner);
                entry.due = dueDate;
                if (item == null) list.items.add(entry);
                refreshChecklists();
                return;
            } catch (IllegalArgumentException error) { app.showError(error); }
        }
    }

    private void refreshDependencies() {
        dependencyBody.removeAll();
        line(dependencyBody, text("This card depends on the tasks below.", 14, false, MUTED));
        gap(dependencyBody, 12);
        for (String id : List.copyOf(draft.dependencies)) {
            Card dependency = board.card(id);
            JPanel entry = row(7);
            entry.add(badge(dependency != null && dependency.complete ? "Complete" : "Open", dependency != null && dependency.complete ? GREEN : ORANGE));
            entry.add(text(dependency == null ? "Removed task" : dependency.title, 14, true, TEXT));
            entry.add(button("Remove", null, () -> { draft.dependencies.remove(id); refreshDependencies(); }).ghost());
            line(dependencyBody, entry);
            gap(dependencyBody, 6);
        }
        line(dependencyBody, button("Add dependency", "plus", () -> {
            List<Card> candidates = board.activeCards().stream().filter(card -> card != original
                && !draft.dependencies.contains(card.id) && !createsCycle(board, draft, card)).toList();
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No eligible cards on this board.");
                return;
            }
            JComboBox<Choice<Card>> options = choices(candidates, card -> card.title, candidates.getFirst());
            if (Forms.accept(this, "Add dependency", Forms.form("Preceding task", options))) {
                draft.dependencies.add(choice(options).id);
                refreshDependencies();
            }
        }));
        refresh(dependencyBody);
    }

    private void refreshFiles() {
        fileBody.removeAll();
        if (draft.attachments.isEmpty()) line(fileBody, text("Attach design notes, reference art, or playtest logs.", 14, false, MUTED));
        for (Attachment file : List.copyOf(draft.attachments)) {
            JPanel entry = row(7);
            entry.add(glyph("paperclip", ACCENT, 17));
            entry.add(text(shorten(file.name()), 14, true, TEXT));
            entry.add(text(file.size() < 1024 ? file.size() + " B" : Math.round(file.size() / 1024.0) + " KiB", 12, false, QUIET));
            entry.add(button("Save a copy", null, () -> saveAttachment(file)).ghost());
            entry.add(button("Remove", null, () -> { draft.attachments.remove(file); refreshFiles(); }).ghost());
            line(fileBody, entry);
            gap(fileBody, 6);
        }
        gap(fileBody, 10);
        line(fileBody, primary("Attach files", "plus", this::attach));
        refresh(fileBody);
    }

    private static String shorten(String value) { return BoardCanvas.shortText(value, 42); }

    private void attach() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        runFileTask("Copying attachments…", () -> {
            List<Attachment> added = new ArrayList<>();
            for (var file : chooser.getSelectedFiles()) added.add(app.store.attach(file.toPath()));
            return added;
        }, added -> {
            for (Attachment file : added) if (!draft.attachments.contains(file)) draft.attachments.add(file);
            refreshFiles();
        });
    }

    private void saveAttachment(Attachment file) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(file.name()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        var target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (target.startsWith(app.store.directory.normalize())) {
            app.showError(new IOException("Choose a location outside the Quallo data folder."));
            return;
        }
        if (Files.exists(target) && !app.confirm("Replace the existing file?", "Save attachment")) return;
        runFileTask("Saving attachment…", () -> Files.copy(app.store.attachment(file), target,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING), ignored -> {});
    }

    private <T> void runFileTask(String message, java.util.concurrent.Callable<T> task, java.util.function.Consumer<T> completeTask) {
        FormsUi.runTask(this, message, task, completeTask, app::showError);
    }

    private void refreshDiscussion() {
        conversation.removeAll();
        heading(conversation, "Comments");
        line(conversation, scroll(commentInput));
        gap(conversation, 7);
        line(conversation, button("Add comment", "comment", () -> {
            addPendingComment();
            refreshDiscussion();
        }));
        for (Comment comment : draft.comments) {
            heading(conversation, comment.author() + "  ·  " + STAMP.format(comment.date()));
            JTextArea text = area(comment.text(), 0, 35);
            text.setEditable(false);
            line(conversation, text);
        }
        heading(conversation, "Activity");
        if (draft.events.isEmpty()) line(conversation, text("No changes recorded yet.", 12, false, QUIET));
        for (Model.Event event : draft.events) {
            JLabel text = text(event.text() + "  ·  " + event.author() + "  ·  " + STAMP.format(event.date()), 12, false, MUTED);
            pad(text, 4, 0, 4, 0);
            line(conversation, text);
        }
        refresh(conversation);
    }

    private void addPendingComment() {
        if (commentInput.getText().isBlank()) return;
        draft.comments.addFirst(new Comment(app.state.author, commentInput.getText().strip(), Instant.now()));
        draft.log(app.state.author, "Added a comment");
        commentInput.setText("");
    }

    private void cardActions(JPopupMenu menu) {
        Theme.action(menu, original.archived ? "Restore card" : "Archive card", () -> commit(card -> {
            card.archived = !card.archived;
            card.log(app.state.author, card.archived ? "Archived the card" : "Restored the card");
            Lane home = board.laneOf(card);
            if (!card.archived && home.archived) home.archived = false;
        }));
        Theme.action(menu, "Duplicate card", () -> commit(card -> duplicate(card, false)));
        if (board != app.state.inbox) Theme.action(menu, "Save a copy as template", () -> commit(card -> duplicate(card, true)));
        Theme.action(menu, board == app.state.inbox ? "Move to a project board" : "Move to another board", this::moveAcrossBoards);
    }

    private void duplicate(Card card, boolean template) {
        Card copy = copyCard(card, card.title + (template ? " template" : " copy"), app.state.author);
        copy.template = template;
        Lane lane = board.laneOf(card);
        if (!template) {
            copy.complete = lane.done;
            applyRules(board, lane, copy);
        }
        lane.cards.add(copy);
    }

    static void moveFromInbox(StudioWindow app, Card card) {
        Board original = app.state.inbox;
        chooseMove(app, app, original, target -> {
            moveCard(original, target.board, card, target.lane, null, app.state.author);
            app.changed();
        });
    }

    private void moveAcrossBoards() {
        chooseMove(app, this, board, target -> commit(card ->
            moveCard(board, target.board, card, target.lane, null, app.state.author)));
    }

    private record Destination(Board board, Lane lane) {}

    private static void chooseMove(StudioWindow app, Component owner, Board board,
                                   java.util.function.Consumer<Destination> move) {
        List<Board> boards = app.state.workspaces.stream().flatMap(space -> space.boards.stream())
            .filter(value -> value != board && !value.template && !value.archived && value.firstLane() != null).toList();
        if (boards.isEmpty()) {
            JOptionPane.showMessageDialog(owner, "Create a project board with an active list first.");
            return;
        }
        JComboBox<Choice<Board>> projects = choices(boards, value -> app.state.workspace(value).name + " / " + value.name, boards.getFirst());
        JComboBox<Choice<Lane>> lists = new JComboBox<>();
        styleCombo(lists);
        Runnable fill = () -> {
            lists.removeAllItems();
            choice(projects).lanes.stream().filter(lane -> !lane.archived)
                .forEach(lane -> lists.addItem(new Choice<>(lane, lane.name)));
        };
        projects.addActionListener(event -> fill.run());
        fill.run();
        if (Forms.accept(owner, "Move card to a project board", Forms.form("Board", projects, "List", lists))) {
            move.accept(new Destination(choice(projects), choice(lists)));
        }
    }

    private void commit(java.util.function.Consumer<Card> after) {
        try {
            if (title.getText().isBlank()) throw new IllegalArgumentException("Enter a card title.");
            LocalDate first = Forms.date(starts), last = Forms.date(due);
            if (first != null && last != null && last.isBefore(first)) {
                throw new IllegalArgumentException("The due date must be on or after the start date.");
            }
            int hours;
            try { hours = estimate.getText().isBlank() ? 0 : Integer.parseInt(estimate.getText().strip()); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Enter a whole number of hours.", error); }
            if (hours < 0) throw new IllegalArgumentException("Estimate must be zero or more.");
            draft.title = title.getText().strip();
            draft.description = description.getText().strip();
            draft.start = first;
            draft.due = last;
            draft.type = Forms.value(type);
            draft.priority = Forms.value(priority);
            draft.milestone = milestone.getText().strip();
            draft.estimate = hours;
            draft.complete = complete.isSelected();
            draft.watching = watching.isSelected();
            draft.labels.clear();
            labels.forEach((key, check) -> { if (check.isSelected()) draft.labels.add(key); });
            draft.assignees.clear();
            people.forEach((key, check) -> { if (check.isSelected()) draft.assignees.add(key); });
            draft.fields.clear();
            extras.forEach((key, value) -> { if (!value.getText().isBlank()) draft.fields.put(key, value.getText().strip()); });
            addPendingComment();
            Lane target = choice(destination);
            if (original == null) {
                target.cards.add(draft);
                draft.complete |= target.done;
                applyRules(board, target, draft);
                draft.log(app.state.author, "Created in " + target.name);
            } else {
                initialLane.cards.set(initialLane.cards.indexOf(original), draft);
                draft.log(app.state.author, "Updated card details");
                if (target != initialLane) moveCard(board, board, draft, target, null, app.state.author);
            }
            if (after != null) after.accept(draft);
            dispose();
            app.changed();
        } catch (IllegalArgumentException error) {
            tabs.setSelectedIndex(0);
            app.showError(error);
        }
    }

    private static Card editableCopy(Card card) {
        Card draft = copyCard(card, card.title, "");
        draft.id = card.id;
        draft.archived = card.archived;
        draft.complete = card.complete;
        draft.template = card.template;
        draft.watching = card.watching;
        draft.dependencies.addAll(card.dependencies);
        draft.comments.addAll(card.comments);
        draft.events.clear();
        draft.events.addAll(card.events);
        for (int i = 0; i < card.checklists.size(); i++) {
            for (int j = 0; j < card.checklists.get(i).items.size(); j++) {
                draft.checklists.get(i).items.get(j).done = card.checklists.get(i).items.get(j).done;
            }
        }
        return draft;
    }

    record Choice<T>(T value, String label) {
        @Override public String toString() { return label; }
    }

    @SuppressWarnings("unchecked")
    static <T> T choice(JComboBox<Choice<T>> box) {
        Choice<T> selected = (Choice<T>) box.getSelectedItem();
        return selected == null ? null : selected.value();
    }

    static <T> JComboBox<Choice<T>> choices(List<T> values, java.util.function.Function<T, String> label, T selected) {
        List<Choice<T>> entries = values.stream().map(value -> new Choice<>(value, label.apply(value))).toList();
        @SuppressWarnings("unchecked")
        Choice<T>[] options = entries.toArray(Choice[]::new);
        return Forms.combo(options, entries.stream().filter(entry -> entry.value() == selected).findFirst()
            .orElse(entries.isEmpty() ? null : entries.getFirst()));
    }

    private static void heading(JPanel panel, String title) {
        gap(panel, 13);
        line(panel, text(title, 14, true, TEXT));
        gap(panel, 7);
    }

    private static JScrollPane tab(JPanel content) {
        pad(content, 11, 12, 13, 12);
        JPanel holder = panel(new BorderLayout(), SURFACE);
        holder.add(content, BorderLayout.NORTH);
        return scroll(holder);
    }

    private static void refresh(JPanel panel) {
        panel.revalidate();
        panel.repaint();
    }
}
