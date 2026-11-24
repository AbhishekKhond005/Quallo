package quallo;

import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import static quallo.Model.*;
import quallo.Theme.Button;
import static quallo.Theme.*;

final class Views {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d");
    private static final Comparator<Work> BY_DUE = Comparator.comparing(work -> work.card.due,
        Comparator.nullsLast(Comparator.naturalOrder()));

    static JPanel title(String heading, String detail) {
        JPanel title = stack();
        line(title, text(heading, 29, true, TEXT));
        gap(title, 5);
        line(title, text(detail, 14, false, MUTED));
        return title;
    }

    static JComponent empty(String title, String detail, JComponent action) {
        JPanel center = stack();
        JLabel heading = text(title, 20, true, TEXT);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(heading);
        center.add(Box.createVerticalStrut(6));
        JLabel info = text(detail, 14, false, MUTED);
        info.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(info);
        if (action != null) {
            center.add(Box.createVerticalStrut(13));
            action.setAlignmentX(Component.CENTER_ALIGNMENT);
            center.add(action);
        }
        JPanel page = panel(new GridBagLayout(), BASE);
        page.add(center);
        return page;
    }

    private record Work(Board board, Lane lane, Card card) {}

    static JComponent workspace(StudioWindow app, Workspace space) {
        String query = app.search.getText().strip().toLowerCase(Locale.ROOT);
        List<Work> cards = workspaceWork(space).stream()
            .filter(item -> app.filters.matches(item.board, item.lane, item.card, query)).toList();
        JPanel content = panel(new BorderLayout(0, 15), BASE);
        pad(content, 25, 28, 25, 28);
        content.add(title(space.name, "Tasks across active project boards in this workspace."), BorderLayout.NORTH);
        content.add(tableFor(app, cards, true), BorderLayout.CENTER);
        return content;
    }

    private static List<Work> workspaceWork(Workspace space) {
        return space.boards.stream().filter(item -> !item.archived && !item.template)
            .flatMap(board -> board.lanes.stream().filter(lane -> !lane.archived).flatMap(lane -> lane.cards.stream()
                .filter(card -> !card.archived && !card.template).map(card -> new Work(board, lane, card)))).toList();
    }

    static JComponent table(StudioWindow app) {
        return tableFor(app, app.matchingCards().stream().map(card -> new Work(app.board, app.board.laneOf(card), card)).toList(), false);
    }

