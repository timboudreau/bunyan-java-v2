package com.mastfrog.bunyan.java.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class FileLogSink extends AbstractLogSink implements ThrowingRunnable {

    @JsonProperty("path")
    private final Path path;
    private FileChannel channel;
    @JsonProperty("dead")
    private boolean dead;
    private boolean warned;

    public FileLogSink(Path path, Supplier<LoggingConfig> config) {
        super(config);
        this.path = path;
    }

    @JsonProperty("open")
    public boolean isOpen() {
        FileChannel ch = channel;
        return ch == null ? false : ch.isOpen();
    }

    @Override
    public String toString() {
        return "FileLogSink(" + path + ")";
    }

    private synchronized FileChannel channel() throws IOException {
        if (channel == null) {
            channel = FileChannel.open(path, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        }
        return channel;
    }

    @Override
    public void push(JSONContext ctx, Map<String, Object> logrecord) {
        if (dead) {
            if (!warned) {
                LoggingLogging.log("Cannot log to dead logger {0} - channel is closed", path);
                warned = true;
            }
            return;
        }
        try {
            FileChannel ch;
            try {
                ch = channel();
            } catch (IOException ioe) {
                // dead
                dead = true;
                throw ioe;
            }
            byte[] b = ctx.writeValueAsBytes(logrecord);
            ByteBuffer buffer = ByteBuffer.allocate(b.length + 1);
            buffer.put(b);
            buffer.put((byte) '\n');
            buffer.flip();
            ch.write(buffer);
        } catch (IOException ex) {
            LoggingLogging.log("Exception generating JSON", ex, true);
        }
    }

    @Override
    public void run() throws Exception {
        FileChannel ch;
        synchronized (this) {
            ch = channel;
            channel = null;
        }
        if (ch != null) {
            ch.close();
        }
    }
}
