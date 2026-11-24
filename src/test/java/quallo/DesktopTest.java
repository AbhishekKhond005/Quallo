package quallo;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.*;
import static quallo.StoreTest.check;

public final class DesktopTest {
    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("quallo-desktop-test-");
        try (Store store = new Store(data)) {
            StudioWindow app = onEdt(() -> {
                Theme.setup();
                StudioWindow window = new StudioWindow(store, store.load());
                window.setVisible(true);
                return window;
            });
            try {
                exercise(app, args.length == 0 ? null : Path.of(args[0]));
            } finally {
                onEdt(() -> {
                    for (Window window : Window.getWindows()) window.dispose();
                    return null;
                });
            }
        } finally {
            try (var files = Files.walk(data)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        System.out.println("Native board, editor, search, drag and drop, views and tutorial checks passed.");
    }

    private static void exercise(StudioWindow app, Path captures) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(22);
        robot.waitForIdle();
        screenshot(robot, captures, "board", app);
        for (String tab : new String[]{"Table", "Calendar", "Timeline", "Overview", "Board"}) {
            click(app, tab);
            check(onEdt(() -> app.view.equals(tab)), "open " + tab);
        }
        onEdt(() -> {
            app.search.setText("checkpoint");
            return null;
        });
        Thread.sleep(210);
        check(onEdt(() -> app.matchingCards().size() == 1), "search matching");
        onEdt(() -> {
            app.search.setText("");
            return null;
        });
        Thread.sleep(210);
        Point from = onEdt(() -> namedPoint(app, "Greybox the research outpost", 40, 35));
        Point to = onEdt(() -> namedPoint(app, "Tune player movement", 40, 28));
        robot.mouseMove(from.x, from.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int step = 1; step <= 24; step++) {
            robot.mouseMove(from.x + (to.x - from.x) * step / 24, from.y + (to.y - from.y) * step / 24);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        check(onEdt(() -> app.board.lanes.get(1).cards.getFirst().title.equals("Greybox the research outpost")), "drop onto another list");

        onEdt(() -> {
            SwingUtilities.invokeLater(() -> findButton(app, "New card").doClick());
            return null;
        });
        JDialog editor = waitEditor();
        screenshot(robot, captures, "editor", editor);
        onEdt(() -> {
            JTextField title = find(editor, JTextField.class, field -> "Card title".equals(field.getAccessibleContext().getAccessibleName()));
            title.setText("Verify smoke test build");
            findButton(editor, "Create card").doClick();
            return null;
        });
        check(onEdt(() -> app.board.activeCards().stream().anyMatch(card -> card.title.equals("Verify smoke test build"))),
            "create card from editor");
        click(app, "Project guide");
        screenshot(robot, captures, "guide", app);
        click(app, "Mark complete");
        check(onEdt(() -> app.state.completedLessons.contains("scope")), "tutorial progress");
        click(app, "My work");
        check(onEdt(() -> app.section.equals("My work")), "open My work");
    }

    private static JDialog waitEditor() throws Exception {
        for (int i = 0; i < 25; i++) {
            JDialog dialog = onEdt(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof JDialog candidate && candidate.isShowing() && candidate.getTitle().equals("New card")) return candidate;
                }
                return null;
            });
            if (dialog != null) return dialog;
            Thread.sleep(80);
        }
        throw new AssertionError("Card editor did not open");
    }

    private static void click(Container window, String label) throws Exception {
        onEdt(() -> {
            AbstractButton button = findButton(window, label);
            check(button != null, "button visible " + label);
            button.doClick();
            return null;
        });
    }

    private static AbstractButton findButton(Container window, String label) {
        return find(window, AbstractButton.class, candidate -> label.equals(candidate.getText()));
    }

    private static Point namedPoint(Container window, String name, int x, int y) {
        JComponent tile = find(window, JComponent.class, candidate -> candidate.getAccessibleContext() != null
            && name.equals(candidate.getAccessibleContext().getAccessibleName()) && candidate.getTransferHandler() != null);
        check(tile != null, "named item " + name);
        Point origin = tile.getLocationOnScreen();
        return new Point(origin.x + x, origin.y + y);
    }

    private static <T extends Component> T find(Container window, Class<T> type, java.util.function.Predicate<T> predicate) {
        for (Component candidate : window.getComponents()) {
            if (type.isInstance(candidate) && predicate.test(type.cast(candidate)) && candidate.isShowing()) return type.cast(candidate);
            if (candidate instanceof Container branch) {
                T match = find(branch, type, predicate);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void screenshot(Robot robot, Path folder, String name, Window window) throws Exception {
        if (folder == null) return;
        Files.createDirectories(folder);
        robot.waitForIdle();
        BufferedImage image = onEdt(() -> {
            BufferedImage render = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D canvas = render.createGraphics();
            window.printAll(canvas);
            canvas.dispose();
            return render;
        });
        ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    }

    private static <T> T onEdt(Callable<T> call) throws Exception {
        FutureTask<T> task = new FutureTask<>(call);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
