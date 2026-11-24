package quallo;

import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import javax.swing.*;
import static quallo.Theme.*;

final class Forms {
    static JPanel form(Object... entries) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints position = new GridBagConstraints();
        position.anchor = GridBagConstraints.NORTHWEST;
        for (int i = 0; i < entries.length; i += 2) {
            position.gridy = i / 2;
            position.gridx = 0;
            position.weightx = 0;
            position.fill = GridBagConstraints.NONE;
            position.insets = new Insets(7, 0, 7, 15);
            form.add(text((String) entries[i], 13, true, MUTED), position);
            position.gridx = 1;
            position.weightx = 1;
            position.fill = GridBagConstraints.HORIZONTAL;
            position.insets = new Insets(2, 0, 5, 0);
            form.add((Component) entries[i + 1], position);
        }
        return form;
    }

    static boolean accept(Component owner, String title, JComponent content) {
        return JOptionPane.showConfirmDialog(owner, content, title, JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    static String prompt(Component owner, String title, String label, String initial) {
        JTextField input = field(initial, 25);
        input.selectAll();
        input.addHierarchyListener(event -> {
            if (input.isShowing()) SwingUtilities.invokeLater(input::requestFocusInWindow);
        });
        return accept(owner, title, form(label, input)) ? input.getText().strip() : "";
    }

    static JCheckBox check(String title, boolean selected) {
        JCheckBox check = new JCheckBox(title, selected);
        check.putClientProperty("html.disable", true);
        check.setOpaque(false);
        check.setForeground(TEXT);
        return check;
    }

    static <T> JComboBox<T> combo(T[] options, T selection) {
        JComboBox<T> combo = new JComboBox<>(options);
        combo.setSelectedItem(selection);
        combo.setMaximumRowCount(14);
        styleCombo(combo);
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                putClientProperty("html.disable", true);
                setText(value == null ? "" : value.toString());
                return this;
            }
        });
        return combo;
    }

    static JComboBox<String> optional(Collection<String> values, String selection) {
        JComboBox<String> combo = combo(values.toArray(String[]::new), selection);
        combo.insertItemAt("", 0);
        combo.setSelectedItem(values.contains(selection) ? selection : "");
        return combo;
    }

    static String value(JComboBox<String> combo) {
        return (String) combo.getSelectedItem();
    }

    static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    static LocalDate date(JTextField field) {
        if (field.getText().isBlank()) return null;
        try { return LocalDate.parse(field.getText().strip()); }
        catch (DateTimeParseException error) { throw new IllegalArgumentException("Use YYYY-MM-DD for dates.", error); }
    }
}
