package quallo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class Model {
    static final List<String> TYPES = List.of("Task", "Feature", "Bug", "Asset", "Playtest", "Milestone");
    static final List<String> PRIORITIES = List.of("None", "Low", "Medium", "High", "Urgent");
    static final List<String> COLORS = List.of("#BBA3FF", "#FFB17C", "#7DDBC0", "#78ADFF", "#F695B3", "#E8CD7A", "#9BA6BA");
    static final String SAMPLE_COVER = "nightfall";

    static String id() {
        return UUID.randomUUID().toString();
    }

    static final class State {
        String author = "Me";
        String selectedBoard = "";
        final List<Workspace> workspaces = new ArrayList<>();
        Board inbox = new Board("Inbox");
        final Set<String> completedLessons = new LinkedHashSet<>();

        State() {
            inbox.lanes.add(new Lane("Captured", COLORS.getFirst()));
        }

        Board board(String id) {
            return workspaces.stream().flatMap(space -> space.boards.stream())
                .filter(board -> board.id.equals(id)).findFirst().orElse(null);
        }

        Workspace workspace(Board board) {
            return workspaces.stream().filter(space -> space.boards.contains(board)).findFirst().orElse(null);
        }

        Board firstBoard() {
            return workspaces.stream().flatMap(space -> space.boards.stream())
                .filter(board -> !board.archived && !board.template).findFirst().orElse(null);
        }
    }

    static final class Workspace {
        String id = id();
        String name;
        final List<Board> boards = new ArrayList<>();

        Workspace(String name) {
            this.name = name;
        }
    }

    static final class Board {
        String id = id();
        String name;
        String description = "";
        String cover = "";
        String color = COLORS.getFirst();
        boolean favorite, archived, template;
        final List<Lane> lanes = new ArrayList<>();
        final Map<String, String> labels = new LinkedHashMap<>();
        final Set<String> people = new LinkedHashSet<>();
        final Set<String> fields = new LinkedHashSet<>();
        final List<Rule> rules = new ArrayList<>();

        Board(String name) {
            this.name = name;
        }

        Lane lane(String id) {
            return lanes.stream().filter(lane -> lane.id.equals(id)).findFirst().orElse(null);
        }

        Lane laneOf(Card card) {
            return lanes.stream().filter(lane -> lane.cards.contains(card)).findFirst().orElseThrow();
        }

        Card card(String id) {
            return lanes.stream().flatMap(lane -> lane.cards.stream())
                .filter(card -> card.id.equals(id)).findFirst().orElse(null);
        }

        List<Card> activeCards() {
            return lanes.stream().filter(lane -> !lane.archived).flatMap(lane -> lane.cards.stream())
                .filter(card -> !card.archived && !card.template).toList();
        }

        Lane firstLane() {
            return lanes.stream().filter(lane -> !lane.archived).findFirst().orElse(null);
        }
    }

    static final class Lane {
        String id = id();
        String name;
        String color;
        boolean archived, done;
        int limit;
        final List<Card> cards = new ArrayList<>();

        Lane(String name, String color) {
            this.name = name;
            this.color = color;
        }

        long openCount() {
            return cards.stream().filter(card -> !card.archived && !card.template && !card.complete).count();
        }
    }

    static final class Card {
        String id = id();
        String title;
        String description = "";
        String type = "Task";
        String priority = "None";
        String milestone = "";
        LocalDate start, due;
        boolean complete, archived, template, watching;
        int estimate;
        final Set<String> labels = new LinkedHashSet<>();
        final Set<String> assignees = new LinkedHashSet<>();
        final Set<String> dependencies = new LinkedHashSet<>();
        final Map<String, String> fields = new LinkedHashMap<>();
        final List<Checklist> checklists = new ArrayList<>();
        final List<Attachment> attachments = new ArrayList<>();
        final List<Comment> comments = new ArrayList<>();
        final List<Event> events = new ArrayList<>();

        Card(String title) {
            this.title = title;
        }

        long checklistDone() {
            return checklists.stream().flatMap(list -> list.items.stream()).filter(item -> item.done).count();
        }

        long checklistTotal() {
            return checklists.stream().mapToLong(list -> list.items.size()).sum();
        }

        boolean overdue(LocalDate today) {
            return !complete && due != null && due.isBefore(today);
        }

        void log(String author, String text) {
            events.addFirst(new Event(author, text, Instant.now()));
            if (events.size() > 100) events.removeLast();
        }
    }

    static final class Checklist {
        String title;
        final List<Item> items = new ArrayList<>();

        Checklist(String title) {
            this.title = title;
        }
    }

    static final class Item {
        String title;
        String owner = "";
        LocalDate due;
        boolean done;

        Item(String title) {
            this.title = title;
        }
    }

    record Attachment(String name, String key, long size) {}
    record Comment(String author, String text, Instant date) {}
    record Event(String author, String text, Instant date) {}
    record Rule(String laneId, String action, String value) {}

    static Board newBoard(String name, boolean game) {
        Board board = new Board(name);
        board.lanes.add(new Lane("Backlog", "#A3A9B8"));
        board.lanes.add(new Lane("In Progress", "#BBA3FF"));
        if (game) board.lanes.add(new Lane("Playtest & QA", "#FFB17C"));
        Lane done = new Lane("Done", "#7DDBC0");
        done.done = true;
        board.lanes.add(done);
        if (game) {
            board.labels.put("Design", COLORS.get(0));
            board.labels.put("Engineering", COLORS.get(3));
            board.labels.put("Art", COLORS.get(4));
            board.labels.put("Audio", COLORS.get(5));
            board.labels.put("QA", COLORS.get(2));
            board.fields.add("Build / version");
            board.fields.add("Platform");
        }
        return board;
    }

    static Card addCard(Board board, Lane lane, String title, String author) {
        Card card = new Card(title);
        lane.cards.add(card);
        card.complete = lane.done;
        card.log(author, "Created in " + lane.name);
        applyRules(board, lane, card);
        return card;
    }

    static void moveCard(Board from, Board to, Card card, Lane target, Card before, String author) {
        if (card == before) return;
        Lane old = from.laneOf(card);
        old.cards.remove(card);
        int index = before == null ? -1 : target.cards.indexOf(before);
        target.cards.add(index < 0 ? target.cards.size() : index, card);
        if (from != to) {
            from.lanes.forEach(lane -> lane.cards.forEach(other -> other.dependencies.remove(card.id)));
            card.dependencies.clear();
            card.fields.keySet().retainAll(to.fields);
            card.assignees.retainAll(to.people);
            card.checklists.forEach(list -> list.items.forEach(item -> {
                if (!to.people.contains(item.owner)) item.owner = "";
            }));
            card.labels.forEach(label -> to.labels.putIfAbsent(label, from.labels.getOrDefault(label, COLORS.getFirst())));
        }
        if (old != target) {
            if (target.done) card.complete = true;
            else if (old.done) card.complete = false;
            applyRules(to, target, card);
            card.log(author, "Moved from " + old.name + " to " + target.name);
        }
    }

    static void applyRules(Board board, Lane lane, Card card) {
        for (Rule rule : board.rules) {
            if (!rule.laneId().equals(lane.id)) continue;
            switch (rule.action()) {
                case "Complete" -> card.complete = true;
                case "Reopen" -> card.complete = false;
                case "Add label" -> {
                    if (board.labels.containsKey(rule.value())) card.labels.add(rule.value());
                }
                default -> { }
            }
        }
    }

    static Card copyCard(Card original, String title, String author) {
        Card copy = new Card(title);
        copy.description = original.description;
        copy.type = original.type;
        copy.priority = original.priority;
        copy.milestone = original.milestone;
        copy.start = original.start;
        copy.due = original.due;
        copy.estimate = original.estimate;
        copy.labels.addAll(original.labels);
        copy.assignees.addAll(original.assignees);
        copy.fields.putAll(original.fields);
        copy.attachments.addAll(original.attachments);
        for (Checklist list : original.checklists) {
            Checklist checklist = new Checklist(list.title);
            for (Item item : list.items) {
                Item entry = new Item(item.title);
                entry.owner = item.owner;
                entry.due = item.due;
                checklist.items.add(entry);
            }
            copy.checklists.add(checklist);
        }
        copy.log(author, "Copied from " + original.title);
        return copy;
    }

    static Board copyBoard(Board original, String title, String author) {
        Board copy = new Board(title);
        copy.description = original.description;
        copy.cover = original.cover;
        copy.color = original.color;
        copy.labels.putAll(original.labels);
        copy.people.addAll(original.people);
        copy.fields.addAll(original.fields);
        Map<String, String> lanes = new LinkedHashMap<>();
        Map<String, Card> cards = new LinkedHashMap<>();
        for (Lane old : original.lanes) {
            if (old.archived) continue;
            Lane lane = new Lane(old.name, old.color);
            lane.done = old.done;
            lane.limit = old.limit;
            copy.lanes.add(lane);
            lanes.put(old.id, lane.id);
            for (Card card : old.cards) {
                if (card.archived) continue;
                Card newCard = copyCard(card, card.title, author);
                newCard.template = card.template;
                newCard.complete = card.complete;
                for (int i = 0; i < card.checklists.size(); i++) {
                    for (int j = 0; j < card.checklists.get(i).items.size(); j++) {
                        newCard.checklists.get(i).items.get(j).done = card.checklists.get(i).items.get(j).done;
                    }
                }
                lane.cards.add(newCard);
                cards.put(card.id, newCard);
            }
        }
        cards.forEach((id, card) -> original.card(id).dependencies.stream().map(cards::get)
            .filter(java.util.Objects::nonNull).forEach(parent -> card.dependencies.add(parent.id)));
        original.rules.stream().filter(rule -> lanes.containsKey(rule.laneId()))
            .forEach(rule -> copy.rules.add(new Rule(lanes.get(rule.laneId()), rule.action(), rule.value())));
        return copy;
    }

    static boolean createsCycle(Board board, Card from, Card dependency) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(dependency.id);
        for (int i = 0; i < queue.size(); i++) {
            String id = queue.get(i);
            if (id.equals(from.id)) return true;
            Card next = board.card(id);
            if (next != null && seen.add(id)) queue.addAll(next.dependencies);
        }
        return false;
    }
}
