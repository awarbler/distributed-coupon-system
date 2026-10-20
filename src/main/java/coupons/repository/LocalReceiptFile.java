package coupons.repository;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Versioned local checkpoint, locked to one running CLI and replaced atomically on save. */
final class LocalReceiptFile implements AutoCloseable {
    private static final String VERSION = "coupon-receipts-v1";
    final Path path;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean closed;

    LocalReceiptFile(Path target) throws IOException {
        path = target.toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        lockChannel = FileChannel.open(path.resolveSibling(path.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (RuntimeException | IOException failure) {
            lockChannel.close();
            throw new IOException("Receipt file is already in use: " + path, failure);
        }
        if (acquired == null) {
            lockChannel.close();
            throw new IOException("Receipt file is already in use: " + path);
        }
        lock = acquired;
    }

    InMemoryStore.State load() throws IOException { return readSnapshot(path); }

    /** Read-only decoder for explicitly selected copies; creates no lock or directory. */
    static InMemoryStore.State readSnapshot(Path path) throws IOException { return readSnapshot(path, null); }

    /** Decode the exact bytes inventoried by migration, avoiding a second source-file read. */
    static InMemoryStore.State readSnapshot(Path path, byte[] capturedBytes) throws IOException {
        if (capturedBytes == null && !Files.exists(path)) return new InMemoryStore().state;
        try (var input = new ObjectInputStream(capturedBytes == null ? Files.newInputStream(path) : new java.io.ByteArrayInputStream(capturedBytes))) {
            input.setObjectInputFilter(info -> {
                if (info.depth() > 100 || info.references() > 2_000_000 || info.arrayLength() > 100_000_000) {
                    return ObjectInputFilter.Status.REJECTED;
                }
                Class<?> type = info.serialClass();
                if (type == null) return ObjectInputFilter.Status.UNDECIDED;
                while (type.isArray()) type = type.getComponentType();
                String name = type.getName();
                return type.isPrimitive() || name.startsWith("coupons.") || name.startsWith("java.lang.")
                        || name.startsWith("java.util.") || name.startsWith("java.math.") || name.startsWith("java.time.")
                        ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
            });
            if (!VERSION.equals(input.readUTF())) throw new IOException("Unsupported receipt file version.");
            Object stored = input.readObject();
            if (!(stored instanceof InMemoryStore.State state)) throw new IOException("Invalid receipt file contents.");
            return state;
        } catch (ClassNotFoundException | RuntimeException failure) {
            throw new IOException("Cannot read saved receipts; original file left unchanged: " + path, failure);
        }
    }

    void save(InMemoryStore.State state) throws IOException {
        if (closed) throw new IOException("Receipt storage is closed.");
        Path temporary = Files.createTempFile(path.getParent(), "receipts-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE);
                    var output = new ObjectOutputStream(Channels.newOutputStream(channel))) {
                output.writeUTF(VERSION);
                output.writeObject(state);
                output.flush();
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override public void close() throws IOException {
        if (!closed) {
            closed = true;
            try { lock.release(); } finally { lockChannel.close(); }
        }
    }
}
