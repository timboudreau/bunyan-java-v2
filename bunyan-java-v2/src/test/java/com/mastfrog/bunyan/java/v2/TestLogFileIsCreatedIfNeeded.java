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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class TestLogFileIsCreatedIfNeeded {

    private static final Path nonexistent = Paths.get("/tmp/092309203u923-x");
    private static final Path nonexistent2 = Paths.get("/tmp/092309203u923-y");

    @Test
    public void test() {
        assertFalse(Files.exists(nonexistent));
        LoggingConfig cfig = LoggingConfig.builder().logToFile(nonexistent)
                .logErrorAndFatalTo(nonexistent2)
                .build();
        Logs lgs = cfig.logs("wooper");
        lgs.fatal("foo foo").add("name", "fru").close();
        cfig.shutdown();
        assertTrue(Files.exists(nonexistent));
    }

    @BeforeEach
    public void before() throws IOException {
        Files.deleteIfExists(nonexistent);
        Files.deleteIfExists(nonexistent2);
    }

    @BeforeEach
    public void after() throws IOException {
        Files.deleteIfExists(nonexistent);
        Files.deleteIfExists(nonexistent2);
    }
}
