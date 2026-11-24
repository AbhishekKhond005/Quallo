package quallo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import static quallo.Model.*;

final class WorkspaceXml {
    private WorkspaceXml() {}

    static void write(State state, OutputStream output) throws IOException {
        try {
            XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
            xml.writeStartDocument("UTF-8", "1.0");
            element(xml, "quallo", "version", 1, "author", state.author, "selected", state.selectedBoard);
            for (String id : state.completedLessons) entry(xml, "lesson", "id", id);
            for (Workspace space : state.workspaces) {
                element(xml, "workspace", "id", space.id, "name", space.name);
                for (Board board : space.boards) writeBoard(xml, board);
                xml.writeEndElement();
            }
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.flush();
        } catch (XMLStreamException error) {
            throw new IOException("Could not write the workspace", error);
        }
    }

    private static void writeBoard(XMLStreamWriter xml, Board board) throws XMLStreamException {
        element(xml, "board", "id", board.id, "name", board.name, "cover", board.cover, "color", board.color,
            "favorite", board.favorite, "archived", board.archived, "template", board.template);
        content(xml, "description", board.description);
        for (var label : board.labels.entrySet()) entry(xml, "label", "name", label.getKey(), "color", label.getValue());
        for (String person : board.people) entry(xml, "person", "name", person);
        for (String field : board.fields) entry(xml, "field", "name", field);
        for (Rule rule : board.rules) entry(xml, "rule", "lane", rule.laneId(), "action", rule.action(), "value", rule.value());
        for (Lane lane : board.lanes) {
            element(xml, "lane", "id", lane.id, "name", lane.name, "color", lane.color,
                "archived", lane.archived, "done", lane.done, "limit", lane.limit);
            for (Card card : lane.cards) writeCard(xml, card);
            xml.writeEndElement();
        }
        xml.writeEndElement();
    }

    private static void writeCard(XMLStreamWriter xml, Card card) throws XMLStreamException {
        element(xml, "card", "id", card.id, "title", card.title, "type", card.type, "priority", card.priority,
            "milestone", card.milestone, "start", card.start, "due", card.due, "estimate", card.estimate,
            "archived", card.archived, "complete", card.complete, "template", card.template, "watching", card.watching);
        content(xml, "description", card.description);
        for (String label : card.labels) entry(xml, "label", "name", label);
        for (String person : card.assignees) entry(xml, "assignee", "name", person);
        for (String parent : card.dependencies) entry(xml, "dependency", "id", parent);
        for (var field : card.fields.entrySet()) {
            element(xml, "field", "name", field.getKey());
            xml.writeCharacters(xmlText(field.getValue()));
            xml.writeEndElement();
        }
        for (Checklist list : card.checklists) {
            element(xml, "checklist", "title", list.title);
            for (Item item : list.items) entry(xml, "item", "title", item.title, "owner", item.owner, "due", item.due, "done", item.done);
            xml.writeEndElement();
        }
        for (Attachment file : card.attachments) entry(xml, "attachment", "name", file.name(), "key", file.key(), "size", file.size());
        for (Comment comment : card.comments) writeEntry(xml, "comment", comment.author(), comment.text(), comment.date());
        for (Event event : card.events) writeEntry(xml, "event", event.author(), event.text(), event.date());
        xml.writeEndElement();
    }

    private static void writeEntry(XMLStreamWriter xml, String tag, String author, String text, Instant date) throws XMLStreamException {
        element(xml, tag, "author", author, "date", date);
        xml.writeCharacters(xmlText(text));
        xml.writeEndElement();
    }

    private static void content(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(xmlText(value));
        xml.writeEndElement();
    }

    private static void element(XMLStreamWriter xml, String tag, Object... attributes) throws XMLStreamException {
        xml.writeStartElement(tag);
        attributes(xml, attributes);
    }

    private static void entry(XMLStreamWriter xml, String tag, Object... attributes) throws XMLStreamException {
        xml.writeEmptyElement(tag);
        attributes(xml, attributes);
    }

    private static void attributes(XMLStreamWriter xml, Object[] attributes) throws XMLStreamException {
        for (int i = 0; i < attributes.length; i += 2) {
            if (attributes[i + 1] != null) xml.writeAttribute((String) attributes[i], xmlText(attributes[i + 1].toString()));
        }
    }

    private static String xmlText(String text) {
        StringBuilder clean = new StringBuilder(text.length());
        text.codePoints().filter(code -> code == 9 || code == 10 || code == 13 ||
            code >= 32 && code <= 0xD7FF || code >= 0xE000 && code <= 0xFFFD || code >= 0x10000)
            .forEach(clean::appendCodePoint);
        return clean.toString();
    }

