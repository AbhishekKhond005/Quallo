package quallo;

import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import static quallo.Theme.*;

final class FormsUi {
    static <T> void runTask(Window owner, String message, Callable<T> task, Consumer<T> done, Consumer<Exception> error) {
        JDialog waiting = new JDialog(owner, "Quallo", Dialog.ModalityType.APPLICATION_MODAL);
        waiting.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JPanel panel = panel(new BorderLayout(0, 10), SURFACE);
        pad(panel, 16, 20, 16, 20);
        panel.add(text(message, 14, true, TEXT), BorderLayout.NORTH);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        panel.add(bar, BorderLayout.CENTER);
        waiting.setContentPane(panel);
        waiting.setSize(320, 110);
        waiting.setLocationRelativeTo(owner);
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }
            @Override protected void done() {
                waiting.dispose();
                try { done.accept(get()); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); error.accept(exception); }
                catch (ExecutionException exception) {
                    error.accept(exception.getCause() instanceof Exception cause ? cause : exception);
                }
            }
        }.execute();
        waiting.setVisible(true);
    }
}
