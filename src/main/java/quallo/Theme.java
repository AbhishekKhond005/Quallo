package quallo;

import java.awt.*;
import java.awt.geom.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;

final class Theme {
    static final Color BASE = color("#101017"), SIDE = color("#15151F"), SURFACE = color("#181823");
    static final Color CARD = color("#20202D"), HOVER = color("#2A283B"), LINE = color("#2D2C3C");
    static final Color TEXT = color("#F1EFFA"), MUTED = color("#ADA9C0"), QUIET = color("#89859D");
    static final Color ACCENT = color("#C0AAFF"), ACCENT_BG = color("#32284D");
    static final Color GREEN = color("#7DDBC0"), ORANGE = color("#FFB17C");
    static final Color DANGER = color("#FF9DAE");

    private static final String FONT = java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getAvailableFontFamilyNames()).contains("Lato") ? "Lato" : Font.SANS_SERIF;

    static void setup() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) { }
        UIManager.getDefaults().keySet().stream().filter(key -> key.toString().endsWith(".font")).toList()
            .forEach(key -> UIManager.put(key, new javax.swing.plaf.FontUIResource(font(13, false))));
        for (String name : new String[]{"Panel", "OptionPane", "ScrollPane", "Viewport", "PopupMenu", "Menu", "MenuItem",
            "CheckBox", "RadioButton", "ComboBox", "List", "Table", "TableHeader", "TabbedPane", "Spinner",
            "FileChooser", "ToolTip", "TextField", "TextArea", "FormattedTextField", "Button"}) {
            UIManager.put(name + ".background", SURFACE);
            UIManager.put(name + ".foreground", TEXT);
        }
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextArea.caretForeground", TEXT);
        UIManager.put("FormattedTextField.caretForeground", TEXT);
        UIManager.put("List.selectionBackground", ACCENT_BG);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("Table.selectionBackground", ACCENT_BG);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.gridColor", LINE);
        UIManager.put("MenuItem.selectionBackground", ACCENT_BG);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("control", SURFACE);
        UIManager.put("nimbusFocus", ACCENT);
    }

    static Color color(String value) {
        try { return Color.decode(value); }
        catch (IllegalArgumentException error) { return new Color(0xBBA3FF); }
    }

    static Color blend(Color left, Color right, double alpha) {
        return new Color(
            (int) (left.getRed() * (1 - alpha) + right.getRed() * alpha),
            (int) (left.getGreen() * (1 - alpha) + right.getGreen() * alpha),
            (int) (left.getBlue() * (1 - alpha) + right.getBlue() * alpha));
    }

    static Font font(int size, boolean bold) {
        return new Font(FONT, bold ? Font.BOLD : Font.PLAIN, size);
    }

    static JLabel text(String value, int size, boolean bold, Color color) {
        JLabel label = new JLabel();
        plain(label, value);
        label.setFont(font(size, bold));
        label.setForeground(color);
        return label;
    }

    static void plain(JLabel label, String value) {
        label.putClientProperty("html.disable", true);
        label.setText(value);
    }

    static JPanel row(int gap) {
        JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0)) {
            @Override public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                size.width = Math.max(0, size.width - gap * 2);
                return size;
            }
        };
        pane.setOpaque(false);
        pane.setBorder(new EmptyBorder(0, -gap, 0, -gap));
        return pane;
    }

    static JPanel stack() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    static JPanel panel(LayoutManager layout, Color background) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(background);
        return panel;
    }

    static void line(JPanel panel, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(component);
    }

    static void gap(JPanel panel, int pixels) {
        line(panel, (JComponent) Box.createVerticalStrut(pixels));
    }

    static void pad(JComponent component, int top, int left, int bottom, int right) {
        component.setBorder(new EmptyBorder(top, left, bottom, right));
    }

    static JTextField field(String value, int columns) {
        JTextField field = new JTextField(value, columns);
        field.setBackground(CARD);
        field.setForeground(TEXT);
        field.setSelectionColor(ACCENT_BG);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
            new EmptyBorder(8, 10, 8, 10)));
        return field;
    }

    static JTextArea area(String value, int rows, int columns) {
        JTextArea area = new JTextArea(value, rows, columns);
        area.setFont(font(14, false));
        area.setForeground(TEXT);
        area.setBackground(CARD);
        area.setCaretColor(TEXT);
        area.setSelectionColor(ACCENT_BG);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        pad(area, 9, 10, 9, 10);
        return area;
    }

    static JScrollPane scroll(Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(null);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        for (JScrollBar bar : new JScrollBar[]{pane.getVerticalScrollBar(), pane.getHorizontalScrollBar()}) {
            bar.setUnitIncrement(18);
            bar.setPreferredSize(new Dimension(9, 9));
            bar.setUI(new BasicScrollBarUI() {
                @Override protected void configureScrollBarColors() {
                    thumbColor = LINE;
                    trackColor = BASE;
                }
                @Override protected JButton createDecreaseButton(int orientation) { return hidden(); }
                @Override protected JButton createIncreaseButton(int orientation) { return hidden(); }
                private JButton hidden() {
                    JButton button = new JButton();
                    button.setPreferredSize(new Dimension());
                    return button;
                }
            });
        }
        return pane;
    }

    static Button button(String title, String icon, Runnable action) {
        Button button = new Button(title, icon);
        button.addActionListener(event -> action.run());
        return button;
    }

    static Button primary(String title, String icon, Runnable action) {
        return button(title, icon, action).style(ACCENT, BASE);
    }

    static JLabel glyph(String name, Color color, int size) {
        return new JLabel(new Glyph(name, color, size));
    }

    static JLabel badge(String value, Color tint) {
        JLabel badge = new JLabel() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                g.setColor(blend(CARD, tint, 0.16));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        plain(badge, value);
        badge.setFont(font(11, true));
        badge.setForeground(tint);
        pad(badge, 4, 7, 4, 7);
        return badge;
    }

    static Graphics2D smooth(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g;
    }

    static final class Button extends JButton {
        private final String glyph;
        private Color fill = SURFACE;
        private Color ink = MUTED;
        private boolean outline = true;

        Button(String title, String glyph) {
            super(title);
            this.glyph = glyph;
            putClientProperty("html.disable", true);
            setFont(font(13, true));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setRolloverEnabled(true);
            setIconTextGap(8);
            pad(this, 8, title.isEmpty() ? 8 : 11, 8, title.isEmpty() ? 8 : 11);
            style(fill, ink);
        }

        Button style(Color fill, Color ink) {
            this.fill = fill;
            this.ink = ink;
            setForeground(ink);
            if (glyph != null) setIcon(new Glyph(glyph, ink, 15));
            return this;
        }

        Button ghost() {
            outline = false;
            return style(null, MUTED);
        }

        Button left() {
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            Color surface = getModel().isRollover() ? fill == null ? HOVER : blend(fill, TEXT, .09) : fill;
            if (surface != null) {
                g.setColor(surface);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 11, 11);
            }
            if (hasFocus() || outline && fill == SURFACE) {
                g.setColor(hasFocus() ? ACCENT : LINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 11, 11);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    static class Surface extends JPanel {
        private final int radius;
        private final Color fill;
        private final Color border;

        Surface(LayoutManager layout, Color fill, Color border, int radius) {
            super(layout);
            this.fill = fill;
            this.border = border;
            this.radius = radius;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            if (border != null) {
                g.setColor(border);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g.dispose();
        }
    }

    static final class Glyph implements Icon {
        private final String name;
        private final Color color;
        private final int size;

        Glyph(String name, Color color, int size) {
            this.name = name;
            this.color = color;
            this.size = size;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = smooth(graphics);
            g.translate(x, y);
            g.scale(size / 20d, size / 20d);
            g.setColor(color);
            g.setStroke(new BasicStroke(1.55f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (name) {
                case "logo" -> {
                    g.fillRoundRect(0, 0, 20, 20, 6, 6);
                    g.setColor(BASE);
                    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawRoundRect(5, 5, 9, 9, 4, 4);
                    path(g, 11, 11, 15, 15);
                }
                case "board" -> { g.drawRoundRect(2, 3, 16, 14, 3, 3); path(g, 7, 7, 7, 13); path(g, 12, 7, 12, 11); }
                case "grid" -> {
                    for (int xx : new int[]{3, 11}) for (int yy : new int[]{3, 11}) g.drawRoundRect(xx, yy, 6, 6, 2, 2);
                }
                case "table" -> { g.drawRoundRect(2, 3, 16, 14, 3, 3); path(g, 2, 8, 18, 8); path(g, 2, 12, 18, 12); path(g, 8, 8, 8, 17); }
                case "cal" -> {
                    g.drawRoundRect(2, 4, 16, 14, 3, 3); path(g, 2, 8, 18, 8); path(g, 6, 2, 6, 6); path(g, 14, 2, 14, 6);
                    g.fillOval(6, 11, 2, 2); g.fillOval(11, 11, 2, 2);
                }
                case "time" -> { path(g, 3, 4, 3, 16, 18, 16); path(g, 6, 7, 11, 7); path(g, 9, 11, 16, 11); }
                case "stats" -> { path(g, 3, 3, 3, 17, 17, 17); path(g, 7, 13, 7, 9); path(g, 11, 13, 11, 5); path(g, 15, 13, 15, 7); }
                case "search" -> { g.drawOval(3, 3, 10, 10); path(g, 12, 12, 17, 17); }
                case "plus" -> { path(g, 5, 10, 15, 10); path(g, 10, 5, 10, 15); }
                case "check" -> path(g, 4, 10, 8, 14, 16, 6);
                case "checks" -> { g.drawRoundRect(3, 3, 14, 14, 3, 3); path(g, 6, 10, 9, 13, 14, 7); }
                case "close" -> { path(g, 5, 5, 15, 15); path(g, 15, 5, 5, 15); }
                case "right" -> path(g, 7, 4, 13, 10, 7, 16);
                case "left" -> path(g, 13, 4, 7, 10, 13, 16);
                case "down" -> path(g, 5, 8, 10, 13, 15, 8);
                case "menu" -> { for (int i : new int[]{4, 9, 14}) g.fillOval(i, 9, 2, 2); }
                case "filter" -> { path(g, 3, 5, 17, 5); path(g, 6, 10, 14, 10); path(g, 8, 15, 12, 15); }
                case "book" -> { path(g, 10, 6, 7, 3, 3, 3, 3, 15, 7, 15, 10, 17, 13, 15, 17, 15, 17, 3, 13, 3, 10, 6, 10, 17); }
                case "star", "star-fill" -> {
                    Path2D p = shape(10, 2, 12.3, 7.2, 18, 7.7, 13.8, 11.5, 15, 17, 10, 14, 5, 17, 6.2, 11.5, 2, 7.7, 7.7, 7.2);
                    p.closePath();
                    if (name.equals("star")) g.draw(p); else g.fill(p);
                }
                case "archive" -> { g.drawRoundRect(2, 3, 16, 4, 2, 2); path(g, 4, 7, 4, 16, 16, 16, 16, 7); path(g, 8, 10, 12, 10); }
                case "settings" -> { g.drawOval(4, 4, 12, 12); g.drawOval(8, 8, 4, 4); path(g, 10, 2, 10, 4); path(g, 10, 16, 10, 18); path(g, 2, 10, 4, 10); path(g, 16, 10, 18, 10); }
                case "people" -> { g.drawOval(6, 3, 7, 7); g.drawArc(3, 12, 13, 8, 0, 180); }
                case "arrow" -> { path(g, 4, 10, 16, 10); path(g, 11, 5, 16, 10, 11, 15); }
                case "paperclip" -> { g.draw(new Arc2D.Double(4, 4, 12, 12, 75, 260, Arc2D.OPEN)); path(g, 13, 5, 7, 11, 9, 13, 14, 8); }
                case "comment" -> { path(g, 4, 14, 2, 6, 5, 3, 15, 3, 18, 6, 18, 12, 15, 15, 9, 15, 4, 18, 4, 14); }
                case "dot" -> g.fillOval(6, 6, 8, 8);
                default -> g.drawOval(4, 4, 12, 12);
            }
            g.dispose();
        }

        private static void path(Graphics2D g, double... points) {
            g.draw(shape(points));
        }

        private static Path2D shape(double... points) {
            Path2D path = new Path2D.Double();
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
            return path;
        }
    }

    static void action(JPopupMenu menu, String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.putClientProperty("html.disable", true);
        item.addActionListener(event -> action.run());
        menu.add(item);
    }

    static void showMenu(JComponent anchor, Consumer<JPopupMenu> populate) {
        JPopupMenu menu = new JPopupMenu();
        populate.accept(menu);
        menu.show(anchor, 0, anchor.getHeight() + 5);
    }
}
