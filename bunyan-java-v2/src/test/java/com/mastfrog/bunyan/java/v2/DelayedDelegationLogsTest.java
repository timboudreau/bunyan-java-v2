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

import com.fasterxml.jackson.databind.ObjectMapper;
import static com.mastfrog.bunyan.java.v2.LogLevel.DEBUG_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.ERROR_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.FATAL_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.INFO_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.TRACE_LEVEL;
import static com.mastfrog.bunyan.java.v2.LogLevel.WARN_LEVEL;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class DelayedDelegationLogsTest {

    private static final Logs DEF = Logs.named("other");
    private static final Logs LOG_A = Logs.named("alog");
    private static final Logs LOG_B = Logs.named("blog");
    private static Path aLog;
    private static Path bLog;
    private static Path defaultLog;

    @Test
    public void testLoggingConfigurationMaterializesAfterThresholdRecords() throws InterruptedException {
        Logs[] all = new Logs[]{DEF, LOG_A, LOG_B};
        for (int i = 0; i < 60; i++) {
            Logs log = all[i % 3];
            logOne(i, "message-" + i, log);
        }
        for (int i = 0; i < 5; i++) {
            if (DelayedDelegationLogs.config == null) {
                Thread.sleep(100);
            }
        }
        assertNotNull(DelayedDelegationLogs.config, "Logging config never initialized");
        DelayedDelegationLogs.config.shutdown();
        checkLogContents(aLog, "alog");
        checkLogContents(bLog, "blog");
        checkLogContents(defaultLog, "other");
    }

    private void checkLogContents(Path logFile, String name) {
        List<String> expectedMessages = dataByLog.get(name);
        List<String> foundMessages = new ArrayList<>();
        Map<String, LogLevel> foundLevels = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try (Stream<CharSequence> lines = FileUtils.lines(logFile)) {
            lines.forEach(line -> {
                try {
                    Map<String, Object> record = CollectionUtils.uncheckedMap(mapper.readValue(line.toString(), Map.class));
                    Supplier<String> msg = () -> {
                        return line + " in " + logFile + " for " + name;
                    };
                    assertTrue(record.containsKey("name"), msg);
                    assertTrue(record.get("name") instanceof String, msg);
                    assertTrue(record.containsKey("msg"), msg);
                    assertTrue(record.get("msg") instanceof String, msg);
                    String m = (String) record.get("msg");
                    foundMessages.add(m);
                    assertTrue(record.containsKey("level"), msg);
                    assertTrue(record.get("level") instanceof Integer, msg);
                    int level = (int) record.get("level");
                    switch (level) {
                        case FATAL_LEVEL:
                        case ERROR_LEVEL:
                        case WARN_LEVEL:
                        case INFO_LEVEL:
                        case DEBUG_LEVEL:
                        case TRACE_LEVEL:
                            break;
                        default:
                            fail("Weird log level " + level + " in " + msg.get());
                    }
                    foundLevels.put(m, LogLevel.valueOf(level));
                } catch (IOException ex) {
                    Exceptions.chuck(ex);
                }
            });
        }
        for (Map.Entry<String, LogLevel> e : foundLevels.entrySet()) {
            LogLevel expectedLevel = levelForMessage.get(e.getKey());
            assertNotNull(expectedLevel, "No expected level for " + e.getKey() + " but found " + e.getValue());
            assertEquals(expectedLevel, e.getValue(), "Wrong level for " + e.getKey());
        }
        assertEquals(expectedMessages, foundMessages, () -> {
            List<String> missing = new ArrayList<>(expectedMessages);
            missing.removeAll(foundMessages);
            List<String> surprises = new ArrayList<>(foundMessages);
            surprises.removeAll(expectedMessages);
            if (!missing.isEmpty() || !surprises.isEmpty()) {
                return "Missing: " + missing + "; unexpected: " + surprises;
            }
            return "Order differs";
        });
    }

    Map<String, List<String>> dataByLog = CollectionUtils.supplierMap(ArrayList::new);
    Map<String, LogLevel> levelForMessage = new HashMap<>();

    private void logOne(int ord, String msg, Logs logs) {
        String nm = DEF == logs ? "other" : LOG_A == logs ? "alog" : LOG_B == logs ? "blog" : null;
        assertNotNull(nm, "What is " + logs);
        dataByLog.get(nm).add(msg);
        Log log;
        LogLevel level;
        switch (ord % 6) {
            case 0:
                log = logs.debug();
                level = LogLevel.DEBUG;
                break;
            case 1:
                log = logs.trace();
                level = LogLevel.TRACE;
                break;
            case 2:
                log = logs.info();
                level = LogLevel.INFO;
                break;
            case 3:
                log = logs.warn();
                level = LogLevel.WARN;
                break;
            case 4:
                log = logs.error();
                level = LogLevel.ERROR;
                break;
            case 5:
            default:
                level = LogLevel.FATAL;
                log = logs.fatal();
        }
        log.add("ord", ord)
                .message(msg)
                .add("skiddoo", 23)
                .close();
        levelForMessage.put(msg, level);
    }

    @BeforeAll
    public static void configureLogging() throws IOException {
        System.setProperty("disable.console.logger", "true");
        aLog = FileUtils.newTempFile("bunyan-alog");
        bLog = FileUtils.newTempFile("bunyan-alog");
        defaultLog = FileUtils.newTempFile("bunyan-defaultLog");
        setSystemProperties(
                LoggingConfig.PROP_DEFAULT_FILE, defaultLog,
                LoggingConfig.PROP_ASYNC, "false",
                LoggingConfig.PROP_LOG_CONSOLE, "true",
                LoggingConfig.PROP_SEQ_NUMBERS, "true",
                LoggingConfig.PROP_HOSTNAME, "wiggles",
                LoggingConfig.PROP_MIN_LEVEL, "trace",
                LoggingConfig.PROP_ROUTED_LOGGERS, "alog,blog",
                LoggingConfig.PROP_ROUTE_PREFIX + "alog", aLog,
                LoggingConfig.PROP_ROUTE_PREFIX + "blog", bLog
        );
    }

    @AfterAll
    public static void cleanup() throws IOException {
        boolean aLogExists = FileUtils.deleteIfExists(aLog);
        boolean bLogExists = FileUtils.deleteIfExists(bLog);
        boolean dLogExists = FileUtils.deleteIfExists(defaultLog);
        assertTrue(aLogExists, aLog + " not created");
        assertTrue(bLogExists, bLog + " not created");
        assertTrue(dLogExists, defaultLog + " not created");
    }

    private static void setSystemProperties(Object... items) {
        assert items.length % 2 == 0;
        for (int i = 0; i < items.length; i += 2) {
            System.setProperty(items[i].toString(), items[i + 1].toString());
        }
    }

}