    private static JComponent tableFor(StudioWindow app, List<Work> cards, boolean showBoard) {
        if (cards.isEmpty()) return empty("No matching tasks", "Try a different search or add a card.", null);
        String[] columns = { "Card", showBoard ? "Board / list" : "List", "Type", "Priority", "Assignees", "Due date", "Status" };
        JTable table = new JTable(new AbstractTableModel() {
            @Override public int getRowCount() { return cards.size(); }
            @Override public int getColumnCount() { return columns.length; }
            @Override public String getColumnName(int col) { return columns[col]; }
            @Override public Object getValueAt(int row, int col) {
                Work item = cards.get(row);
                Card card = item.card;
                return switch (col) {
                    case 0 -> card.title;
                    case 1 -> (showBoard ? item.board.name + " / " : "") + item.lane.name;
                    case 2 -> card.type;
                    case 3 -> card.priority;
                    case 4 -> String.join(", ", card.assignees);
                    case 5 -> Forms.date(card.due);
                    default -> card.complete ? "Complete" : card.overdue(LocalDate.now()) ? "Overdue" : "Open";
                };
            }
        });
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(38);
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        int[] widths = {245, 145, 85, 75, 130, 87, 78};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, selected, focus, row, col);
                putClientProperty("html.disable", true);
                setFont(font(13, col == 0));
                setBackground(selected ? ACCENT_BG : row % 2 == 0 ? SURFACE : SIDE);
                setForeground(col == 6 && value.equals("Overdue") ? DANGER : col == 6 && value.equals("Complete") ? GREEN : col == 0 ? TEXT : MUTED);
                pad(this, 0, 10, 0, 4);
                setToolTipText(String.valueOf(value));
                return this;
            }
        });
        Runnable open = () -> {
            int index = table.getSelectedRow();
            if (index < 0) return;
            Work selected = cards.get(table.convertRowIndexToModel(index));
            app.editCard(selected.board, selected.card);
        };
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2) open.run(); }
        });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        table.getActionMap().put("open", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { open.run(); }
        });
        JPanel wrap = panel(new BorderLayout(0, 7), BASE);
        JPanel info = row(9);
        info.add(text(cards.size() + (cards.size() == 1 ? " card" : " cards"), 13, true, MUTED));
        info.add(button("Open selected", "arrow", open).ghost());
        wrap.add(info, BorderLayout.NORTH);
        app.currentScroll = scroll(table);
        wrap.add(app.currentScroll, BorderLayout.CENTER);
        return wrap;
    }

    static JComponent myWork(StudioWindow app) {
        String query = app.search.getText().strip().toLowerCase(Locale.ROOT);
        List<Work> list = personalWork(app).stream().filter(item -> app.filters.matches(item.board, item.lane, item.card, query)).sorted(BY_DUE).toList();
        long overdue = list.stream().filter(item -> item.card.overdue(LocalDate.now())).count();
        long upcoming = list.stream().filter(item -> !item.card.complete && item.card.due != null && !item.card.due.isBefore(LocalDate.now())
            && !item.card.due.isAfter(LocalDate.now().plusDays(7))).count();
        JPanel page = panel(new BorderLayout(0, 17), BASE);
        pad(page, 25, 28, 25, 28);
        JPanel heading = stack();
        line(heading, title("My work", "Tasks assigned to " + app.state.author + " and cards you’re watching."));
        gap(heading, 12);
        JPanel counts = row(8);
        counts.add(badge(overdue + " overdue", overdue > 0 ? DANGER : QUIET));
        counts.add(badge(upcoming + " due in 7 days", ACCENT));
        line(heading, counts);
        page.add(heading, BorderLayout.NORTH);
        page.add(list.isEmpty() ? empty("No tasks here yet", "Assign a card to yourself or select Watch in My work.", null)
            : tableFor(app, list, true), BorderLayout.CENTER);
        return page;
    }

    private static List<Work> personalWork(StudioWindow app) {
        return app.state.workspaces.stream().flatMap(space -> workspaceWork(space).stream())
            .filter(item -> item.card.watching || item.card.assignees.contains(app.state.author)
                || item.card.checklists.stream().flatMap(list -> list.items.stream())
                    .anyMatch(task -> !task.done && task.owner.equals(app.state.author)))
            .toList();
    }

    static JComponent calendar(StudioWindow app) {
        JPanel page = panel(new BorderLayout(0, 10), BASE);
        page.add(monthBar(app, "Due dates"), BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(0, 7, 5, 5));
        grid.setOpaque(false);
        for (String name : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}) {
            JLabel day = text(name, 12, true, MUTED);
            pad(day, 0, 5, 0, 0);
            grid.add(day);
        }
        Map<LocalDate, List<Card>> days = new LinkedHashMap<>();
        for (Card card : app.matchingCards()) {
            if (card.due != null) days.computeIfAbsent(card.due, date -> new ArrayList<>()).add(card);
        }
        LocalDate first = app.month.atDay(1);
        int dayOffset = first.getDayOfWeek().getValue() - 1;
        int slots = ((app.month.lengthOfMonth() + dayOffset + 6) / 7) * 7;
        JPanel cells = new JPanel(new GridLayout(0, 7, 5, 5));
        cells.setOpaque(false);
        for (int i = 0; i < slots; i++) {
            LocalDate date = first.plusDays(i - dayOffset);
            boolean current = YearMonth.from(date).equals(app.month);
            boolean today = date.equals(LocalDate.now());
            JPanel tile = new Surface(new BorderLayout(), current ? SURFACE : SIDE, today ? ACCENT : LINE, 12);
            pad(tile, 6, 7, 5, 7);
            tile.setPreferredSize(new Dimension(115, 87));
            JPanel tasks = stack();
            line(tasks, text(String.valueOf(date.getDayOfMonth()), 12, true, today ? ACCENT : current ? TEXT : QUIET));
            for (Card card : days.getOrDefault(date, List.of()).stream().limit(2).toList()) {
                Button item = button(BoardCanvas.shortText(card.title, 21), null, () -> app.editCard(app.board, card)).ghost().left();
                item.setToolTipText(card.title);
                item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                pad(item, 3, 3, 3, 3);
                item.style(card.complete ? blend(CARD, GREEN, .12) : ACCENT_BG, card.complete ? GREEN : ACCENT);
                line(tasks, item);
            }
            if (days.getOrDefault(date, List.of()).size() > 2) {
                Button more = button("+" + (days.get(date).size() - 2) + " more", null,
                    () -> chooseDayCard(app, days.get(date))).ghost();
                pad(more, 1, 3, 1, 3);
                line(tasks, more);
            }
            tile.add(tasks, BorderLayout.NORTH);
            cells.add(tile);
        }
        JPanel content = panel(new BorderLayout(0, 4), BASE);
        content.add(grid, BorderLayout.NORTH);
        content.add(cells, BorderLayout.CENTER);
        app.currentScroll = scroll(content);
        page.add(app.currentScroll, BorderLayout.CENTER);
        return page;
    }

    private static void chooseDayCard(StudioWindow app, List<Card> cards) {
        var choices = CardEditor.choices(cards, card -> card.title, cards.getFirst());
        if (Forms.accept(app, "Due on " + cards.getFirst().due, Forms.form("Card", choices))) {
            app.editCard(app.board, CardEditor.choice(choices));
        }
    }

    private static JPanel monthBar(StudioWindow app, String title) {
        JPanel bar = panel(new BorderLayout(), BASE);
        JLabel heading = text(title + "  /  " + app.month.format(MONTH), 17, true, TEXT);
        bar.add(heading, BorderLayout.WEST);
        JPanel controls = row(5);
        Button previous = button("", "left", () -> stepMonth(app, -1));
        previous.setToolTipText("Previous month");
        controls.add(previous);
        controls.add(button("Today", "cal", () -> {
            app.month = YearMonth.now();
            app.currentScroll = null;
            app.render();
        }));
        Button next = button("", "right", () -> stepMonth(app, 1));
        next.setToolTipText("Next month");
        controls.add(next);
        bar.add(controls, BorderLayout.EAST);
        return bar;
    }

    private static void stepMonth(StudioWindow app, int delta) {
        app.month = app.month.plusMonths(delta);
        app.currentScroll = null;
        app.render();
    }

    static JComponent timeline(StudioWindow app) {
        JPanel page = panel(new BorderLayout(0, 10), BASE);
        page.add(monthBar(app, "Schedule"), BorderLayout.NORTH);
        LocalDate from = app.month.atDay(1), to = app.month.atEndOfMonth();
        List<Card> cards = app.matchingCards().stream().filter(card -> {
            LocalDate start = card.start == null ? card.due : card.start;
            LocalDate end = card.due == null ? card.start : card.due;
            return start != null && !end.isBefore(from) && !start.isAfter(to);
        }).sorted(Comparator.comparing(card -> card.start == null ? card.due : card.start)).toList();
        if (cards.isEmpty()) {
            page.add(empty("No scheduled tasks this month", "Set a start or due date on a card to plot its schedule.", null), BorderLayout.CENTER);
            return page;
        }
        JPanel rows = stack();
        line(rows, new TimelineRow(app, null, from, to));
        for (Card card : cards) line(rows, new TimelineRow(app, card, from, to));
        app.currentScroll = scroll(rows);
        page.add(app.currentScroll, BorderLayout.CENTER);
        return page;
    }

    private static final class TimelineRow extends JButton {
        private final Card card;
        private final LocalDate from, to;
        private final Color tint;
        TimelineRow(StudioWindow app, Card card, LocalDate from, LocalDate to) {
            this.card = card;
            this.from = from;
            this.to = to;
            tint = card == null || card.labels.isEmpty() ? ACCENT : color(app.board.labels.getOrDefault(card.labels.iterator().next(), COLORS.getFirst()));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            setPreferredSize(new Dimension(695, 40));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusable(card != null);
            if (card != null) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(card.title + "  ·  " + Forms.date(card.start) + " – " + Forms.date(card.due));
                getAccessibleContext().setAccessibleName(card.title);
                addActionListener(event -> app.editCard(app.board, card));
            }
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            int width = getWidth(), height = getHeight(), label = Math.min(235, width / 3);
            int days = to.getDayOfMonth();
            double cell = (width - label - 11) / (double) days;
            g.setColor(getModel().isRollover() ? HOVER : SURFACE);
            g.fillRect(0, 0, width, height);
            g.setFont(font(12, false));
            for (int i = 0; i < days; i++) {
                int x = label + (int) (i * cell);
                if (i % 7 == 0) {
                    g.setColor(LINE);
                    g.drawLine(x, 0, x, height);
                    if (card == null) { g.setColor(MUTED); g.drawString(from.plusDays(i).format(DAY), x + 5, 24); }
                }
                if (from.plusDays(i).equals(LocalDate.now())) {
                    g.setColor(blend(SURFACE, ACCENT, .36));
                    g.drawLine(x, 0, x, height);
                }
            }
            if (card == null) {
                g.setColor(MUTED);
                g.drawString("Task", 11, 24);
            } else {
                g.setColor(TEXT);
                g.setFont(font(13, true));
                g.drawString(BoardCanvas.shortText(card.title, Math.max(13, label / 8)), 11, 24);
                LocalDate start = card.start == null ? card.due : card.start;
                LocalDate end = card.due == null ? card.start : card.due;
                int left = label + (int) (Math.max(0, ChronoUnit.DAYS.between(from, start)) * cell);
                int right = label + (int) ((Math.min(days - 1, ChronoUnit.DAYS.between(from, end)) + 1) * cell);
                g.setColor(card.complete ? GREEN : tint);
                g.fillRoundRect(left + 1, 12, Math.max(3, right - left - 3), 16, 10, 10);
            }
            g.setColor(hasFocus() ? ACCENT : LINE);
            if (hasFocus()) g.drawRect(0, 0, width - 1, height - 1);
            else g.drawLine(0, height - 1, width, height - 1);
            g.dispose();
        }
    }

    static JComponent overview(StudioWindow app) {
        List<Card> cards = app.matchingCards();
        long done = cards.stream().filter(card -> card.complete).count();
        long overdue = cards.stream().filter(card -> card.overdue(LocalDate.now())).count();
        long blocked = cards.stream().filter(card -> card.dependencies.stream().map(app.board::card).anyMatch(parent -> parent != null && !parent.complete)).count();
        long effort = cards.stream().filter(card -> !card.complete).mapToLong(card -> card.estimate).sum();
        JPanel root = stack();
        JPanel summary = new JPanel(new GridLayout(1, 5, 8, 0));
        summary.setOpaque(false);
        summary.add(stat("Open cards", cards.size() - done, ACCENT));
        summary.add(stat("Complete", done, GREEN));
        summary.add(stat("Overdue", overdue, DANGER));
        summary.add(stat("Blocked", blocked, ORANGE));
        summary.add(stat("Open hours", effort, ACCENT));
        line(root, summary);
        gap(root, 15);
        JPanel rows = new JPanel(new GridLayout(1, 2, 9, 0));
        rows.setOpaque(false);
        JPanel byList = summaryBox("List progress");
        for (Lane lane : app.board.lanes) {
            if (lane.archived) continue;
            List<Card> items = cards.stream().filter(lane.cards::contains).toList();
            progress(byList, lane.name, items.stream().filter(card -> card.complete).count(), items.size(), color(lane.color));
        }
        rows.add(byList);
        JPanel milestones = summaryBox("Milestones");
        Map<String, List<Card>> grouped = new LinkedHashMap<>();
        for (Card card : cards) {
            if (!card.milestone.isBlank()) grouped.computeIfAbsent(card.milestone, milestone -> new ArrayList<>()).add(card);
        }
        if (grouped.isEmpty()) line(milestones, text("Add a milestone name to related cards.", 13, false, MUTED));
        grouped.forEach((key, list) -> progress(milestones, key, list.stream().filter(card -> card.complete).count(), list.size(), ACCENT));
        rows.add(milestones);
        line(root, rows);
        gap(root, 15);
        JPanel next = summaryBox("Upcoming deadlines");
        var waiting = cards.stream().filter(card -> !card.complete && card.due != null).sorted(Comparator.comparing(card -> card.due)).limit(8).toList();
        if (waiting.isEmpty()) line(next, text("No open cards with due dates.", 13, false, MUTED));
        for (Card card : waiting) {
            JPanel item = row(9);
            item.add(badge(card.due.format(DAY), card.overdue(LocalDate.now()) ? DANGER : ACCENT));
            item.add(button(card.title, "arrow", () -> app.editCard(app.board, card)).ghost());
            line(next, item);
        }
        line(root, next);
        app.currentScroll = scroll(root);
        return app.currentScroll;
    }

    private static JPanel summaryBox(String heading) {
        JPanel tile = new Surface(null, SURFACE, LINE, 13);
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        pad(tile, 13, 14, 14, 14);
        line(tile, text(heading, 17, true, TEXT));
        gap(tile, 8);
        return tile;
    }

    private static JPanel stat(String name, long value, Color tint) {
        JPanel tile = new Surface(new BorderLayout(), SURFACE, LINE, 13);
        pad(tile, 13, 14, 14, 14);
        JPanel content = stack();
        line(content, text(name, 13, true, MUTED));
        gap(content, 3);
        line(content, text(String.valueOf(value), 30, true, tint));
        tile.add(content, BorderLayout.CENTER);
        return tile;
    }

    private static void progress(JPanel host, String name, long done, int total, Color tint) {
        JPanel line = new JPanel(new BorderLayout(6, 0));
        line.setOpaque(false);
        line.add(text(name, 13, true, TEXT), BorderLayout.WEST);
        line.add(text(done + "/" + total, 12, false, MUTED), BorderLayout.EAST);
        line(host, line);
        gap(host, 4);
        JProgressBar bar = new JProgressBar(0, Math.max(1, total));
        bar.setValue((int) done);
        bar.setBorderPainted(false);
        bar.setForeground(tint);
        bar.setBackground(CARD);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
        line(host, bar);
        gap(host, 9);
    }

    static void exportCsv(StudioWindow app) {
        var path = BoardsUi.chooseFile(app, true, "CSV export", "csv", "Quallo-cards.csv");
        if (path == null) return;
        Board board = app.board;
        List<Card> cards = board.activeCards();
        FormsUi.runTask(app, "Writing CSV…", () -> {
            try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("Title,List,Description,Type,Priority,Complete,Start,Due,Milestone,Estimate hours,Assignees,Labels\r\n");
                for (Card card : cards) {
                    List<String> fields = List.of(card.title, board.laneOf(card).name, card.description, card.type, card.priority,
                        String.valueOf(card.complete), Forms.date(card.start), Forms.date(card.due), card.milestone,
                        String.valueOf(card.estimate), String.join("; ", card.assignees), String.join("; ", card.labels));
                    writer.write(String.join(",", fields.stream().map(Views::csv).toList()) + "\r\n");
                }
            }
            return path;
        }, target -> JOptionPane.showMessageDialog(app, "CSV saved to " + target), app::showError);
    }

    private static String csv(String value) {
        String stripped = value.stripLeading();
        if ((!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) || value.startsWith("\t") || value.startsWith("\r")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
