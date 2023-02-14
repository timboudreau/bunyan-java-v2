/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.bunyan.java.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastfrog.function.throwing.ThrowingRunnable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
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
    
    Path path() {
        return path;
    }
    
    void close() throws IOException {
        channel.force(true);
        channel.close();
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

    synchronized long size() throws IOException {
        FileChannel ch = channel;
        if (ch != null) {
            try {
                return ch.size();
            } catch (IOException ioe) {
                try {
                    return Files.size(path);
                } catch (IOException ioe2) {
                    ioe2.addSuppressed(ioe);
                    LoggingLogging.log(ioe2);
                }
            }
        }
        return 0L;
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
