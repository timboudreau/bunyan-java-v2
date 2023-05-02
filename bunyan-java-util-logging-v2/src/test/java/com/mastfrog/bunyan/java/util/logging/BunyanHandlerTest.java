/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.bunyan.java.util.logging;

import com.google.inject.AbstractModule;
import com.mastfrog.bunyan.java.util.logging.BunyanHandlerTest.CustomSinkModule;
import com.mastfrog.bunyan.java.v2.JSONContext;
import com.mastfrog.bunyan.java.v2.LogSink;
import com.mastfrog.bunyan.java.v2.LoggingConfig;
import com.mastfrog.bunyan.java.v2.Logs;
import com.mastfrog.giulius.bunyan.java.v2.LoggingModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.TestWith;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({BunyanJavaLoggingModule.class, CustomSinkModule.class})
public class BunyanHandlerTest {

    static Logger LOGGER = Logger.getLogger("foo.bar.baz");
    static final int FATAL_LEVEL = 60;
    static final int ERROR_LEVEL = 50;
    static final int WARN_LEVEL = 40;
    static final int INFO_LEVEL = 30;
    static final int DEBUG_LEVEL = 20;
    static final int TRACE_LEVEL = 10;

    @Test
    public void testSomeMethod(CSink sink) {
        Exception ex = new IllegalStateException();
        LOGGER.log(Level.WARNING, "This is a warning");
        LOGGER.log(Level.INFO, "This is info");
        LOGGER.log(Level.SEVERE, "Something thrown", ex);

        sink.assertHasMessage(WARN_LEVEL, "This is a warning");
        sink.assertHasMessage(INFO_LEVEL, "This is info");
        sink.assertHasMessage(ERROR_LEVEL, "Something thrown");
    }

    static final class CustomSinkModule extends AbstractModule {

        @Override
        protected void configure() {
            CSink sink = new CSink();
            LoggingConfig cfig = new LoggingConfig.Builder()
                    .logTo(sink)
                    .hostNameForLogRecords("localhost")
                    .build();
            bind(LogSink.class).toInstance(sink);
            bind(CSink.class).toInstance(sink);
            bind(LoggingConfig.class).toInstance(cfig);
            install(new LoggingModule().dontBindLoggingConfig());
            bind(Logs.class).toProvider(() -> {
                return Logs.named("stuff");
            });
        }
    }

    private static final class CSink implements LogSink {

        private final List<Entry> entries = new LinkedList<>();

        Entry assertHasMessage(Integer level, String msg) {
            boolean found = false;
            Entry result = null;
            for (Entry e : entries) {
                if (Objects.equals(e.level, level)) {
                    Object o = e.record.get("msg");
                    if (o != null) {
                        if (msg.equals(o.toString())) {
                            found = true;
                            result = e;
                            break;
                        }
                    }
                }
            }
            assertTrue("Did not find '" + msg + "' at '" + level + "' in " + entries, found);
            return result;
        }

        @Override
        public void push(JSONContext ctx, Map<String, Object> logrecord) {
            Integer level = (Integer) logrecord.get("level");
            assertNotNull(level);
            entries.add(new Entry(level, logrecord));
        }
    }

    static final class Entry {

        private final Integer level;
        private final Map<String, Object> record;

        public Entry(Integer level, Map<String, Object> record) {
            this.level = level;
            this.record = record;
        }

        public String toString() {
            return level + ": " + record;
        }
    }
}
