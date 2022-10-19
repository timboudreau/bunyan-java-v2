package com.mastfrog.bunyan.java.v2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.otherpackage.Hammerer;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.DefaultLoggingConfigHandling.NON_DEFAULT;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.DefaultLoggingConfigHandling.SET_IF_UNSET;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.DefaultLoggingConfigHandling.TAKE_OVER;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.JsonSerializationPolicy.ADAPTIVE;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.JsonSerializationPolicy.ALWAYS_JACKSON;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.JsonSerializationPolicy.NEVER_JACKSON;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.PROP_VALUE_DONT_SET_AS_DEFAULT_CONFIG;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.PROP_VALUE_JSON_SERIALIZATION_POLICY_ADAPTIVE;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.PROP_VALUE_JSON_SERIALIZATION_POLICY_ALWAYS_JACKSON;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.PROP_VALUE_JSON_SERIALIZATION_POLICY_NEVER_JACKSON;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.PROP_VALUE_TAKE_OVER_AS_DEFAULT_CONFIG;
import static com.mastfrog.bunyan.java.v2.LoggingConfig.PROP_VALUE_USE_AS_DEFAULT_CONFIG_IF_UNSET;
import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.collections.CollectionUtils;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Phaser;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class LogsImplTest {

    Logs STATIC;

    LoggingConfig lc;
    Logs foo;
    Logs bar;
    Logs boo;
    Path tmp;
    Path severes;
    Path boos;
    Path statics;
    CachingLogSink cls;
    CachingLogSink sevs;
    CachingLogSink booSink;
    CachingLogSink staticsSink;

    @Test
    public void testConcurrentLogging() throws Throwable {
        int max = 20;
        int threads = 7;
        Phaser phaser = new Phaser(threads + 1);
        Runnable r = new Hammerer(phaser, max, foo, bar, boo);
        Thread[] th = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            th[i] = new Thread(r, "hammer-" + (i + 1));
            th[i].setDaemon(true);
            th[i].start();
        }
        phaser.arriveAndDeregister();
        for (int i = 0; i < th.length; i++) {
            th[i].join();
        }
        lc.shutdown();
        Thread.sleep(1500);
        cls.assertMatches(tmp);
        sevs.assertMatches(severes);
        booSink.assertMatches(boos);
    }

    @Test
    public void testBasicLogging() throws InterruptedException, IOException {
        Logs staticChild = STATIC.child("child", 1);
        foo.error(lg -> {
            assertNotNull(lg);
            lg.message("Bad thing happened")
                    .add("hey", 23)
                    .add("bad", true);
        });
        bar.debug(lg -> {
            assertNotNull(lg);
            lg.add("wunk", 5.5)
                    .add("zip", new int[]{1, 2, 3, 4})
                    .add("null", null)
                    .message("Uh oh");
        });
        Logs ch = bar.child("thing", "two");
        for (int i = 0; i < 20; i++) {
            try (Log l = ch.warn()) {
                l.message("msg-" + i);
                l.add("bean", i % 2 == 0)
                        .add("moo", 23 * i).add("x", i);
                Thread.sleep(50);
            }
        }
        for (int i = 20; i < 1140; i++) {
            if (i % 100 == 0) {
                int ix = i;
                staticChild.warn((lg) -> {
                    lg.message("gubba gubba hey").add("iv", ix);
                });
            }
            if (i % 27 == 0) {
                int fi = i;
                try (Log bl = boo.warn()) {
                    bl.message("boo-" + fi).add("whatevs", fi).add("amt", (double) 27);
                }
//                boo.info(bl -> {
//                    bl.message("boo-" + fi).add("whatevs", 27);
//                });
            }
            try (Log l = ch.warn()) {
                l.message("msg-" + i);
                l.add("bean", i % 2 == 0)
                        .add("moo", 23 * i).add("x", i)
                        .addLazy("wookie", () -> {
                            return map("hey").to("you").map("wunk").finallyTo(ZonedDateTime.now().toEpochSecond());
                        }
                        );
                if (i % 100 == 0) {
                    Thread.sleep(10);
                }
            }
        }
        lc.shutdown();
        Thread.sleep(1500);
        cls.assertMatches(tmp);
        sevs.assertMatches(severes);
        booSink.assertMatches(boos);
        staticsSink.assertMatches(statics);
        staticsSink.all.forEach(record -> {
            assertTrue(record.containsKey("child"));
            assertTrue(record.get("child") instanceof Integer);
            assertEquals(Integer.valueOf(1), record.get("child"));
        });
    }

    @BeforeEach
    public void doIt() throws IOException {
        System.setProperty("disable.console.logger", "true");
        STATIC = Logs.named("static");
        assertNull(DelayedDelegationLogs.config, "Logging configuration already set at test start");
        assertFalse(DelayedDelegationLogs.hasDelegate(STATIC));
        STATIC.error().add("This should get printed on initialization");
        tmp = FileUtils.newTempFile("bunyan-test");
        severes = FileUtils.newTempFile("bunyan-test-severe");
        boos = FileUtils.newTempFile("bunyan-test-boo");
        statics = FileUtils.newTempFile("bunyan-test-statics");
        sevs = new CachingLogSink("severe", () -> lc);
        cls = new CachingLogSink("default", () -> lc);
        booSink = new CachingLogSink("boo", () -> lc);
        staticsSink = new CachingLogSink("statics", () -> lc);
        lc = LoggingConfig
                .builder()
                .logToConsole()
                .logToFile(tmp)
                .logTo(cls)
                .logErrorAndFatalTo(sevs)
                .logErrorAndFatalTo(severes)
                .routeLogsTo(booSink, "boo")
                .routeLogsTo(boos, "boo")
                .routeLogsTo(statics, "static")
                .routeLogsTo(staticsSink, "static")
                .asyncLogging()
                .withSequenceNumbers()
                .recordCaller()
                .build();
        assertNotNull(lc);
        foo = lc.logs("foo");
        assertNotNull(foo);
        bar = lc.logs("bar");
        assertNotNull(bar);
        boo = lc.logs("boo");

        assert PROP_VALUE_TAKE_OVER_AS_DEFAULT_CONFIG.equals(TAKE_OVER.toString());
        assert PROP_VALUE_USE_AS_DEFAULT_CONFIG_IF_UNSET.equals(SET_IF_UNSET.toString());
        assert PROP_VALUE_DONT_SET_AS_DEFAULT_CONFIG.equals(NON_DEFAULT.toString());
        assert ADAPTIVE.toString().equals(PROP_VALUE_JSON_SERIALIZATION_POLICY_ADAPTIVE);
        assert ALWAYS_JACKSON.toString().equals(PROP_VALUE_JSON_SERIALIZATION_POLICY_ALWAYS_JACKSON);
        assert NEVER_JACKSON.toString().equals(PROP_VALUE_JSON_SERIALIZATION_POLICY_NEVER_JACKSON);

    }

    public static final class R implements Runnable {

        public int foo = 23;

        @Override
        public void run() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @AfterEach
    public void didIt() throws IOException {
        lc.shutdown();
        FileUtils.deleteIfExists(tmp);
        FileUtils.deleteIfExists(severes);
        FileUtils.deleteIfExists(boos);
        FileUtils.deleteIfExists(statics);
    }

    static final class CachingLogSink extends AbstractLogSink {

        AtomicLinkedQueue<Map<String, Object>> all = new AtomicLinkedQueue<>();
        @JsonProperty("cachingLogSinkName")
        private final String name;

        public CachingLogSink(String name, Supplier<LoggingConfig> configSupplier) {
            super(configSupplier);
            this.name = name;
        }

        public String toString() {
            return "CachingLogSink(" + name + ")";
        }

        @Override
        @SuppressWarnings("unchecked")
        public void push(JSONContext ctx, Map<String, Object> logRecord) {
            try {
                // ensure no mismatches due to longs being read back as ints, etc.
                ObjectMapper mapper = new ObjectMapper();
                byte[] b = mapper.writeValueAsBytes(logRecord);
                all.add(mapper.readValue(b, Map.class));
            } catch (IOException ex) {
                Exceptions.chuck(ex);
            }
        }

        @SuppressWarnings("unchecked")
        public void assertMatches(Path file) throws IOException {
            assertFalse(all.isEmpty(), "Logs never routed to sink for " + name);
            Set<Map<String, Object>> got = new HashSet<>();
            ObjectMapper mapper = new ObjectMapper();
            for (String line : Files.readAllLines(file)) {
                Map<String, Object> m = (Map<String, Object>) mapper.readValue(line, Map.class);
                got.add(m);
            }
            Set<Map<String, Object>> all = new HashSet<>(this.all.drain());
            assertEquals(all.size(), got.size(), "Size mismatch for " + name);
            if (!Objects.equals(got, all)) {
                Set<Map<String, Object>> dis = CollectionUtils.disjunction(all, got);
                fail(name + " file contents do not match - disjunction: " + dis);
            }
        }
    }
}
