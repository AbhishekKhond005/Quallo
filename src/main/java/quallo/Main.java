package quallo;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class Main {
    public static void main(String[] args) {
        Path directory = args.length == 2 && args[0].equals("--data-dir") ? Path.of(args[1]) : Store.defaultDirectory();
        SwingUtilities.invokeLater(() -> {
            Theme.setup();
            try {
                Store store = new Store(directory);
                try {
                    Model.State state = store.load();
                    new StudioWindow(store, state).setVisible(true);
                } catch (Exception error) {
                    store.close();
                    throw error;
                }
            } catch (Exception error) {
                if (!GraphicsEnvironment.isHeadless()) {
                    JOptionPane.showMessageDialog(null, error.getMessage(), "Quallo could not start", JOptionPane.ERROR_MESSAGE);
                } else {
                    error.printStackTrace();
                }
            }
        });
    }

    private Main() {}
}
