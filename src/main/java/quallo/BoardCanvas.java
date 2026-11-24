package quallo;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import static quallo.Model.*;
import quallo.Theme.Button;
import static quallo.Theme.*;

final class BoardCanvas extends JPanel {
    private static final int LANE_WIDTH = 219;
    private static final int LANE_GAP = 12;
    private static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("MMM d");
    private static final DataFlavor CARD_FLAVOR = new DataFlavor(Card.class, "Quallo card");
    private final StudioWindow app;
    private final Board board;
    private final Set<Card> visible;
    private final LocalDate today = LocalDate.now();

    BoardCanvas(StudioWindow app) {
        super(new BorderLayout());
        setOpaque(false);
        this.app = app;
        board = app.board;
        visible = new HashSet<>(app.matchingCards());
        List<Lane> lanes = board.lanes.stream().filter(lane -> !lane.archived).toList();
        if (lanes.isEmpty()) {
            add(Views.empty("This board has no lists", "Create a list to start organizing tasks.",
                primary("Add a list", "plus", () -> BoardsUi.editLane(app, null))), BorderLayout.CENTER);
            return;
        }
        LaneStrip strip = new LaneStrip(lanes.size());
        lanes.forEach(lane -> strip.add(lanePanel(lane)));
        app.currentScroll = scroll(strip);
        add(app.currentScroll, BorderLayout.CENTER);
    }

    private JPanel lanePanel(Lane lane) {
        JPanel column = new Surface(new BorderLayout(), SURFACE, LINE, 18);
        pad(column, 0, 9, 9, 9);
        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        pad(heading, 9, 4, 8, 2);
        JPanel title = row(5);
        title.add(glyph(lane.done ? "check" : "dot", color(lane.color), 15));
        JLabel name = text(lane.name, 15, true, TEXT);
        name.setToolTipText(lane.name);
        Dimension titleSize = name.getPreferredSize();
        name.setPreferredSize(new Dimension(Math.min(122, titleSize.width), titleSize.height));
        title.add(name);
        long count = lane.cards.stream().filter(visible::contains).count();
        String counter = lane.limit > 0 ? lane.openCount() + "/" + lane.limit : String.valueOf(count);
        JLabel badge = badge(counter, lane.limit > 0 && lane.openCount() > lane.limit ? DANGER : QUIET);
        badge.setToolTipText(lane.limit > 0 ? "Open tasks / work-in-progress limit" : "Visible cards");
        title.add(badge);
        heading.add(title, BorderLayout.CENTER);
        Button menu = new Button("", "menu").ghost();
        pad(menu, 6, 5, 6, 5);
        menu.setToolTipText(lane.name + " list options");
        menu.addActionListener(event -> BoardsUi.laneMenu(app, lane, menu));
        heading.add(menu, BorderLayout.EAST);
        column.add(heading, BorderLayout.NORTH);

        JPanel cards = stack();
        boolean any = false;
        for (Card card : lane.cards) {
            if (!visible.contains(card)) continue;
            line(cards, new Tile(lane, card));
            gap(cards, 8);
            any = true;
        }
        if (!any) {
            JLabel placeholder = text("No cards", 12, false, QUIET);
            pad(placeholder, 13, 10, 14, 0);
            line(cards, placeholder);
        }
        Button add = button("Add card", "plus", () -> app.addCard(lane)).ghost().left();
        add.setMaximumSize(new Dimension(Integer.MAX_VALUE, 33));
        line(cards, add);
        column.add(cards, BorderLayout.CENTER);
        column.setTransferHandler(new CardTransfer(lane, null));
        cards.setTransferHandler(new CardTransfer(lane, null));
        return column;
    }

    private final class Tile extends Surface {
        private boolean hover;
        private final Card card;

