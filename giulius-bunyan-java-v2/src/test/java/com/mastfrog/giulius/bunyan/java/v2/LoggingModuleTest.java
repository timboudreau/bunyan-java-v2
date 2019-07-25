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
import com.mastfrog.bunyan.java.v2.Logs;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class LoggingModuleTest {

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
            curr.warn(s).add("iv", i).close();
        }
        deps.shutdown();
        Thread.sleep(100);
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
        }
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
                .bindLogger("things").bindLogger("stuff"));

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
