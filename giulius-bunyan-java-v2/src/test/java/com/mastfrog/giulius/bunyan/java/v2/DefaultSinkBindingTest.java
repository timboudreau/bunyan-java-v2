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
package com.mastfrog.giulius.bunyan.java.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.bunyan.java.v2.JSONContext;
import com.mastfrog.bunyan.java.v2.LogSink;
import com.mastfrog.bunyan.java.v2.Logs;
import com.mastfrog.giulius.Dependencies;
import static com.mastfrog.giulius.bunyan.java.v2.LoggingModule.GUICE_BINDING_DEFAULT_SINK;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.thread.ResettableCountDownLatch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class DefaultSinkBindingTest {

    Path logfile;
    Dependencies deps;
    Logs things;
    Logs stuff;
    static Logs STATIC = Logs.named("static");

    @Test
    public void testLogsAreWritten() throws IOException, InterruptedException {
        int max = 30;
        Logs[] all = new Logs[]{things, stuff, STATIC};
        String[] allStrings = new String[max];
        for (int i = 0; i < max; i++) {
            Logs curr = all[i % 3];
            String s = "doit-" + i;
            allStrings[i] = s;
            curr.error(s).add("iv", i).close();
        }
        deps.shutdown();
        Thread.sleep(1000);
        assertTrue(Files.size(logfile) > 0, "Log file is empty");
        ObjectMapper mapper = new ObjectMapper();
        List<String> lines = Files.readAllLines(logfile);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Map<String, Object> m = CollectionUtils.uncheckedMap(mapper.readValue(line, Map.class));
            int iv = (int) m.get("iv");
            String msg = (String) m.get("msg");

            assertEquals(i, iv);
            assertEquals(allStrings[i], msg);
            INSTANCE.assertMessage(msg);
        }
        assertNotNull(INSTANCE);
    }

    @BeforeEach
    public void initLogging() throws IOException {
        System.setProperty("disable.console.logger", "true");
        logfile = FileUtils.newTempFile("logging-module-test.log");
        Settings settings = Settings.builder()
                .add(LoggingModule.SETTINGS_KEY_LOG_FILE, logfile.toString())
                .add(LoggingModule.SETTINGS_KEY_LOG_TO_CONSOLE, true)
                .add(LoggingModule.SETTINGS_KEY_LOG_LEVEL, "debug")
                .add(LoggingModule.SETTINGS_KEY_LOG_TO_CONSOLE, true)
                .add(LoggingModule.SETTINGS_KEY_ASYNC_LOGGING, false)
                .build();

        deps = new Dependencies(settings, new LoggingModule()
                .bindLogger("things").bindLogger("stuff"),
                binder -> {
                    binder.bind(LogSink.class).annotatedWith(Names.named(GUICE_BINDING_DEFAULT_SINK))
                            .to(DefaultSink.class);
                }
        );

        X x = deps.getInstance(X.class);
        things = x.things;
        stuff = x.stuff;
        assertNotNull(things);
        assertNotNull(stuff);
    }

    @AfterEach
    public void shutdownLogging() throws IOException {
        if (deps != null) {
            deps.shutdown();
        }
        Files.deleteIfExists(logfile);
        INSTANCE = null;
    }

    static DefaultSink INSTANCE;

    static final class DefaultSink implements LogSink {

        private static final List<Map<String, Object>> records = new CopyOnWriteArrayList<>();
        private final ResettableCountDownLatch latch = new ResettableCountDownLatch(1);
        private final Set<String> messages = new CopyOnWriteArraySet<>();

        @Inject
        public DefaultSink() {
            assertNull(INSTANCE, "Created twice!");
            INSTANCE = this;
        }

        public String toString() {
            return "test-default-sink";
        }

        private void assertMessage(String msg) {
            assertTrue(messages.contains(msg));
        }

        @Override
        public void push(JSONContext ctx, Map<String, Object> logrecord) {
            CharSequence s = (CharSequence) logrecord.get("msg");
            messages.add(s.toString());
            records.add(logrecord);
            latch.countDown();
        }

    }

    static class X {

        final Logs things;
        final Logs stuff;

        @Inject
        X(@Named("things") Logs things, @Named("stuff") Logs stuff) {
            this.things = things;
            this.stuff = stuff;
        }

    }
}
