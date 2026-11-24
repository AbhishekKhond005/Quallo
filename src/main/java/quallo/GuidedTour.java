package quallo;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Arrays;
import javax.swing.*;
import static quallo.Theme.*;

final class GuidedTour {
    private static final String TARGET_PROPERTY = "quallo.tourTarget";

    private enum Step {
        PROJECTS("Projects", "Find your projects", "Click Projects to see your workspaces, boards, and any templates you save.", "Open Projects"),
        BOARD("New board", "Open a project board", "Click New board. Enter a board name and, if this is your first workspace, a workspace name. Click OK to open your empty board.", "New board"),
        LIST("List", "Set up a workflow", "Click List and name a stage in your workflow, such as Backlog or Playtest & QA. You can set a work-in-progress limit or mark incoming cards complete.", "Add a list"),
        CARD("New card", "Capture a task", "Click New card and enter a feature, bug, or playtest task. Add a due date to see it in Calendar. Use the editor tabs for checklists, dependencies, files, and discussion.", "New card"),
        FILTERS("Filters", "Find the work that matters", "Click Filters to narrow the board by label, assignee, priority, type, or status. Choose Apply, Clear all, or Cancel to return to the tour.", "Open filters"),
        TABLE("Table", "Review cards in a table", "Click Table for a sortable list of cards. Select a column header to sort, or open a row to edit its card.", "Open Table"),
        CALENDAR("Calendar", "Check upcoming deadlines", "Click Calendar to see cards on their due dates. Use the arrows next to Today to move between months.", "Open Calendar"),
        TIMELINE("Timeline", "Plan work over time", "Click Timeline to see how work overlaps. Cards with both a start date and a due date span the days between them.", "Open Timeline"),
        OVERVIEW("Overview", "Review the project", "Click Overview to check progress, deadlines, and blocked work before your next playtest or milestone.", "Open Overview"),
        INBOX("Inbox", "Capture ideas in Inbox", "Click Inbox. You can add a quick note here and move it to a project board later.", "Open Inbox"),
        MY_WORK("My work", "Find your assigned work", "Click My work to find cards assigned to you, watched cards, and your checklist assignments across workspaces.", "Open My work"),
        SETTINGS("Settings and backup", "Keep a backup", "Click Settings to see the local data folder and ZIP backup options. Close the settings dialog to finish the tour.", "Open Settings"),
        COMPLETE(null, "Tour complete", "You can return to Tutorial to replay the tour or read the game development lessons.", "Done");

        final String target, title, instruction, action;

        Step(String target, String title, String instruction, String action) {
            this.target = target;
            this.title = title;
            this.instruction = instruction;
            this.action = action;
        }

        boolean onBoard() {
            return ordinal() >= LIST.ordinal() && ordinal() <= OVERVIEW.ordinal();
        }
    }

    private static final Step[] STEPS = Step.values();
    private final StudioWindow app;
    private Spotlight spotlight;
    private Component originalGlass, originalFocus;
    private JButton originalDefault;
    private Step step;

    GuidedTour(StudioWindow app) {
        this.app = app;
    }

    static <T extends AbstractButton> T target(T button, String id) {
        button.putClientProperty(TARGET_PROPERTY, id);
        return button;
    }

    boolean isActive() {
        return spotlight != null;
    }

    void start() {
        if (isActive()) return;
        originalFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        originalGlass = app.getGlassPane();
        originalDefault = app.getRootPane().getDefaultButton();
        spotlight = new Spotlight();
        app.setGlassPane(spotlight);
        spotlight.setVisible(true);
        show(Step.PROJECTS);
    }

    void close() {
        if (!isActive()) return;
        spotlight.setVisible(false);
        app.setGlassPane(originalGlass);
        app.getRootPane().setDefaultButton(originalDefault);
        spotlight = null;
        originalGlass = null;
        originalDefault = null;
        if (originalFocus != null && originalFocus.isShowing()) originalFocus.requestFocusInWindow();
        else app.getRootPane().requestFocusInWindow();
        originalFocus = null;
    }

    private void go(int direction) {
        int index = step.ordinal() + direction;
        while (index > 0 && index < STEPS.length - 1 && !available(STEPS[index])) index += direction;
        show(STEPS[index]);
    }

    private boolean available(Step candidate) {
        if (!candidate.onBoard()) return true;
        return app.board != null && (candidate != Step.CARD || app.board.firstLane() != null);
    }

