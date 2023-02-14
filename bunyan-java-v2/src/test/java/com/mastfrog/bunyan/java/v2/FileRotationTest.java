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

import com.mastfrog.util.file.FileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FileRotationTest {

    private static final int BATCH_SIZE = 3000;
    private Path dir;
    private LoggingConfig config;
    private Logs logs;
    private Random rnd;

    @Test
    public void test() throws Exception {
        writeSomeStuff();
        writeSomeStuff();

        List<Path> all = new ArrayList<>();
        try (Stream<Path> str = Files.list(dir).filter(fl -> fl.getFileName().toString().contains(getClass().getSimpleName()))) {
            str.forEach(all::add);
        }
        assertEquals(3, all.size());
        assertTrue(all.contains(dir.resolve(getClass().getSimpleName() + ".log")));
        assertTrue(all.contains(dir.resolve(getClass().getSimpleName() + "_1.log")));
        assertTrue(all.contains(dir.resolve(getClass().getSimpleName() + "_2.log")));

    }

    private void writeSomeStuff() {
        Random r = rnd;
        for (int i = 0; i < BATCH_SIZE; i++) {
            byte[] bytes = new byte[128];
            r.nextBytes(bytes);
            int ix = i;
            logs.info("Item " + i, log -> {
                log.add("TheseAreSomeBytesWithAReallyLongName", Base64.getEncoder().encodeToString(bytes))
                        .add("index", ix).add("odd", ix % 2 != 0);
            });
        }
        System.out.println("Wrote " + BATCH_SIZE);
    }

    @BeforeEach
    public void createLogs() throws IOException {
        dir = FileUtils.newTempDir(getClass().getSimpleName() + "_");
        System.out.println("Dir is " + dir);
        config = LoggingConfig.builder().fileRotationThresholdMegabytes(1)
                .dontUseShutdownHook().hostNameForLogRecords("blah")
                .logToFile(dir.resolve(getClass().getSimpleName() + ".log"))
                .withSequenceNumbers()
                .setMinimumLogLevelToDebug()
                .nonDefault()
                .build();
        logs = config.logs("testStuff");
        rnd = ThreadLocalRandom.current();
    }

    @AfterEach
    public void shutdownLogs() throws IOException {
        try {
            if (config != null) {
                config.shutdown();
            }
        } finally {
            if (dir != null) {
                FileUtils.deltree(dir);
            }
        }
    }
}
