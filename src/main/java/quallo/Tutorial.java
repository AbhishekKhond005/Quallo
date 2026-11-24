package quallo;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import static quallo.Theme.*;

final class Tutorial {
    private record Lesson(String id, String title, String detail, String practice, String action) {}

    private static final List<Lesson> LESSONS = List.of(
        new Lesson("scope", "Set up your project",
            "Open Projects and create a workspace for your studio or team. Create a board for your game and describe the player experience you want your first playable build to demonstrate.",
            "Create the lists you want to work through. For a vertical slice, try Backlog, In Progress, Playtest & QA, and Done. Use List settings to complete cards when they enter Done.", "Projects"),
        new Lesson("plan", "Break the game into tasks",
            "Capture ideas quickly in Inbox, then use Move to board to put them on the right list. Use New card on a board when you already know where the work belongs.",
            "Create a milestone for a playable build and give it a due date. Add a feature card with a checklist of steps you can verify in a build.", "Inbox"),
        new Lesson("flow", "Manage work in progress",
            "Drag cards between lists as work advances. Set a work-in-progress limit in List settings and use Dependencies on a card when another task must finish first.",
            "Create a dependency between two cards, then select Blocked under the Status filter to find work waiting on another task.", "Projects"),
        new Lesson("test", "Run a playtest and triage bugs",
            "Add labels and local assignees from the board menu. Use custom fields to record a build version and platform. Track defects as Bug cards with steps to reproduce, expected behavior, and observed behavior.",
            "Create a Playtest card, add a comment with a finding, and attach a screenshot or notes. Move the Bug card you create into your triage list.", "Projects"),
        new Lesson("review", "Review deadlines and progress",
            "Use Calendar for due dates, Timeline for start and due dates, and Overview for list progress and blocked work. My work collects assigned and watched cards.",
            "Set Watch in My work on a card and find it in My work. Use your board’s Overview to review the progress of a milestone.", "My work"),
        new Lesson("handoff", "Reuse your process and back up your work",
            "Use Actions on a card to save it as a template, then use Card templates in the board menu to create a card from it. Automation rules can label or complete cards when they enter a list.",
            "Save your bug report as a template. Export a ZIP backup from Settings to keep a copy of your workspaces and attachments.", "Settings")
    );

    static JComponent build(StudioWindow app) {
        JPanel root = stack();
        pad(root, 25, 28, 25, 28);
        JPanel header = new Surface(new BorderLayout(20, 0), SURFACE, LINE, 19);
        pad(header, 17, 17, 17, 17);
        header.add(Views.title("Tutorial", "Set up your own game project, from the first board to playtest and release."), BorderLayout.CENTER);
        long complete = LESSONS.stream().filter(lesson -> app.state.completedLessons.contains(lesson.id())).count();
        header.add(badge(complete + " / " + LESSONS.size() + " complete", GREEN), BorderLayout.EAST);
        line(root, header);
        gap(root, 12);
        for (Lesson lesson : LESSONS) {
            boolean done = app.state.completedLessons.contains(lesson.id());
            JPanel card = new Surface(new BorderLayout(12, 0), SURFACE, LINE, 13);
            pad(card, 13, 14, 13, 14);
            JPanel lessonBody = stack();
            JPanel heading = row(8);
            heading.add(glyph(done ? "check" : "book", done ? GREEN : ACCENT, 17));
            heading.add(text(lesson.title(), 17, true, TEXT));
            line(lessonBody, heading);
            gap(lessonBody, 6);
            line(lessonBody, wrapped(lesson.detail(), MUTED, false));
            gap(lessonBody, 5);
            line(lessonBody, wrapped(lesson.practice(), TEXT, true));
            gap(lessonBody, 7);
            JPanel buttons = row(6);
            buttons.add(button("Open " + lesson.action(), "arrow", () -> {
                if (lesson.action().equals("Settings")) BoardsUi.settings(app);
                else app.openSection(lesson.action());
            }).ghost());
            buttons.add(button(done ? "Mark incomplete" : "Mark complete", done ? "close" : "check", () -> {
                if (done) app.state.completedLessons.remove(lesson.id());
                else app.state.completedLessons.add(lesson.id());
                app.changed();
            }).ghost().style(null, done ? MUTED : GREEN));
            line(lessonBody, buttons);
            card.add(lessonBody, BorderLayout.CENTER);
            line(root, card);
            gap(root, 8);
        }
        JPanel shortcuts = row(8);
        shortcuts.add(badge("Ctrl+K  Search", ACCENT));
        shortcuts.add(badge("Ctrl+N  New card", ACCENT));
        shortcuts.add(badge("F1  Tutorial", ACCENT));
        line(root, shortcuts);
        JPanel view = panel(new BorderLayout(), BASE);
        view.add(root, BorderLayout.NORTH);
        app.currentScroll = scroll(view);
        app.currentScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return app.currentScroll;
    }

    private static JTextArea wrapped(String value, Color tint, boolean bold) {
        JTextArea paragraph = area(value, 0, 1);
        paragraph.setBackground(SURFACE);
        paragraph.setForeground(tint);
        paragraph.setFont(font(14, bold));
        paragraph.setEditable(false);
        paragraph.setFocusable(false);
        pad(paragraph, 1, 0, 1, 0);
        return paragraph;
    }

}
