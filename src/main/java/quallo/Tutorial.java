package quallo;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import static quallo.Model.*;
import static quallo.Theme.*;

final class Tutorial {
    private record Lesson(String id, String title, String detail, String practice, String action) {}

    private static final List<Lesson> LESSONS = List.of(
        new Lesson("scope", "Define your vertical slice",
            "In Projects, create a board using Game production. Use its description to record the player experience you want to validate and the build you plan to deliver.",
            "On Project Nightfall, the goal is a playable route from the ridge to the outpost. Add a Milestone card for your own playable build and give it a target date.", "Open sample board"),
        new Lesson("plan", "Make work small enough to finish",
            "Write one outcome on each card. Label it by discipline, choose a type, assign someone, and break the work into checklist items with observable acceptance criteria.",
            "Open Greybox the research outpost and inspect its checklist. Add a new design task in Backlog with one step you can verify.", "Open sample board"),
        new Lesson("flow", "Manage the work in progress",
            "Drag cards between lists as their status changes. Give In Progress a WIP limit to make overload visible. Use a dependency when a task genuinely needs another task to finish first.",
            "Drag a sample card into In Progress. Open Tune player movement and find its dependency. Use Filters → Blocked to see work waiting on another card.", "Open sample board"),
        new Lesson("test", "Run a playtest and triage the bugs",
            "Send a card to Playtest & QA when a build is ready. Record the build and platform in custom fields. For defects, capture steps to reproduce, expected behavior, observed behavior, and severity.",
            "Open Run the first corridor playtest and add a comment with a finding. Create a Bug card and attach a copy of a test note or screenshot.", "Open sample board"),
        new Lesson("review", "Review production and deadlines",
            "Use Calendar for due dates, Timeline for start and due dates, and Overview for list progress, milestones, and blocked work. My work collects your assignments and watched cards.",
            "Open Overview and review the vertical slice. Pick a card, turn on Watch in My work, then open My work to find it.", "Open overview"),
        new Lesson("handoff", "Reuse your process and protect your work",
            "Save a bug report card as a template from Actions. Set an automation rule to label cards when they enter Playtest & QA. Export a workspace backup from Settings to include attached files.",
            "Save a card template, create a task from it, then export a backup to a folder outside Quallo’s data directory.", "Open settings")
    );

    static JComponent build(StudioWindow app) {
        JPanel root = stack();
        pad(root, 25, 28, 25, 28);
        JPanel header = new Surface(new BorderLayout(20, 0), SURFACE, LINE, 19);
        pad(header, 17, 17, 17, 17);
        header.add(Views.title("Project guide", "Plan, build, and playtest a game with Quallo."), BorderLayout.CENTER);
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
            buttons.add(button(lesson.action(), "arrow", () -> openExample(app, lesson)).ghost());
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
        shortcuts.add(badge("F1  Project guide", ACCENT));
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

    private static void openExample(StudioWindow app, Lesson lesson) {
        if (lesson.action().equals("Open settings")) { BoardsUi.settings(app); return; }
        Board sample = app.state.workspaces.stream().flatMap(space -> space.boards.stream())
            .filter(board -> !board.archived && !board.template && board.cover.equals(SAMPLE_COVER)).findFirst().orElse(null);
        if (sample == null) sample = app.state.firstBoard();
        if (sample == null) { BoardsUi.createBoard(app, null); return; }
        app.chooseBoard(sample);
        if (lesson.action().equals("Open overview")) { app.view = "Overview"; app.render(); }
    }
}
