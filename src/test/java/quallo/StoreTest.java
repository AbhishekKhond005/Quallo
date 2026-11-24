package quallo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import static quallo.Model.*;

public final class StoreTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("quallo-store-test-");
        try {
            run(root);
            System.out.println("Workspace, dependencies, rules, persistence and backup checks passed.");
        } finally {
            try (var files = Files.walk(root)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    private static void run(Path root) throws IOException {
        Path backup = root.resolve("backup.zip");
        String cardId;
        try (Store store = new Store(root.resolve("original"))) {
            State state = store.load();
            check(state.workspaces.isEmpty() && state.selectedBoard.isEmpty() && state.firstBoard() == null,
                "first launch has no sample workspace, board or selection");
            check(state.inbox.activeCards().isEmpty() && state.completedLessons.isEmpty(), "first launch has no tasks or tutorial progress");
            Workspace workspace = new Workspace("Test studio");
            state.workspaces.add(workspace);
            Board board = new Board("Test game");
            workspace.boards.add(board);
            state.selectedBoard = board.id;
            board.lanes.add(new Lane("Draft", COLORS.getFirst()));
            Lane shipped = new Lane("Shipped", COLORS.get(2));
            shipped.done = true;
            board.lanes.add(shipped);
            board.labels.put("QA", COLORS.get(2));
            board.people.add("Me");
            board.fields.add("Build / version");
            check(state.board(state.selectedBoard) == board, "selected board");
            try (Store duplicate = new Store(store.directory)) {
                throw new AssertionError("Expected single-writer locking for " + duplicate.directory);
            } catch (IOException expected) { }

            Lane backlog = board.lanes.getFirst();
            Lane done = board.lanes.getLast();
            Card first = addCard(board, backlog, "Write debug notes", state.author);
            Card second = addCard(board, backlog, "Fix 🎮 camera & camera < clipping", state.author);
            cardId = second.id;
            second.description = "Reproduce on explicit build\nVerify the fix.";
            second.start = LocalDate.of(2026, 3, 11);
            second.due = second.start.plusDays(5);
            second.watching = true;
            second.assignees.add("Me");
            second.dependencies.add(first.id);
            second.fields.put("Build / version", "0.1.22");
            second.comments.add(new Comment("Me", "A note < & >", Instant.now()));
            Checklist checklist = new Checklist("Acceptance");
            Item step = new Item("Replay the tutorial");
            step.done = true;
            step.owner = "Me";
            step.due = second.due;
            checklist.items.add(step);
            second.checklists.add(checklist);

            check(createsCycle(board, first, second), "cycle is detected");
            check(!createsCycle(board, second, first), "acyclic link is allowed");
            board.rules.add(new Rule(done.id, "Add label", "QA"));
            moveCard(board, board, second, done, null, state.author);
            check(second.complete && second.labels.contains("QA"), "move applies completion and label");
            moveCard(board, board, second, backlog, first, state.author);
            check(!second.complete && backlog.cards.indexOf(second) + 1 == backlog.cards.indexOf(first), "reopen and reorder");

            WorkFilters search = new WorkFilters();
            search.status = "Blocked";
            check(search.matches(board, backlog, second, "0.1.22"), "search data and blocked status");
            check(!search.matches(board, backlog, first, ""), "hide unblocked task");
            search.clear();
            first.type = "Bug";
            first.priority = "High";
            first.milestone = "Closed beta";
            first.due = LocalDate.now().plusDays(2);
            first.watching = true;
            first.labels.add("QA");
            first.assignees.add("Me");
            search.type = "Bug";
            search.priority = "High";
            search.milestone = "Closed beta";
            search.label = "QA";
            search.assignee = "Me";
            for (String status : new String[]{"Open", "Due next 7 days", "Watching"}) {
                search.status = status;
                check(search.matches(board, backlog, first, "") && !search.matches(board, backlog, second, ""),
                    "combined filters with " + status);
            }
            search.status = "Complete";
            check(!search.matches(board, backlog, first, ""), "open card excluded from complete filter");
            search.clear();
            first.due = LocalDate.now().minusDays(1);
            search.status = "Overdue";
            check(search.matches(board, backlog, first, ""), "overdue filter");
            Board copy = copyBoard(board, "Backup template", state.author);
            Card copied = copy.activeCards().stream().filter(card -> card.title.equals(second.title)).findFirst().orElseThrow();
            check(copied.dependencies.size() == 1 && !copied.dependencies.contains(first.id), "copy rewires dependency");
            check(copy.card(copied.dependencies.iterator().next()) != null && copied.checklistDone() == 1, "copy preserves task structure");

            Path art = Files.writeString(root.resolve("playtest notes.txt"), "Notes for the QA team");
            second.attachments.add(store.attach(art));
            check(second.attachments.getFirst().equals(store.attach(art)), "attachment deduplication");
            Card captured = addCard(state.inbox, state.inbox.firstLane(), "Capture playtest notes", state.author);
            captured.attachments.add(store.attach(art));
            state.completedLessons.add("scope");
            store.save(state);
            store.backup(state, backup);
        }
        try (Store store = new Store(root.resolve("original"))) {
            State restored = store.load();
            Card card = restored.firstBoard().card(cardId);
            check(card.title.startsWith("Fix 🎮") && card.description.contains("\n"), "unicode and multiline text");
            check(card.comments.getFirst().text().equals("A note < & >") && card.checklistDone() == 1, "comments and checklists");
            check(restored.completedLessons.contains("scope") && card.watching, "tutorial and watched cards");
            check(card.start.plusDays(5).equals(card.due) && card.assignees.contains("Me"), "dates and assignees");
        }
        try (Store store = new Store(root.resolve("restored"))) {
            State restored = store.restore(backup);
            Attachment file = restored.firstBoard().card(cardId).attachments.getFirst();
            check(Files.readString(store.attachment(file)).equals("Notes for the QA team"), "backup restores copied file");
            check(store.load().firstBoard().card(cardId) != null, "backup remains on disk");
            check(restored.inbox.activeCards().getFirst().attachments.getFirst().equals(file), "backup includes Inbox");
            Path incomplete = root.resolve("incomplete.zip");
            try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(incomplete))) {
                zip.putNextEntry(new java.util.zip.ZipEntry("workspace.xml"));
                WorkspaceXml.write(restored, zip);
                zip.closeEntry();
            }
            try {
                store.restore(incomplete);
                throw new AssertionError("Backup with a missing attachment should fail");
            } catch (IOException expected) {
                check(store.load().firstBoard().card(cardId) != null && Files.isRegularFile(store.attachment(file)),
                    "failed restore preserves the existing workspace and attachments");
            }
        }
    }

    static void check(boolean expression, String description) {
        if (!expression) throw new AssertionError(description);
    }
}