    private void show(Step next) {
        step = next;
        if (next == Step.BOARD && !app.section.equals("Projects")) app.openSection("Projects");
        if (next.onBoard() && !app.section.equals("Project")) app.openSection("Project");
        boolean existing = next == Step.BOARD && app.board != null;
        String id = existing ? "Open board " + app.board.id : next.target;
        String title = existing ? "Open your board" : next.title;
        String instruction = existing ? "Click Open to continue with " + BoardCanvas.shortText(app.board.name, 55) + "." : next.instruction;
        spotlight.showStep(id, title, instruction, existing ? "Open board" : next.action);
    }

    private void activate() {
        Step current = step;
        if (current == Step.COMPLETE) { close(); return; }
        AbstractButton button = spotlight.target;
        if (button == null || !button.isShowing()) { go(1); return; }
        int previousCount = switch (current) {
            case LIST -> app.board.lanes.size();
            case CARD -> app.board.activeCards().size();
            default -> 0;
        };
        button.doClick(0);
        if (!isActive() || current != step) return;
        boolean cancelled = switch (current) {
            case BOARD -> !app.section.equals("Project") || app.board == null;
            case LIST -> app.board.lanes.size() == previousCount;
            case CARD -> app.board.activeCards().size() == previousCount;
            default -> false;
        };
        if (cancelled) show(current);
        else go(1);
    }

    private static AbstractButton findTarget(Container container, String id) {
        for (Component child : container.getComponents()) {
            if (!child.isShowing()) continue;
            if (child instanceof AbstractButton button && id.equals(button.getClientProperty(TARGET_PROPERTY))) return button;
            if (child instanceof Container nested) {
                AbstractButton result = findTarget(nested, id);
                if (result != null) return result;
            }
        }
        return null;
    }

    private final class Spotlight extends JPanel {
        private static final int POPUP_WIDTH = 365;
        private static final int MARGIN = 14;
        private static final int OFFSET = 16;
        private static final int SCALE = 5;
        private static final ConvolveOp BLUR = blur();

        private final JPanel popup = new Surface(new BorderLayout(0, 11), SURFACE, ACCENT, 20);
        private final JLabel progress = text("", 11, true, ACCENT);
        private final JLabel heading = text("", 21, true, TEXT);
        private final JTextArea instructions = area("", 0, 26);
        private final JPanel actions = Theme.row(6);
        private final Theme.Button back = button("Back", "left", () -> go(-1)).ghost();
        private final Theme.Button skip = button("Skip step", null, () -> go(1)).ghost();
        private Theme.Button advance;
        private String targetId;
        private AbstractButton target;
        private Rectangle targetBounds = new Rectangle();
        private Shape highlight;
        private BufferedImage backdrop;
        private boolean pressedTarget;

