/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
import com.mastfrog.jackson.configuration.JacksonConfigurer;
import com.mastfrog.util.collections.StringObjectMap;
import com.mastfrog.util.fileformat.SimpleJSON;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class InstantInNonJacksonTest {

    private LoggingConfig config;
    private List<CharSequence> lines;
    private Logs logs;
    ObjectMapper m = JacksonConfigurer.configureFromMetaInfServices(new ObjectMapper());

    @Test
    public void test() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("hey", "you");
        map.put("when", Instant.parse("2023-02-14T04:01:10.697915Z"));
        map.put("vookerboodle", Instant.parse("2023-02-14T04:01:10.697915Z"));

        String res = SimpleJSON.stringify(map, SimpleJSON.Style.COMPACT);
        Map<String, Object> decoded = m.readValue(res, StringObjectMap.class);
        assertTrue(decoded.containsKey("hey"));
        assertTrue(decoded.containsKey("when"));
        assertTrue(decoded.containsKey("vookerboodle"));

        logs.error("stuff", log -> {
            log.add("foo", "bar")
                    .add("When", Instant.parse("2023-02-14T04:01:10.697915Z"))
                    .add("Dur", Duration.ofMinutes(10));
        });
        int ct = 0;
        while (lines.isEmpty()) {
            Thread.sleep(10);
            if (ct > 200) {
                break;
            }
        }
        assertFalse(lines.isEmpty(), "No lines collected");
        try {
            Map<String, Object> objs = m.readValue(lines.get(0).toString(), StringObjectMap.class);

            assertEquals("bar", objs.get("foo"));
            assertTrue(objs.containsKey("When"));
            assertTrue(objs.containsKey("Dur"));
        } catch (Exception ex) {
            throw new AssertionError("Invalid json.  Line:\n" + lines.get(0) + "\n", ex);
        }
    }

    @BeforeEach
    public void setup() {
        System.setProperty("disable.console.logger", "false");
        lines = new ArrayList<>();
        LoggingConfig[] cfig = new LoggingConfig[1];
        Supplier<LoggingConfig> supp = () -> cfig[0];
        ConsoleLogSink sink = new ConsoleLogSink(supp, lines::add);
        config = LoggingConfig.builder().nonDefault()
                .dontUseShutdownHook()
                .neverUseJacksonForJSON()
                .logTo(sink)
                .dontEscalateOnError()
                .hostNameForLogRecords("whoopty")
                .setMinimumLogLevelToDebug()
                .routeLogsTo(sink, "stuff")
                .build();
        cfig[0] = config;
        logs = config.logs("stuff");
    }

}
