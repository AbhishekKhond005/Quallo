package quallo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import javax.swing.*;
import static quallo.Model.*;

final class WorkFilters {
    String label = "", assignee = "", priority = "", type = "", status = "", milestone = "";

    boolean active() {
        return !(label.isEmpty() && assignee.isEmpty() && priority.isEmpty() && type.isEmpty() && status.isEmpty() && milestone.isEmpty());
    }

    void clear() {
        label = assignee = priority = type = status = milestone = "";
    }

    boolean matches(Board board, Lane lane, Card card, String query) {
        if (!query.isEmpty()) {
            String text = String.join(" ", card.title, card.description, card.type, card.priority, card.milestone,
                lane.name, String.join(" ", card.labels), String.join(" ", card.assignees),
                String.join(" ", card.fields.values())).toLowerCase(Locale.ROOT);
            if (!text.contains(query)) return false;
        }
        if (!label.isEmpty() && !card.labels.contains(label)) return false;
        if (!assignee.isEmpty() && !card.assignees.contains(assignee)) return false;
        if (!priority.isEmpty() && !priority.equals(card.priority)) return false;
        if (!type.isEmpty() && !type.equals(card.type)) return false;
        if (!milestone.isEmpty() && !milestone.equals(card.milestone)) return false;
        return switch (status) {
            case "Open" -> !card.complete;
            case "Complete" -> card.complete;
            case "Overdue" -> card.overdue(LocalDate.now());
            case "Due next 7 days" -> !card.complete && card.due != null && !card.due.isBefore(LocalDate.now())
                && !card.due.isAfter(LocalDate.now().plusDays(7));
            case "Watching" -> card.watching;
            case "Blocked" -> card.dependencies.stream().map(board::card).anyMatch(parent -> parent != null && !parent.complete);
            default -> true;
        };
    }

    void edit(StudioWindow app) {
        JComboBox<String> labels = Forms.optional(app.board.labels.keySet(), label);
        JComboBox<String> people = Forms.optional(app.board.people, assignee);
        JComboBox<String> priorities = Forms.optional(PRIORITIES, priority);
        JComboBox<String> types = Forms.optional(TYPES, type);
        JComboBox<String> statuses = Forms.optional(java.util.List.of("Open", "Complete", "Overdue", "Due next 7 days", "Watching", "Blocked"), status);
        var names = new ArrayList<>(app.board.activeCards().stream().map(card -> card.milestone)
            .filter(name -> !name.isBlank()).distinct().sorted().toList());
        JComboBox<String> milestones = Forms.optional(names, milestone);
        JPanel form = Forms.form("Label", labels, "Assignee", people, "Priority", priorities, "Type", types,
            "Status", statuses, "Milestone", milestones);
        int result = JOptionPane.showOptionDialog(app, form, "Filter tasks", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
            null, new Object[]{"Apply", "Clear all", "Cancel"}, "Apply");
        if (result == 1) clear();
        else if (result == 0) {
            label = Forms.value(labels);
            assignee = Forms.value(people);
            priority = Forms.value(priorities);
            type = Forms.value(types);
            status = Forms.value(statuses);
            milestone = Forms.value(milestones);
        } else return;
        app.visibleLimits.clear();
        app.currentScroll = null;
        app.render();
    }
}
