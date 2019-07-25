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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TestContextualLogging {

    private LoggingConfig cfig;
    private Logs foo;
    private Logs bar;
    private LS fooSink;
    private LS barSink;

    @Test
    public void testContextualLogsAreRoutedToCurrentContext() throws Throwable {
        foo.error("foo1", foolg -> {
            foolg.add("skiddoo", 23);
            try (Log barlg = bar.contextual().fatal("bar1")) {
                barlg.add("hbie", 56);
            }
        });
        barSink.assertNotLogged("foo1");
        fooSink.assertLogged("foo1");
        fooSink.assertLogged("bar1");
        barSink.assertNotLogged("bar1");
    }

    @BeforeEach
    public void setup() {
        fooSink = new LS("foo");
        barSink = new LS("bar");
        cfig = LoggingConfig.builder().routeLogsTo(fooSink, "foo")
                .routeLogsTo(barSink, "bar").build();
        foo = cfig.logs("foo");
        bar = cfig.logs("bar");
    }

    @AfterEach
    public void teardown() {
        if (cfig != null) {
            cfig.shutdown();
        }
    }

    static final class LS implements LogSink {

        private final String name;
        private final Set<String> messages = new HashSet<>();

        public LS(String name) {
            this.name = name;
        }

        @Override
        public void push(JSONContext ctx, Map<String, Object> logrecord) {
            messages.add((String) logrecord.get("msg"));
        }

        public void assertLogged(String msg) {
            assertTrue(messages.contains(msg), msg + " was not logged to " + name);
        }

        public void assertNotLogged(String msg) {
            assertFalse(messages.contains(msg), msg + " was logged to " + name + " but should not have been");
        }
    }
}
