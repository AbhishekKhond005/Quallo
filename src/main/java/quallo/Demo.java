package quallo;

import java.time.LocalDate;
import static quallo.Model.*;

final class Demo {
    static State create() {
        State state = new State();
        Workspace studio = new Workspace("Indie Studio");
        state.workspaces.add(studio);

        Board board = new Board("Project Nightfall");
        board.description = "A sci-fi exploration game · Vertical slice production";
        board.cover = SAMPLE_COVER;
        board.favorite = true;
        board.people.addAll(java.util.List.of("Alex Kim", "Maya Rao", "Sam Chen", "Jules Ortiz", "Me"));
        board.labels.put("Design", COLORS.get(0));
        board.labels.put("Engineering", COLORS.get(3));
        board.labels.put("Art", COLORS.get(4));
        board.labels.put("Audio", COLORS.get(5));
        board.labels.put("QA", COLORS.get(2));
        board.fields.add("Build / version");
        board.fields.add("Platform");
        studio.boards.add(board);
        state.selectedBoard = board.id;

        Lane backlog = lane(board, "Backlog", "#9BA6BA", false);
        Lane doing = lane(board, "In Progress", COLORS.get(0), false);
        Lane review = lane(board, "Playtest & QA", COLORS.get(1), false);
        Lane done = lane(board, "Done", COLORS.get(2), true);
        doing.limit = 4;

        Card greybox = card(board, backlog, "Greybox the research outpost", "Design", "Alex Kim",
            "Build the first playable route from the ridge to the outpost.", 9, "Task", "Medium", 0, 3);
        card(board, backlog, "Design the inventory HUD", "Design", "Maya Rao",
            "Explore an interaction layout for pickups and equipment.", 13, "Feature", "None", 0, 0);
        card(board, backlog, "Record environmental audio", "Audio", "Jules Ortiz",
            "Capture wind, ambience, and interior tones for the station.", 17, "Asset", "Low", 0, 0);

        Card movement = card(board, doing, "Tune player movement", "Engineering", "Sam Chen",
            "Refine jump timing and acceleration for rocky terrain.", 2, "Feature", "High", 2, 4);
        movement.dependencies.add(greybox.id);
        card(board, doing, "Paint the outpost kit", "Art", "Maya Rao",
            "Set surface materials and color accents for modular pieces.", 5, "Asset", "None", 1, 3);
        card(board, doing, "Implement checkpoint saves", "Engineering", "Sam Chen",
            "Persist character state between checkpoints.", 7, "Feature", "Medium", 0, 0);

        Card playtest = card(board, review, "Run the first corridor playtest", "QA", "Jules Ortiz",
            "Observe route clarity and collect feedback on traversal.", 3, "Playtest", "None", 2, 3);
        playtest.fields.put("Build / version", "0.1.0");
        card(board, review, "Fix camera clipping on slopes", "Engineering", "Sam Chen",
            "Reproduce the clipping near the east ridge before retesting.", 4, "Bug", "High", 0, 0);

        card(board, done, "Lock the core gameplay loop", "Design", "Alex Kim",
            "Confirm the explore, scan, and extract loop for the vertical slice.", -5, "Milestone", "None", 3, 3);
        card(board, done, "Set up the project repository", "Engineering", "Sam Chen",
            "Create the initial project layout and source control conventions.", -4, "Task", "None", 0, 0);
        card(board, done, "Collect alien biome references", "Art", "Maya Rao",
            "Curate lighting, terrain, and material references for the art team.", -3, "Asset", "None", 0, 0);

        Board assets = newBoard("Art & Assets", true);
        assets.color = COLORS.get(4);
        assets.description = "Concept art, asset production, and art reviews";
        studio.boards.add(assets);
        Board bugs = newBoard("Bug Tracker", true);
        bugs.color = COLORS.get(1);
        bugs.description = "Reproduction steps, triage, and verified fixes";
        studio.boards.add(bugs);
        return state;
    }

    private static Lane lane(Board board, String name, String color, boolean done) {
        Lane lane = new Lane(name, color);
        lane.done = done;
        board.lanes.add(lane);
        return lane;
    }

    private static Card card(Board board, Lane lane, String title, String label, String assignee,
                             String description, int days, String type, String priority, int checked, int total) {
        Card card = addCard(board, lane, title, "Quallo");
        card.labels.add(label);
        card.assignees.add(assignee);
        card.description = description;
        card.due = LocalDate.now().plusDays(days);
        card.start = card.due.minusDays(4);
        card.type = type;
        card.priority = priority;
        card.milestone = "Vertical slice";
        card.estimate = 4;
        if (total > 0) {
            Checklist list = new Checklist("Acceptance criteria");
            String[] steps = {"Prepare a test scene", "Check the workflow in a fresh build",
                "Capture notes and follow-up tasks", "Verify against the vertical slice brief"};
            for (int i = 0; i < total; i++) {
                Item item = new Item(steps[i]);
                item.done = i < checked;
                list.items.add(item);
            }
            card.checklists.add(list);
        }
        return card;
    }
}
