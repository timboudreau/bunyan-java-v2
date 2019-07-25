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
package com.mastfrog.bunyan.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.file.channels.LeaseException;
import com.mastfrog.logstructured.Serde;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.preconditions.Checks.nonNegative;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.thread.BufferPool;
import com.mastfrog.util.thread.BufferPool.BufferHolder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
final class MapSerde implements Serde<Map<String, Object>>, AutoCloseable {

    private final ObjectMapper mapper;
    private final BufferPool pool;
    private static final byte[] NEWLINE = new byte[]{'\n'};

    MapSerde(ObjectMapper mapper) {
        this(mapper, 32768 * 2);
    }

    MapSerde(ObjectMapper mapper, int bufferSize) {
        this.mapper = mapper;
        pool = new BufferPool(nonNegative("bufferSize", bufferSize));
    }

    @Override
    public void close() {
        pool.close();
    }

    @Override
    public <C extends java.nio.channels.WritableByteChannel & java.nio.channels.SeekableByteChannel> void serialize(Map<String, Object> logrecord, C channel) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(notNull("logrecord", logrecord));
        try (BufferHolder holder = pool.buffer()) {
            ByteBuffer buf = holder.buffer();
            if (bytes.length + 5 > buf.capacity()) {
                ByteBuffer len = ByteBuffer.allocate(5);
                len.putInt(bytes.length + 1);
                // We include a \n here so that the raw files will be readable with
                // command-line bunyan, which ignores lines that don't look like JSON
                // and happily parses ones that do - so ensure the record-length
                // data appears to be its own line.  Thus these files become
                // explorable from the console without a special tool to examine them.
                len.put((byte) '\n');
                len.flip();
                int writtenLength = channel.write(len);
                if (writtenLength < 5) {
                    throw new IOException("Failed to write 5-byte length + "
                            + "\\n buffer to channel - only wrote " + writtenLength
                            + " bytes.");
                }
                writtenLength = channel.write(ByteBuffer.wrap(bytes));
                if (writtenLength < bytes.length) {
                    throw new IOException("Failed to write " + bytes.length + "-byte length + "
                            + "\\n data array to channel - only wrote " + writtenLength
                            + " bytes.");
                }
                // Same here - we want these readable for forensics purposes
                writtenLength = channel.write(ByteBuffer.wrap(NEWLINE));
                if (writtenLength < 1) {
                    throw new IOException("Failed to write 1-byte "
                            + "\\n trailing newline buffer to channel - only wrote " + writtenLength
                            + " bytes.");
                }
            } else {
                buf.rewind();
                buf.limit(buf.capacity());
                buf.putInt(bytes.length + 1);
                // We include a \n here so that the raw files will be readable with
                // command-line bunyan, which ignores lines that don't look like JSON
                // and happily parses ones that do - so ensure the record-length
                // data appears to be its own line.  Thus these files become
                // explorable from the console without a special tool to examine them.
                buf.put((byte) '\n');
                buf.put(bytes);
                // Same here - we want these readable for forensics purposes
                buf.put((byte) '\n');
                buf.flip();
                int written = channel.write(buf);
                if (written != bytes.length + 6) {
                    throw new IOException("Write error should have written "
                            + (bytes.length + 6) + " but wrote " + written);
                }
                buf.flip();
            }
        }
    }

    @Override
    public <C extends java.nio.channels.ReadableByteChannel & java.nio.channels.SeekableByteChannel>
            Map<String, Object> deserialize(Path in, C channel) throws LeaseException, IOException {
        long pos = notNull("channel", channel).position();
        try (BufferHolder holder = pool.buffer()) {
            ByteBuffer buf = holder.buffer();
            buf.rewind();
            buf.limit(5);
            int readBytes = channel.read(buf);
            if (readBytes < 5) {
                throw new IOException("Underflow - read < 5 bytes looking for length");
            }
            buf.flip();
            int recordLength = buf.getInt();
            if (recordLength <= 0) {
                throw new IOException("Corrupted file suggested record length " + recordLength);
            }
            if (recordLength > buf.capacity()) {
                buf = ByteBuffer.allocate(recordLength);
            } else {
                buf.rewind();
                buf.limit(recordLength);
            }
            int readCount = channel.read(buf);
            if (readCount < recordLength) {
                throw new IOException("Underflow reading record body - expected " + recordLength + " got " + readCount);
            }
            buf.flip();
            try (InputStream inStream = Streams.asInputStream(false, buf)) {
                return CollectionUtils.uncheckedMap(mapper.readValue(inStream, Map.class));
            } finally {
                // Pool expects position of 0 to mark a buffer as not needing possible write out on shutdown,
                // for better or worse
                buf.position(0);
            }
        } catch (IOException ioe) {
            channel.position(pos);
            throw ioe;
        }
    }
}
