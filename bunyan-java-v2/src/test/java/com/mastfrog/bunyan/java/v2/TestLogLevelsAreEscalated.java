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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TestLogLevelsAreEscalated {

    Logs logs;
    LogCapture severe;
    LogCapture all;
    LoggingConfig cfig;

    @Test
    public void testRightLoggersAreReturned() {
        LogSink sink = cfig.sinkFor("test", LogLevel.FATAL);
        assertTrue(sink.toString().contains("LogCapture(severe-only)")
                && sink.toString().contains("LogCapture(all-log-records)"), sink.toString());
        sink = cfig.sinkFor("test", LogLevel.ERROR);
        assertTrue(sink.toString().contains("LogCapture(severe-only)")
                && sink.toString().contains("LogCapture(all-log-records)"), sink.toString());
        sink = cfig.sinkFor("test", LogLevel.TRACE);
        assertTrue(sink.toString().contains("NULL"), sink.toString());

        sink = cfig.sinkFor("debug", LogLevel.DEBUG);
        assertTrue(sink.toString().contains("LogCapture(all-log-records)")
                && !sink.toString().contains("LogCapture(severe-only)"), sink.toString());
        sink = cfig.sinkFor("info", LogLevel.INFO);
        assertTrue(sink.toString().contains("LogCapture(all-log-records)")
                && !sink.toString().contains("LogCapture(severe-only)"), sink.toString());
    }

    @Test
    public void testLogEscalation() {
        logs.debug("traceMe", log -> {
            log.add("ix", 1);
        });
        logs.debug("traceThisException", log -> {
            log.add(new RuntimeException("foo"));
        });
        logs.debug("shouldBeFatal", log -> {
            log.add(new NullPointerException("bar"));
        });
        logs.trace("traceThisExceptionToo", log -> {
            log.add("z", new RuntimeException("foo"));
        });
        logs.debug("shouldAlsoBeFatal", log -> {
            log.add("q", new NullPointerException("bar"));
        });
        logs.error("errorMe", log -> {
            log.add("ix", 2);
        });
        logs.trace("shouldNotExist", log -> {
            log.add("x", "y");
        });
        all.assertHasMessage("errorMe");
        all.assertHasMessage("traceMe");
        severe.assertHasMessage("errorMe");
        severe.assertNoMessage("traceMe");
        all.assertLevelForMessage(LogLevel.ERROR, "traceThisException");
        all.assertLevelForMessage(LogLevel.ERROR, "traceThisExceptionToo");
        all.assertLevelForMessage(LogLevel.FATAL, "shouldBeFatal");
        all.assertLevelForMessage(LogLevel.FATAL, "shouldAlsoBeFatal");
        all.assertLevelForMessage(LogLevel.DEBUG, "traceMe");
        all.assertLevelForMessage(LogLevel.ERROR, "errorMe");
        severe.assertLevelForMessage(LogLevel.ERROR, "traceThisException");
        severe.assertLevelForMessage(LogLevel.ERROR, "traceThisExceptionToo");
        severe.assertLevelForMessage(LogLevel.FATAL, "shouldBeFatal");
        severe.assertLevelForMessage(LogLevel.FATAL, "shouldAlsoBeFatal");
        all.assertNoMessage("shouldNotExist");
    }

    @BeforeEach
    public void setup() {
        severe = new LogCapture("severe-only");
        all = new LogCapture("all-log-records");
        cfig = LoggingConfig.builder()
                .logTo(all)
                .logErrorAndFatalTo(severe)
                .setMinimumLogLevelToDebug()
                .escalateOnError()
                .build();
        logs = cfig.logs("test");
    }

    @AfterEach
    public void after() {
        if (cfig != null) {
            cfig.shutdown();
        }
    }

    static final class LogCapture implements LogSink {

        private final Set<String> msgs = new HashSet<>();
        private final Map<String, Integer> levelForMessage = new HashMap<>();
        private final String name;

        public LogCapture(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "LogCapture(" + name + ")";
        }

        public void assertLevelForMessage(LogLevel level, String msg) {
            assertHasMessage(msg);
            int gotLevel = levelForMessage.get(msg);
            assertEquals(level.intValue(), gotLevel, "'" + msg + "' logged at " + LogLevel.valueOf(gotLevel));
        }

        public void assertHasMessage(String s) {
            assertTrue(msgs.contains(s), name + " should have receievd '" + s + "' but did not");
        }

        public void assertNoMessage(String s) {
            assertFalse(msgs.contains(s), name + " should not have receievd '" + s + "' but did.");
        }

        @Override
        public void push(JSONContext ctx, Map<String, Object> logrecord) {
            String message = (String) logrecord.get("msg");
            Integer level = ((Number) logrecord.get("level")).intValue();
            levelForMessage.put(message, level);
            assertNotNull(message);
            msgs.add(message);
        }
    }
}
