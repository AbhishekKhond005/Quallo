package quallo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import static quallo.Model.*;

final class Store implements AutoCloseable {
    private static final String MANIFEST = "workspace.xml";
    private static final long MAX_ATTACHMENT = 250L * 1024 * 1024;
    final Path directory;
    private final Path manifest, attachments;
    private final FileChannel channel;
    private final FileLock lock;

    Store(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath();
        Files.createDirectories(this.directory);
        manifest = this.directory.resolve(MANIFEST);
        attachments = this.directory.resolve("attachments");
        Files.createDirectories(attachments);
        channel = FileChannel.open(this.directory.resolve("workspace.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = channel.tryLock();
            if (lock == null) throw new IOException("Quallo already has this workspace open");
        } catch (IOException | OverlappingFileLockException error) {
            channel.close();
            throw new IOException("Unable to open this workspace. Close any other Quallo window and try again.", error);
        }
    }

    static Path defaultDirectory() {
        String local = System.getenv("LOCALAPPDATA");
        if (System.getProperty("os.name").startsWith("Windows") && local != null && !local.isBlank()) {
            return Path.of(local, "Quallo");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank() && Path.of(xdg).isAbsolute()) return Path.of(xdg, "quallo");
        return Path.of(System.getProperty("user.home"), ".local", "share", "quallo");
    }

    State load() throws IOException {
        if (Files.notExists(manifest)) return new State();
        try (InputStream input = Files.newInputStream(manifest)) {
            return WorkspaceXml.read(input);
        }
    }

    void save(State state) throws IOException {
        Path temporary = Files.createTempFile(directory, "workspace-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                WorkspaceXml.write(state, output);
            }
            try (FileChannel data = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                data.force(true);
            }
            replace(temporary, manifest);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Attachment attach(Path source) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            return putAttachment(input, source.getFileName().toString(), null);
        }
    }

    Path attachment(Attachment file) throws IOException {
        if (!file.key().matches("[0-9a-f]{64}")) throw new IOException("Invalid attachment reference");
        return attachments.resolve(file.key());
    }

    private Attachment putAttachment(InputStream source, String name, Attachment expected) throws IOException {
        MessageDigest digest = sha256();
        Path temporary = Files.createTempFile(attachments, "attachment-", ".tmp");
        long size = 0;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                DigestInputStream stream = new DigestInputStream(source, digest);
                byte[] buffer = new byte[64 * 1024];
                for (int count; (count = stream.read(buffer)) != -1;) {
                    size += count;
                    if (size > MAX_ATTACHMENT) throw new IOException("Attachments can be up to 250 MiB each");
                    output.write(buffer, 0, count);
                }
            }
            Attachment file = new Attachment(name, HexFormat.of().formatHex(digest.digest()), size);
            if (expected != null && (!expected.key().equals(file.key()) || expected.size() != size)) {
                throw new IOException("Invalid data for attachment " + name);
            }
            Path target = attachment(file);
            if (!Files.exists(target) || expected != null) replace(temporary, target);
            return file;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void backup(State state, Path target) throws IOException {
        if (target.toAbsolutePath().normalize().startsWith(directory.normalize())) {
            throw new IOException("Choose a backup location outside the Quallo data folder");
        }
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), ".quallo-", ".zip");
        try {
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
                zip.putNextEntry(new ZipEntry(MANIFEST));
                WorkspaceXml.write(state, zip);
                zip.closeEntry();
                for (Attachment file : files(state).values()) {
                    zip.putNextEntry(new ZipEntry("attachments/" + file.key()));
                    Files.copy(attachment(file), zip);
                    zip.closeEntry();
                }
            }
            replace(temp, target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    State restore(Path source) throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST);
            if (manifestEntry == null || manifestEntry.getSize() < 0 || manifestEntry.getSize() > 64L * 1024 * 1024) {
                throw new IOException("The backup workspace is missing or too large");
            }
            State state;
            try (InputStream input = zip.getInputStream(manifestEntry)) {
                // The explicit read bound also applies when ZIP metadata reports an incorrect size.
                byte[] bytes = input.readNBytes(64 * 1024 * 1024 + 1);
                if (bytes.length > 64 * 1024 * 1024) throw new IOException("The backup workspace is too large");
                state = WorkspaceXml.read(new java.io.ByteArrayInputStream(bytes));
            }
            for (Attachment file : files(state).values()) {
                attachment(file);
                ZipEntry entry = zip.getEntry("attachments/" + file.key());
                if (entry == null || entry.getSize() != file.size() || file.size() > MAX_ATTACHMENT) {
                    throw new IOException("Missing or invalid attachment " + file.name());
                }
                try (InputStream input = zip.getInputStream(entry)) {
                    putAttachment(input, file.name(), file);
                }
            }
            save(state);
            return state;
        }
    }

    private static Map<String, Attachment> files(State state) {
        Map<String, Attachment> files = new LinkedHashMap<>();
        java.util.stream.Stream.concat(java.util.stream.Stream.of(state.inbox),
            state.workspaces.stream().flatMap(space -> space.boards.stream())).forEach(board ->
                board.lanes.forEach(lane -> lane.cards.forEach(card ->
                    card.attachments.forEach(file -> files.putIfAbsent(file.key(), file)))));
        return files;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void close() throws IOException {
        try (channel) {
            lock.release();
        }
    }
}
