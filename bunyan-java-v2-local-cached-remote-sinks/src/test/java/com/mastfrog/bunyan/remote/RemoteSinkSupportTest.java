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
import com.mastfrog.bunyan.java.v2.JSONContext;
import com.mastfrog.bunyan.java.v2.LoggingConfig;
import com.mastfrog.bunyan.java.v2.Logs;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class RemoteSinkSupportTest {

    private Path dir;
    private RemoteSinkImpl rs;
    private RemoteSinkSupport rss;
    private LoggingConfig config;
    private Logs foo;
    private Logs bar;

    @Test
    public void testSomeMethod() throws InterruptedException, IOException {
        int max = 20;
        for (int i = 0; i < max; i++) {
            int ix = i;
            String x = foo.info("x-" + i, log -> {
                log.add("foo", "bar");
                return "x-" + ix;
            });
            assertEquals("x-" + ix, x);
        }
        config.shutdown();
        rss.shutdown();
        rs.close();
        List<Map<String, Object>> all = readAll();
        Set<String> onDisk = new HashSet<>();
        for (Map<String, Object> m : all) {
            String msg = (String) m.get("msg");
            onDisk.add(msg);
        }
        for (int i = 0; i < max; i++) {
            String lookFor = "x-" + i;
            assertTrue(rs.msgs.contains(lookFor), "Missing " + lookFor);
            assertTrue(onDisk.contains(lookFor), "Not found on disk: " + lookFor);
        }
    }

    private List<Map<String, Object>> readAll() throws IOException {
        ObjectMapper m = new ObjectMapper();
        MapSerde serde = new MapSerde(m);
        List<Map<String, Object>> l = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(file -> {
                if (!file.getFileName().toString().endsWith(".cursor")) {
                    try (FileChannel readChannel = FileChannel.open(file, StandardOpenOption.READ)) {
                        while (readChannel.position() < readChannel.size()) {
                            Map<String, Object> record = serde.deserialize(file, readChannel);
                            System.out.println("DISK: " + record + " pos now " + readChannel.position() + " / " + readChannel.size());
                            l.add(record);
                        }
                    } catch (IOException ex) {
                        Exceptions.chuck(ex);
                    }
                }
            });
        }
        return l;
    }

    @BeforeEach
    public void setup() throws IOException {
        dir = FileUtils.newTempDir(RemoteSinkSupportTest.class.getSimpleName() + "-");
        rs = new RemoteSinkImpl();
        ObjectMapper mapper = new ObjectMapper();
        rss = new RemoteSinkSupport(() -> rs, dir, mapper);
        config = LoggingConfig.builder().logTo(rss.start()).build();
        foo = config.logs("foo");
        bar = config.logs("bar");
    }

    @AfterEach
    public void teardown() throws IOException {
        try {
            if (dir != null) {
                FileUtils.deltree(dir);
            }
        } finally {
            config.shutdown();
        }
    }

    static final class RemoteSinkImpl implements RemoteSink {

        private final ExecutorService svc = Executors.newSingleThreadExecutor();
        private final Set<String> msgs = Collections.synchronizedSet(new HashSet<>());

        @Override
        public void open(Consumer<Boolean> whenReady) {
            svc.submit(() -> {
                whenReady.accept(true);
            });
        }

        @Override
        public void push(JSONContext ctx, Map<String, Object> logRecord, Runnable onDone) {
            String s = (String) logRecord.get("msg");
            msgs.add(s);
            svc.submit(onDone);
        }

        @Override
        public void close() throws IOException {
            for (Runnable r : svc.shutdownNow()) {
                r.run();
            }
        }

    }
}
