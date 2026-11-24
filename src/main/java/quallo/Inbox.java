package quallo;

import java.awt.*;
import javax.swing.*;
import static quallo.Model.*;
import quallo.Theme.Button;
import static quallo.Theme.*;

final class Inbox {
    static JComponent build(StudioWindow app) {
        Board inbox = app.state.inbox;
        Lane lane = inbox.firstLane();
        JPanel body = stack();
        pad(body, 25, 28, 25, 28);
        JPanel header = panel(new BorderLayout(10, 0), BASE);
        header.add(Views.title("Inbox", "Capture an idea or follow-up and file it on a project board when you’re ready."), BorderLayout.CENTER);
        header.add(button("Archive", "archive", () -> BoardsUi.boardArchive(app, inbox)), BorderLayout.EAST);
        line(body, header);
        gap(body, 17);

        JPanel input = new Surface(new BorderLayout(8, 0), SURFACE, LINE, 13);
        pad(input, 10, 10, 10, 10);
        JTextField title = field("", 24);
        title.getAccessibleContext().setAccessibleName("Capture title");
        title.setToolTipText("Enter a task, idea, or playtest note");
        Runnable add = () -> {
            if (title.getText().isBlank()) { title.requestFocusInWindow(); return; }
            addCard(inbox, lane, title.getText().strip(), app.state.author);
            app.changed();
        };
        title.addActionListener(event -> add.run());
        input.add(title, BorderLayout.CENTER);
        input.add(primary("Capture", "plus", add), BorderLayout.EAST);
        line(body, input);
        gap(body, 16);
        for (Card card : inbox.activeCards().reversed()) {
            JPanel row = new Surface(new BorderLayout(8, 0), SURFACE, LINE, 13);
            pad(row, 7, 11, 7, 9);
            Button titleButton = new Button(card.title, card.complete ? "check" : "dot").ghost().left();
            titleButton.setToolTipText(card.title);
            titleButton.addActionListener(event -> app.editCard(inbox, card));
            row.add(titleButton, BorderLayout.CENTER);
            JPanel actions = Theme.row(5);
            actions.add(button("Move to board", "arrow", () -> CardEditor.moveFromInbox(app, card)).ghost());
            actions.add(button("Archive", "archive", () -> {
                card.archived = true;
                card.log(app.state.author, "Archived in Inbox");
                app.changed();
            }).ghost());
            row.add(actions, BorderLayout.EAST);
            line(body, row);
            gap(body, 7);
        }
        if (inbox.activeCards().isEmpty()) line(body, text("Your inbox is empty.", 14, false, MUTED));
        JPanel page = panel(new BorderLayout(), BASE);
        page.add(body, BorderLayout.NORTH);
        app.currentScroll = scroll(page);
        SwingUtilities.invokeLater(title::requestFocusInWindow);
        return app.currentScroll;
    }
}