        Spotlight() {
            setLayout(null);
            setOpaque(false);
            setFocusCycleRoot(true);
            setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
            getAccessibleContext().setAccessibleName("Guided tour");
            pad(popup, 14, 16, 15, 16);

            JPanel top = stack();
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.add(progress, BorderLayout.WEST);
            Theme.Button close = button("", "close", GuidedTour.this::close).ghost();
            close.setToolTipText("Close tour");
            close.getAccessibleContext().setAccessibleName("Close tour");
            header.add(close, BorderLayout.EAST);
            line(top, header);
            line(top, heading);
            popup.add(top, BorderLayout.NORTH);

            instructions.setBackground(SURFACE);
            instructions.setForeground(MUTED);
            instructions.setEditable(false);
            instructions.setFocusable(false);
            pad(instructions, 0, 0, 0, 0);
            popup.add(instructions, BorderLayout.CENTER);
            popup.add(actions, BorderLayout.SOUTH);
            add(popup);

            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close-tour");
            getActionMap().put("close-tour", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { GuidedTour.this.close(); }
            });
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent event) { refresh(); }
            });
            MouseAdapter pointer = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    setCursor(Cursor.getPredefinedCursor(targetBounds.contains(event.getPoint()) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }

                @Override public void mousePressed(MouseEvent event) {
                    pressedTarget = SwingUtilities.isLeftMouseButton(event) && targetBounds.contains(event.getPoint());
                }

                @Override public void mouseReleased(MouseEvent event) {
                    if (pressedTarget && targetBounds.contains(event.getPoint())) activate();
                    pressedTarget = false;
                }
            };
            addMouseListener(pointer);
            addMouseMotionListener(pointer);
            addMouseWheelListener(event -> event.consume());
        }

        void showStep(String id, String title, String message, String action) {
            targetId = id;
            target = null;
            targetBounds = new Rectangle();
            highlight = null;
            pressedTarget = false;
            plain(progress, step == Step.COMPLETE ? "GUIDED TOUR" : String.format("STEP %d OF %d", step.ordinal() + 1, STEPS.length - 1));
            plain(heading, title);
            instructions.setText(message);
            actions.removeAll();
            back.setEnabled(step != Step.PROJECTS);
            actions.add(back);
            if (step != Step.COMPLETE) actions.add(skip);
            advance = primary(action, "arrow", GuidedTour.this::activate);
            actions.add(advance);
            app.getRootPane().setDefaultButton(advance);
            app.validate();
            refresh();
            SwingUtilities.invokeLater(() -> {
                if (spotlight == this) advance.requestFocusInWindow();
            });
        }

        private void refresh() {
            if (spotlight != this || getWidth() <= 0 || getHeight() <= 0) return;
            target = targetId == null ? null : findTarget(app.getContentPane(), targetId);
            if (target != null) {
                target.scrollRectToVisible(new Rectangle(target.getSize()));
                targetBounds = SwingUtilities.convertRectangle(target, target.getVisibleRect(), this);
                if (targetBounds.isEmpty()) target = null;
            }
            if (target == null) {
                targetBounds = new Rectangle();
                highlight = null;
            } else {
                Rectangle border = new Rectangle(targetBounds);
                border.grow(4, 4);
                highlight = new RoundRectangle2D.Float(border.x, border.y, border.width, border.height, 13, 13);
            }
            layoutPopup();
            captureBackdrop();
            repaint();
        }

        private void layoutPopup() {
            int width = Math.min(POPUP_WIDTH, getWidth() - 2 * MARGIN);
            Insets padding = popup.getInsets();
            instructions.setSize(width - padding.left - padding.right, Short.MAX_VALUE);
            int height = popup.getPreferredSize().height;
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            if (highlight != null) {
                Rectangle target = highlight.getBounds();
                if (target.x + target.width + OFFSET + width <= getWidth() - MARGIN) {
                    x = target.x + target.width + OFFSET;
                    y = target.y;
                } else if (target.y + target.height + OFFSET + height <= getHeight() - MARGIN) {
                    x = target.x + target.width - width;
                    y = target.y + target.height + OFFSET;
                } else if (target.x - OFFSET - width >= MARGIN) {
                    x = target.x - OFFSET - width;
                    y = target.y;
                } else {
                    x = target.x;
                    y = target.y - OFFSET - height;
                }
            }
            x = Math.clamp(x, MARGIN, Math.max(MARGIN, getWidth() - width - MARGIN));
            y = Math.clamp(y, MARGIN, Math.max(MARGIN, getHeight() - height - MARGIN));
            popup.setBounds(x, y, width, height);
            popup.validate();
        }

        private void captureBackdrop() {
            BufferedImage reduced = new BufferedImage(Math.max(1, getWidth() / SCALE),
                Math.max(1, getHeight() / SCALE), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = reduced.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.scale(reduced.getWidth() / (double) getWidth(), reduced.getHeight() / (double) getHeight());
                Container content = app.getContentPane();
                Point origin = SwingUtilities.convertPoint(content, 0, 0, this);
                g.translate(origin.x, origin.y);
                content.printAll(g);
            } finally {
                g.dispose();
            }
            backdrop = BLUR.filter(reduced, null);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            if (backdrop != null) {
                Area shaded = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
                if (highlight != null) shaded.subtract(new Area(highlight));
                Shape old = g.getClip();
                g.clip(shaded);
                g.drawImage(backdrop, 0, 0, getWidth(), getHeight(), null);
                g.setColor(new Color(10, 8, 17, 147));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setClip(old);
            }
            if (highlight != null) {
                g.setColor(new Color(192, 170, 255, 73));
                g.setStroke(new BasicStroke(7));
                g.draw(highlight);
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(2));
                g.draw(highlight);
            }
            g.dispose();
        }

        private static ConvolveOp blur() {
            float[] weights = new float[25];
            Arrays.fill(weights, 1f / weights.length);
            return new ConvolveOp(new Kernel(5, 5, weights), ConvolveOp.EDGE_NO_OP, null);
        }
    }
}
