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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.mastfrog.bunyan.java.v2.LoggingConfig;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TestSettingsAreHonored {

    Path a, b, c, d;

    @Test
    public void testSettings() throws IOException {
        SettingsBuilder b = Settings.builder();
        checkOne("levelConfig.default", 30, b, false);
        b.add(LoggingModule.SETTINGS_KEY_LOG_LEVEL, "warn");
        checkOne("levelConfig.default", 40, b, false);
        b.add(LoggingModule.SETTINGS_KEY_LOG_SEQUENCE_NUMBERS, "true");

        b.add(LoggingModule.SETTINGS_KEY_LOG_SEVERE_TO_FILE, a.toString());
        checkOne("router.logPathForSevere", a, b, false);
        b.add(LoggingModule.SETTINGS_KEY_LOG_FILE, this.b.toString());
        checkOne("router.defaultRoute.path", this.b, b, false);
        checkOne("router.defaultRoute.dead", false, b, false);

        b.add(LoggingModule.SETTINGS_KEY_ROUTED_LOGS, "boorarf,hoodge");
        b.add(LoggingModule.SETTINGS_KEY_ROUTED_LOG_PREFIX + "boorarf", c.toString());
        b.add(LoggingModule.SETTINGS_KEY_ROUTED_LOG_PREFIX + "hoodge", d.toString());
        checkOne("router.routes.boorarf", c, b, false);
        checkOne("router.routes.hoodge", d, b, false);
        b.add(LoggingModule.SETTINGS_KEY_ROUTED_LOG_LEVEL_PREFIX + "boorarf", "trace");
        b.add(LoggingModule.SETTINGS_KEY_ROUTED_LOG_LEVEL_PREFIX + "hoodge", "fatal");
        checkOne("levelConfig.minLevels.boorarf", "trace", b, false);
        checkOne("levelConfig.minLevels.hoodge", "fatal", b, false);
        checkOne("hostname", null, b, true);
        b.add(LoggingModule.SETTINGS_KEY_LOG_HOSTNAME, "wumper");
        checkOne("hostname", "wumper", b, false);
        checkOne("levelConfig.default", 40, b, false);
    }

    @BeforeEach
    public void tempfiles() throws IOException {
        a = FileUtils.newTempFile("TestSettingsAreHonored-1-");
        b = FileUtils.newTempFile("TestSettingsAreHonored-2-");
        c = FileUtils.newTempFile("TestSettingsAreHonored-3-");
        d = FileUtils.newTempFile("TestSettingsAreHonored-4-");
    }

    private void checkOne(String path, Object expectedValue, SettingsBuilder sb, boolean expectNull) throws IOException {
        if (expectedValue instanceof Path) {
            expectedValue = expectedValue.toString();
        }
        Dependencies deps = new Dependencies(sb.build(), new LoggingModule());
        try {
            LoggingConfig config = deps.getInstance(LoggingConfig.class);
            ObjectMapper mapper = deps.getInstance(Key.get(ObjectMapper.class,
                    Names.named(LoggingModule.GUICE_BINDING_OBJECT_MAPPER))).enable(SerializationFeature.INDENT_OUTPUT);
            String s = mapper.writeValueAsString(config);
            Map<String, Object> m = CollectionUtils.uncheckedMap(mapper.readValue(s, Map.class));
            Object o = find(path, m, path, expectNull);
            assertEquals(expectedValue, o, path);
            config.shutdown();
        } finally {
            deps.shutdown();
        }
    }

    private Object find(String path, Map<String, Object> map, String fullpath, boolean expectNull) {
        int dot = path.indexOf('.');
        if (dot > 0) {
            String item = path.substring(0, dot);
            path = path.substring(dot + 1);
            Object found = map.get(item);
            if (found == null) {
                fail("Out of objects looking for " + item + " of " + fullpath + " in " + map);
            }
            if (!(found instanceof Map<?, ?>)) {
                fail("Not a map at " + item + " in " + fullpath + ": " + found + " (" + found.getClass().getName() + ")");
            }
            return find(path, CollectionUtils.uncheckedMap((Map<?, ?>) found), fullpath, expectNull);
        } else {
            Object result = map.get(path);
            if (result == null && !expectNull) {
                fail("No " + fullpath + " at " + path + " in " + map);
            }
            return result;
        }
    }
}
