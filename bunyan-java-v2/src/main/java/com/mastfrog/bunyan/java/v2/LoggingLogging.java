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

import com.mastfrog.util.time.TimeUtil;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;

/**
 * Since a failure inside a logger can't log to itself, we do this.
 *
 * @author Tim Boudreau
 */
class LoggingLogging {

    private static final boolean ACTIVE;
    private final static long startup = System.currentTimeMillis();

    static {
        ACTIVE = Boolean.getBoolean("bunyan.debug");
    }

    private static String prefix() {
        long now = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - startup;
        Duration runningFor = Duration.ofMillis(elapsed);
        Instant at = Instant.ofEpochMilli(now);
        return at + " " + TimeUtil.format(runningFor, true)
                + '\t';
    }

    static void log(String msg) {
        log(false, msg);
    }

    static void log(boolean alwaysLog, String msg) {
        if (alwaysLog || ACTIVE) {
            System.err.println(prefix() + msg);
        }
    }

    static void log(boolean alwaysLog, String msg, Object... args) {
        if (alwaysLog || ACTIVE) {
            System.err.println(prefix() + MessageFormat.format(msg, args));
        }
    }

    static void log(String msg, Object... args) {
        if (ACTIVE) {
            System.err.println(prefix()
                    + MessageFormat.format(msg, args));
        }
    }

    static void log(Throwable thrown) {
        if (ACTIVE) {
            System.err.println(prefix() + thrown.getMessage());
        }
        thrown.printStackTrace(System.err);
    }

    static void log(String msg, Throwable thrown) {
        log(msg, thrown, false);
    }

    static void log(String msg, Throwable thrown, boolean alwaysLog) {
        if (alwaysLog || ACTIVE) {
            System.err.println(prefix()
                    + (msg == null ? "" : msg + '\t') + thrown.getMessage());
            thrown.printStackTrace(System.err);
        }
    }
}