    static State read(InputStream input) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var parser = factory.newDocumentBuilder();
            parser.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            Element root = parser.parse(input).getDocumentElement();
            if (!root.getTagName().equals("quallo") || !root.getAttribute("version").equals("1")) {
                throw new IOException("This workspace format is unsupported");
            }
            State state = new State();
            state.author = value(root, "author", "Me");
            state.selectedBoard = root.getAttribute("selected");
            for (Element lesson : children(root, "lesson")) state.completedLessons.add(lesson.getAttribute("id"));
            for (Element element : children(root, "workspace")) {
                Workspace space = new Workspace(element.getAttribute("name"));
                space.id = value(element, "id", space.id);
                for (Element board : children(element, "board")) space.boards.add(readBoard(board));
                state.workspaces.add(space);
            }
            return state;
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Could not read the workspace", error);
        }
    }

    private static Board readBoard(Element node) {
        Board board = new Board(node.getAttribute("name"));
        board.id = value(node, "id", board.id);
        board.cover = node.getAttribute("cover");
        board.color = value(node, "color", board.color);
        board.favorite = flag(node, "favorite");
        board.archived = flag(node, "archived");
        board.template = flag(node, "template");
        board.description = childText(node, "description");
        for (Element label : children(node, "label")) board.labels.put(label.getAttribute("name"), label.getAttribute("color"));
        for (Element person : children(node, "person")) board.people.add(person.getAttribute("name"));
        for (Element field : children(node, "field")) board.fields.add(field.getAttribute("name"));
        for (Element rule : children(node, "rule")) {
            board.rules.add(new Rule(rule.getAttribute("lane"), rule.getAttribute("action"), rule.getAttribute("value")));
        }
        for (Element element : children(node, "lane")) {
            Lane lane = new Lane(element.getAttribute("name"), value(element, "color", COLORS.getFirst()));
            lane.id = value(element, "id", lane.id);
            lane.archived = flag(element, "archived");
            lane.done = flag(element, "done");
            lane.limit = number(element, "limit");
            for (Element card : children(element, "card")) lane.cards.add(readCard(card));
            board.lanes.add(lane);
        }
        return board;
    }

    private static Card readCard(Element node) {
        Card card = new Card(node.getAttribute("title"));
        card.id = value(node, "id", card.id);
        card.type = value(node, "type", card.type);
        card.priority = value(node, "priority", card.priority);
        card.milestone = node.getAttribute("milestone");
        card.description = childText(node, "description");
        card.start = date(node, "start");
        card.due = date(node, "due");
        card.estimate = number(node, "estimate");
        card.complete = flag(node, "complete");
        card.archived = flag(node, "archived");
        card.template = flag(node, "template");
        card.watching = flag(node, "watching");
        for (Element label : children(node, "label")) card.labels.add(label.getAttribute("name"));
        for (Element assignee : children(node, "assignee")) card.assignees.add(assignee.getAttribute("name"));
        for (Element dependency : children(node, "dependency")) card.dependencies.add(dependency.getAttribute("id"));
        for (Element field : children(node, "field")) card.fields.put(field.getAttribute("name"), field.getTextContent());
        for (Element checklist : children(node, "checklist")) {
            Checklist list = new Checklist(checklist.getAttribute("title"));
            for (Element item : children(checklist, "item")) {
                Item entry = new Item(item.getAttribute("title"));
                entry.owner = item.getAttribute("owner");
                entry.due = date(item, "due");
                entry.done = flag(item, "done");
                list.items.add(entry);
            }
            card.checklists.add(list);
        }
        for (Element file : children(node, "attachment")) {
            card.attachments.add(new Attachment(file.getAttribute("name"), file.getAttribute("key"), Long.parseLong(file.getAttribute("size"))));
        }
        for (Element comment : children(node, "comment")) {
            card.comments.add(new Comment(comment.getAttribute("author"), comment.getTextContent(), Instant.parse(comment.getAttribute("date"))));
        }
        for (Element event : children(node, "event")) {
            card.events.add(new Event(event.getAttribute("author"), event.getTextContent(), Instant.parse(event.getAttribute("date"))));
        }
        return card;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child && child.getTagName().equals(name)) result.add(child);
        }
        return result;
    }

    private static String childText(Element parent, String name) {
        return children(parent, name).stream().findFirst().map(Element::getTextContent).orElse("");
    }

    private static String value(Element element, String name, String fallback) {
        return element.hasAttribute(name) ? element.getAttribute(name) : fallback;
    }

    private static boolean flag(Element element, String name) {
        return Boolean.parseBoolean(element.getAttribute(name));
    }

    private static int number(Element element, String name) {
        return Integer.parseInt(value(element, name, "0"));
    }

    private static LocalDate date(Element element, String name) {
        return element.hasAttribute(name) ? LocalDate.parse(element.getAttribute(name)) : null;
    }
}
