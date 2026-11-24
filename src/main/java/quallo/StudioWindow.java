package quallo;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import static quallo.Model.*;
import quallo.Theme.Button;
import static quallo.Theme.*;

final class StudioWindow extends JFrame {
    final Store store;
    State state;
    Board board;
    Workspace workspaceTable;
    final JPanel navigation = panel(new BorderLayout(), SIDE);
    final JPanel body = panel(new BorderLayout(), BASE);
    final JPanel page = panel(new BorderLayout(), BASE);
    final JLabel saveStatus = text("Saved locally", 12, false, GREEN);
    final SearchField search = new SearchField();
    final Timer searchDelay;
    final WorkFilters filters = new WorkFilters();
    final Map<Lane, Integer> visibleLimits = new LinkedHashMap<>();
    String view = "Board";
    String section = "Project";
    java.time.YearMonth month = java.time.YearMonth.now();
    JScrollPane currentScroll;
    private boolean unsaved;
    private static BufferedImage cover;

    StudioWindow(Store store, State state) {
        super("Quallo");
        this.store = store;
        this.state = state;
        board = initialBoard();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));
        Rectangle display = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1500, display.width - 40), Math.min(940, display.height - 36));
        setLocationRelativeTo(null);
        setIconImage(appIcon());
        setContentPane(panel(new BorderLayout(), BASE));
        getContentPane().add(navigation, BorderLayout.WEST);
        getContentPane().add(body, BorderLayout.CENTER);
        navigation.setPreferredSize(new Dimension(228, 0));
        navigation.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LINE));
        searchDelay = new Timer(120, event -> {
            visibleLimits.clear();
            renderPage();
        });
        searchDelay.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { searchDelay.restart(); }
            @Override public void removeUpdate(DocumentEvent event) { searchDelay.restart(); }
            @Override public void changedUpdate(DocumentEvent event) { searchDelay.restart(); }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (unsaved && !save() && !confirm("Quit with unsaved changes?", "Quit Quallo")) return;
                try { store.close(); }
                catch (IOException error) { showError(error); }
                dispose();
            }
        });
        shortcut(KeyEvent.VK_K, "search", () -> {
            if (!hasSearch() && board != null) openSection("Project");
            search.requestFocusInWindow();
            search.selectAll();
        });
        shortcut(KeyEvent.VK_N, "card", () -> {
            if (section.equals("Inbox")) openSection("Inbox");
            else if (section.equals("Project")) addCard();
            else BoardsUi.createBoard(this, workspaceTable);
        });
        shortcut(KeyEvent.VK_F1, "guide", () -> openSection("Guide"));
        shortcut(KeyEvent.VK_B, "board", () -> openSection("Project"));
        save();
        render();
    }

    private static Image appIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        new Glyph("logo", ACCENT, 64).paintIcon(null, graphics, 0, 0);
        graphics.dispose();
        return image;
    }

    Board initialBoard() {
        Board selected = state.board(state.selectedBoard);
        return selected != null && !selected.archived && !selected.template ? selected : state.firstBoard();
    }

    boolean save() {
        try {
            store.save(state);
            unsaved = false;
            plain(saveStatus, "Saved locally");
            saveStatus.setForeground(GREEN);
            return true;
        } catch (IOException error) {
            unsaved = true;
            plain(saveStatus, "Changes unsaved");
            saveStatus.setForeground(DANGER);
            showError(error);
            return false;
        }
    }

    void changed() {
        save();
        render();
    }

    void chooseBoard(Board selected) {
        board = selected;
        state.selectedBoard = selected.id;
        filters.clear();
        visibleLimits.clear();
        search.setText("");
        searchDelay.stop();
        section = "Project";
        view = "Board";
        currentScroll = null;
        changed();
    }

    void openSection(String name) {
        section = name;
        if (!name.equals("Workspace table")) workspaceTable = null;
        filters.clear();
        visibleLimits.clear();
        search.setText("");
        searchDelay.stop();
        currentScroll = null;
        render();
    }

    void shortcut(int key, String name, Runnable task) {
        int mask = key == KeyEvent.VK_F1 ? 0 : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, mask), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { task.run(); }
        });
    }

    void render() {
        renderNavigation();
        body.removeAll();
        body.add(topbar(), BorderLayout.NORTH);
        body.add(page, BorderLayout.CENTER);
        renderPage();
        navigation.revalidate();
        navigation.repaint();
        body.revalidate();
        body.repaint();
    }

    void renderPage() {
        Point position = currentScroll == null ? new Point() : currentScroll.getViewport().getViewPosition();
        currentScroll = null;
        page.removeAll();
        if (section.equals("Guide")) {
            page.add(Tutorial.build(this), BorderLayout.CENTER);
        } else if (section.equals("Projects")) {
            page.add(BoardsUi.projects(this), BorderLayout.CENTER);
        } else if (section.equals("My work")) {
            page.add(Views.myWork(this), BorderLayout.CENTER);
        } else if (section.equals("Inbox")) {
            page.add(Inbox.build(this), BorderLayout.CENTER);
        } else if (section.equals("Workspace table")) {
            page.add(Views.workspace(this, workspaceTable), BorderLayout.CENTER);
        } else if (board == null) {
            page.add(BoardsUi.projects(this), BorderLayout.CENTER);
        } else {
            JPanel heading = panel(new BorderLayout(0, 16), BASE);
            pad(heading, 22, 28, 0, 28);
            heading.add(hero(), BorderLayout.CENTER);
            heading.add(toolbar(), BorderLayout.SOUTH);
            page.add(heading, BorderLayout.NORTH);
            JComponent content = switch (view) {
                case "Table" -> Views.table(this);
                case "Calendar" -> Views.calendar(this);
                case "Timeline" -> Views.timeline(this);
                case "Overview" -> Views.overview(this);
                default -> new BoardCanvas(this);
            };
            JPanel inner = panel(new BorderLayout(), BASE);
            pad(inner, 14, 28, 17, 28);
            inner.add(content, BorderLayout.CENTER);
            page.add(inner, BorderLayout.CENTER);
        }
        page.revalidate();
        page.repaint();
        JScrollPane rendered = currentScroll;
        if (rendered != null) SwingUtilities.invokeLater(() -> {
            Dimension extent = rendered.getViewport().getExtentSize();
            Dimension size = rendered.getViewport().getViewSize();
            rendered.getViewport().setViewPosition(new Point(Math.min(position.x, Math.max(0, size.width - extent.width)),
                Math.min(position.y, Math.max(0, size.height - extent.height))));
        });
    }

    private void renderNavigation() {
        navigation.removeAll();
        JPanel brand = panel(new BorderLayout(11, 0), SIDE);
        pad(brand, 20, 19, 16, 19);
        brand.add(glyph("logo", ACCENT, 34), BorderLayout.WEST);
        brand.add(text("Quallo", 25, true, TEXT), BorderLayout.CENTER);
        navigation.add(brand, BorderLayout.NORTH);

        JPanel links = stack();
        pad(links, 5, 14, 12, 14);
        nav(links, "Projects", "grid", section.equals("Projects"), () -> openSection("Projects"), ACCENT);
        nav(links, "Inbox", "archive", section.equals("Inbox"), () -> openSection("Inbox"), ORANGE);
        nav(links, "My work", "checks", section.equals("My work"), () -> openSection("My work"), GREEN);
        nav(links, "Project guide", "book", section.equals("Guide"), () -> openSection("Guide"), ORANGE);
        gap(links, 24);
        sectionLabel(links, "WORKSPACES", () -> BoardsUi.newWorkspace(this));
        for (Workspace space : state.workspaces) {
            gap(links, 13);
            JButton spaceName = button(space.name, "down", () -> BoardsUi.workspaceMenu(this, space)).ghost().left();
            wide(links, spaceName, 31);
            for (Board candidate : space.boards) {
                if (candidate.template || candidate.archived) continue;
                nav(links, candidate.name, "dot", section.equals("Project") && candidate == board,
                    () -> chooseBoard(candidate), color(candidate.color));
            }
            nav(links, "New board", "plus", false, () -> BoardsUi.createBoard(this, space), MUTED);
        }
        var favorites = new ArrayList<Board>();
        state.workspaces.forEach(space -> space.boards.stream().filter(b -> b.favorite && !b.archived && !b.template).forEach(favorites::add));
        if (!favorites.isEmpty()) {
            gap(links, 23);
            sectionLabel(links, "STARRED", null);
            gap(links, 7);
            favorites.forEach(candidate -> nav(links, candidate.name, "star", false, () -> chooseBoard(candidate), ACCENT));
        }
        JScrollPane scroller = scroll(links);
        scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        navigation.add(scroller, BorderLayout.CENTER);

        JPanel footer = panel(new BorderLayout(9, 0), SIDE);
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
            new EmptyBorder(12, 16, 13, 13)));
        footer.add(avatar(state.author, 32), BorderLayout.WEST);
        JPanel profile = stack();
        line(profile, text(state.author, 13, true, TEXT));
        line(profile, saveStatus);
        footer.add(profile, BorderLayout.CENTER);
        Button settings = button("", "settings", () -> BoardsUi.settings(this)).ghost();
        settings.setToolTipText("Settings and backup");
        footer.add(settings, BorderLayout.EAST);
        navigation.add(footer, BorderLayout.SOUTH);
    }

    private static void sectionLabel(JPanel links, String title, Runnable add) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        pad(row, 0, 8, 0, 1);
        row.add(text(title, 10, true, QUIET), BorderLayout.WEST);
        if (add != null) {
            Button button = button("", "plus", add).ghost();
            button.setToolTipText("New workspace");
            row.add(button, BorderLayout.EAST);
        }
        wide(links, row, 23);
    }

    private static void nav(JPanel links, String title, String icon, boolean active, Runnable open, Color tint) {
        Button button = button(title, icon, open).ghost().left();
        if (active) button.style(ACCENT_BG, TEXT);
        button.setIcon(new Glyph(icon, tint, 16));
        button.setToolTipText(title);
        wide(links, button, 36);
        gap(links, 2);
    }

    private static void wide(JPanel parent, JComponent component, int height) {
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setPreferredSize(new Dimension(165, height));
        line(parent, component);
    }

    private JPanel topbar() {
        JPanel top = panel(new BorderLayout(12, 0), BASE);
        top.setPreferredSize(new Dimension(0, 62));
        top.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
            new EmptyBorder(9, 28, 9, 28)));
        JPanel trail = row(9);
        if (section.equals("Project") && board != null) {
            Workspace space = state.workspace(board);
            trail.add(text(space == null ? "Workspace" : space.name, 13, false, MUTED));
            trail.add(glyph("right", QUIET, 11));
            trail.add(text(board.name, 13, true, TEXT));
        } else {
            trail.add(text(section.equals("Guide") ? "Project guide" : section.equals("Project") ? "Projects" : section, 14, true, TEXT));
        }
        JPanel holder = new JPanel(new GridBagLayout());
        holder.setOpaque(false);
        holder.add(trail);
        top.add(holder, BorderLayout.WEST);
        JPanel actions = row(10);
        if (hasSearch()) actions.add(search);
        if (!section.equals("Inbox")) {
            boolean project = board != null && section.equals("Project");
            actions.add(primary(project ? "New card" : "New board", "plus",
                project ? this::addCard : () -> BoardsUi.createBoard(this, workspaceTable)));
        }
        JPanel end = new JPanel(new GridBagLayout());
        end.setOpaque(false);
        end.add(actions);
        top.add(end, BorderLayout.EAST);
        return top;
    }

    private boolean hasSearch() {
        return section.equals("Project") && board != null || section.equals("My work") || section.equals("Workspace table");
    }

    private JPanel hero() {
        JPanel hero = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                int w = getWidth(), h = getHeight();
                g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, 23, 23));
                Color tint = color(board.color);
                g.setPaint(new GradientPaint(0, 0, SURFACE, w, h, blend(SURFACE, tint, .27)));
                g.fillRect(0, 0, w, h);
                BufferedImage image = board.cover.equals(SAMPLE_COVER) ? cover() : null;
                if (image != null) {
                    double scale = Math.max(w / (double) image.getWidth(), h / (double) image.getHeight());
                    int iw = (int) Math.ceil(image.getWidth() * scale), ih = (int) Math.ceil(image.getHeight() * scale);
                    g.drawImage(image, (w - iw) / 2, (int) ((h - ih) * .085), iw, ih, null);
                } else {
                    g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 33));
                    for (int i = 0; i < 7; i++) {
                        int size = 128 + i * 43;
                        g.drawOval(w - 175 - size / 2, 76 - size / 2, size, size);
                    }
                }
                g.setPaint(new GradientPaint(0, 0, new Color(13, 12, 22, 190), w * .82f, 0, new Color(17, 13, 24, 14)));
                g.fillRect(0, 0, w, h);
                g.setColor(new Color(232, 223, 248, 58));
                g.drawRoundRect(0, 0, w - 1, h - 1, 23, 23);
                g.dispose();
            }
        };
        hero.setOpaque(false);
        hero.setPreferredSize(new Dimension(0, 153));
        pad(hero, 17, 22, 17, 16);
        JPanel content = stack();
        Workspace space = state.workspace(board);
        line(content, badge(space == null ? "Project board" : space.name, ACCENT));
        gap(content, 5);
        line(content, text(board.name, 35, true, TEXT));
        if (!board.description.isBlank()) {
            gap(content, 5);
            JLabel description = text(board.description.replace('\n', ' '), 14, false, color("#DED6F0"));
            description.setToolTipText(board.description);
            line(content, description);
        }
        hero.add(content, BorderLayout.CENTER);
        JPanel controls = stack();
        JPanel buttons = row(6);
        Button favorite = button("", board.favorite ? "star-fill" : "star", () -> { board.favorite = !board.favorite; changed(); });
        favorite.setToolTipText(board.favorite ? "Remove from starred" : "Star board");
        favorite.style(blend(SURFACE, BASE, .30), ACCENT);
        buttons.add(favorite);
        Button more = new Button("", "menu").style(blend(SURFACE, BASE, .30), TEXT);
        more.setToolTipText("Board settings");
        more.addActionListener(event -> BoardsUi.boardMenu(this, more));
        buttons.add(more);
        line(controls, buttons);
        hero.add(controls, BorderLayout.EAST);
        return hero;
    }

    private static BufferedImage cover() {
        if (cover == null) {
            try (var stream = StudioWindow.class.getResourceAsStream("/quallo/nightfall.jpg")) {
                if (stream != null) cover = ImageIO.read(stream);
            } catch (IOException ignored) { }
        }
        return cover;
    }

    private JPanel toolbar() {
        JPanel toolbar = panel(new BorderLayout(8, 0), BASE);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
        JPanel tabs = row(4);
        String[][] views = {{"Board", "board"}, {"Table", "table"}, {"Calendar", "cal"}, {"Timeline", "time"}, {"Overview", "stats"}};
        for (String[] entry : views) {
            Button tab = button(entry[0], entry[1], () -> { view = entry[0]; currentScroll = null; render(); }).ghost();
            if (view.equals(entry[0])) tab.style(ACCENT_BG, ACCENT);
            tabs.add(tab);
        }
        toolbar.add(tabs, BorderLayout.WEST);
        JPanel controls = row(7);
        Button filter = new Button(filters.active() ? "Filters •" : "Filters", "filter");
        filter.addActionListener(event -> filters.edit(this));
        controls.add(filter);
        controls.add(button("List", "plus", () -> BoardsUi.editLane(this, null)));
        toolbar.add(controls, BorderLayout.EAST);
        pad(tabs, 0, -4, 9, -4);
        pad(controls, 0, -7, 9, -7);
        return toolbar;
    }

    List<Card> matchingCards() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        return board.lanes.stream().filter(lane -> !lane.archived).flatMap(lane -> lane.cards.stream()
            .filter(card -> !card.archived && !card.template && filters.matches(board, lane, card, query))).toList();
    }

    void addCard() {
        if (board == null) { BoardsUi.createBoard(this, null); return; }
        Lane lane = board.firstLane();
        if (lane == null) { BoardsUi.editLane(this, null); return; }
        addCard(lane);
    }

    void addCard(Lane lane) {
        CardEditor.open(this, board, lane, null);
    }

    void editCard(Board board, Card card) {
        CardEditor.open(this, board, board.laneOf(card), card);
    }

    boolean confirm(String message, String title) {
        return JOptionPane.showConfirmDialog(this, message, title,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    void showError(Exception error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), "Quallo", JOptionPane.ERROR_MESSAGE);
    }

    static JLabel avatar(String person, int size) {
        String[] names = person.strip().split("\\s+");
        String initials = names.length == 0 || names[0].isEmpty() ? "?" :
            names[0].substring(0, 1) + (names.length > 1 ? names[names.length - 1].substring(0, 1) : "");
        Color tint = color(COLORS.get(Math.floorMod(person.hashCode(), COLORS.size())));
        JLabel badge = new JLabel(initials.toUpperCase(Locale.ROOT), SwingConstants.CENTER) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                g.setColor(blend(CARD, tint, .29));
                g.fillOval(0, 0, getWidth(), getHeight());
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        badge.setPreferredSize(new Dimension(size, size));
        badge.setFont(font(size < 27 ? 10 : 12, true));
        badge.setForeground(tint);
        badge.setToolTipText(person);
        return badge;
    }

    final class SearchField extends JTextField {
        SearchField() {
            setPreferredSize(new Dimension(210, 34));
            setOpaque(false);
            setForeground(TEXT);
            setCaretColor(ACCENT);
            setSelectionColor(ACCENT_BG);
            pad(this, 6, 31, 6, 8);
            setToolTipText("Search tasks (Ctrl+K)");
            getAccessibleContext().setAccessibleName("Search tasks");
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            g.setColor(SIDE);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 11, 11);
            g.setColor(isFocusOwner() ? ACCENT : LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 11, 11);
            new Glyph("search", QUIET, 16).paintIcon(this, g, 9, 9);
            if (getText().isEmpty()) {
                g.setColor(QUIET);
                g.setFont(font(13, false));
                g.drawString("Search tasks", 31, 22);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