        Tile(Lane lane, Card card) {
            super(new BorderLayout(0, 7), CARD, LINE, 13);
            this.card = card;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            pad(this, 10, 10, 9, 10);
            JPanel information = stack();
            JPanel tags = row(5);
            card.labels.stream().limit(2).forEach(label -> tags.add(
                badge(shortText(label, 18), color(board.labels.getOrDefault(label, COLORS.getFirst())))));
            if (card.labels.isEmpty()) tags.add(badge(card.type, QUIET));
            if (card.priority.equals("High") || card.priority.equals("Urgent")) tags.add(badge(card.priority, ORANGE));
            line(information, tags);
            gap(information, 7);
            JLabel name = text("", 14, true, card.complete ? color("#DAD6E9") : TEXT);
            name.putClientProperty("html.disable", false);
            name.setText("<html>" + escape(card.title) + "</html>");
            name.setPreferredSize(new Dimension(160, 34));
            name.setVerticalAlignment(SwingConstants.TOP);
            line(information, name);
            add(information, BorderLayout.CENTER);
            JPanel footer = new JPanel(new BorderLayout(2, 0));
            footer.setOpaque(false);
            JPanel meta = row(6);
            if (card.due != null) meta.add(detail(card.due.format(CARD_DATE), "cal",
                card.complete ? GREEN : card.overdue(today) ? DANGER : QUIET));
            if (card.checklistTotal() > 0) meta.add(detail(card.checklistDone() + "/" + card.checklistTotal(), "checks", QUIET));
            if (card.due == null && card.checklistTotal() == 0) {
                if (!card.attachments.isEmpty()) meta.add(detail(String.valueOf(card.attachments.size()), "paperclip", QUIET));
                if (!card.comments.isEmpty()) meta.add(detail(String.valueOf(card.comments.size()), "comment", QUIET));
            }
            footer.add(meta, BorderLayout.WEST);
            if (!card.assignees.isEmpty()) footer.add(StudioWindow.avatar(card.assignees.iterator().next(), 23), BorderLayout.EAST);
            footer.setMinimumSize(new Dimension(0, 23));
            footer.setPreferredSize(new Dimension(0, 23));
            add(footer, BorderLayout.SOUTH);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 124));
            setPreferredSize(new Dimension(195, 114));
            setToolTipText(card.title + (card.complete ? " (complete)" : ""));
            getAccessibleContext().setAccessibleName(card.title);
            setTransferHandler(new CardTransfer(lane, card));
            MouseAdapter listener = new MouseAdapter() {
                private Point pressed;
                private boolean dragged;
                @Override public void mouseEntered(MouseEvent event) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent event) { hover = false; repaint(); }
                @Override public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    pressed = event.getPoint();
                    dragged = false;
                    requestFocusInWindow();
                }
                @Override public void mouseDragged(MouseEvent event) {
                    if (pressed == null || dragged || pressed.distance(event.getPoint()) < 7) return;
                    dragged = true;
                    getTransferHandler().exportAsDrag(Tile.this, event, TransferHandler.MOVE);
                }
                @Override public void mouseReleased(MouseEvent event) {
                    if (pressed != null && !dragged && contains(event.getPoint())) app.editCard(board, card);
                    pressed = null;
                }
            };
            addMouseListener(listener);
            addMouseMotionListener(listener);
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "edit");
            getActionMap().put("edit", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { app.editCard(board, card); }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!hover && !isFocusOwner()) return;
            Graphics2D g = smooth(graphics);
            g.setColor(isFocusOwner() ? ACCENT : blend(LINE, ACCENT, .42));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 13, 13);
            g.dispose();
        }
    }

    private final class CardTransfer extends TransferHandler {
        private final Lane lane;
        private final Card card;

        CardTransfer(Lane lane, Card card) {
            this.lane = lane;
            this.card = card;
        }

        @Override public int getSourceActions(JComponent component) { return card == null ? NONE : MOVE; }

        @Override protected Transferable createTransferable(JComponent component) {
            if (card == null) return null;
            return new Transferable() {
                @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{CARD_FLAVOR}; }
                @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return CARD_FLAVOR.equals(flavor); }
                @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                    return card;
                }
            };
        }

        @Override public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(CARD_FLAVOR);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                Card moving = (Card) support.getTransferable().getTransferData(CARD_FLAVOR);
                if (board.card(moving.id) != moving || moving == card) return false;
                Card before = card;
                if (card != null && support.getDropLocation().getDropPoint().y > support.getComponent().getHeight() / 2) {
                    int next = lane.cards.indexOf(card) + 1;
                    before = next < lane.cards.size() ? lane.cards.get(next) : null;
                }
                moveCard(board, board, moving, lane, before, app.state.author);
                app.changed();
                return true;
            } catch (UnsupportedFlavorException | java.io.IOException error) {
                app.showError(error);
                return false;
            }
        }
    }

    private final class LaneStrip extends JPanel implements Scrollable {
        private final int count;

        LaneStrip(int count) {
            super(new GridLayout(1, count, LANE_GAP, 0));
            this.count = count;
            setOpaque(false);
        }

        @Override public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(count * LANE_WIDTH + (count - 1) * LANE_GAP, size.height);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle rect, int orientation, int direction) { return 22; }
        @Override public int getScrollableBlockIncrement(Rectangle rect, int orientation, int direction) {
            return Math.max(22, (orientation == SwingConstants.HORIZONTAL ? rect.width : rect.height) - 30);
        }
        @Override public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport viewport && viewport.getWidth() >= getPreferredSize().width;
        }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport && viewport.getHeight() >= getPreferredSize().height;
        }
    }

    private static JLabel detail(String value, String icon, Color color) {
        JLabel label = text(value, 11, false, color);
        label.setIcon(new Glyph(icon, color, 13));
        label.setIconTextGap(3);
        return label;
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String shortText(String text, int limit) {
        return text.length() > limit ? text.substring(0, limit - 1) + "…" : text;
    }
}
