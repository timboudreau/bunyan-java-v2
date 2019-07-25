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
import com.mastfrog.bunyan.java.v2.LogSink;
import com.mastfrog.file.channels.FileChannelPool;
import com.mastfrog.logstructured.LogStructuredReader;
import com.mastfrog.logstructured.LogStructuredStorage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
public class RemoteSinkSupport {

    private final LogStructuredStorage<Map<String, Object>> lss;
    private final FileChannelPool pool;
    private final AtomicBoolean spoolingStarted = new AtomicBoolean();
    private final Supplier<RemoteSink> remoteSink;
    private final ExecutorService sendThreads;
    private final DiskSink diskSink;
    private LogRecordSender sender;
    private final MapSerde serde;

    public RemoteSinkSupport(Supplier<RemoteSink> remoteSink, Path storageDir, ObjectMapper mapper) {
        // If we use more than one thread, we will duplicate log-records -
        // LogStructuredReader reads the same record again and again until
        // it is advanced, which should only happen when the remote sink
        // has called the callback after a send - so we MUST send records
        // serially
        sendThreads = Executors.newSingleThreadExecutor();
        serde = new MapSerde(mapper);
        lss = LogStructuredStorage.forDir(storageDir, serde, pool = FileChannelPool.newPool(120000));
        this.remoteSink = remoteSink;
        diskSink = new DiskSink(lss.appender());
    }

    void shutdown() throws InterruptedException, IOException {
        sender.touch();
        sendThreads.shutdown();
        sendThreads.awaitTermination(30, TimeUnit.SECONDS);
        pool.close();
        serde.close();
    }

    LogSink start() {
        if (spoolingStarted.compareAndSet(false, true)) {
            RemoteSink sink = remoteSink.get();
            sink.open((ready) -> {
                if (ready) {
                    try {
                        LogStructuredReader<Map<String, Object>> reader = lss.reader();
                        reader.deleteIfAllReadAndAdvanced();
                        sender = new LogRecordSender(reader, sink, sendThreads);
                        diskSink.touch = sender::touch;
                        sender.touch();
                    } catch (IOException ex) {
                        Logger.getLogger(RemoteSinkSupport.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        }
        return diskSink;
    }
}
