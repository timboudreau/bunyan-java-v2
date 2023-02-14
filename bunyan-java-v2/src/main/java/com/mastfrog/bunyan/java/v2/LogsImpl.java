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

import static com.mastfrog.bunyan.java.v2.LogLevel.DEBUG;
import static com.mastfrog.bunyan.java.v2.LogLevel.ERROR;
import static com.mastfrog.bunyan.java.v2.LogLevel.FATAL;
import static com.mastfrog.bunyan.java.v2.LogLevel.INFO;
import static com.mastfrog.bunyan.java.v2.LogLevel.TRACE;
import static com.mastfrog.bunyan.java.v2.LogLevel.WARN;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
class LogsImpl implements Logs {

    private final String name;

    private final LoggingConfig config;

    LogsImpl(String name, LoggingConfig config) {
        this.name = name;
        this.config = config;
    }

    LogImpl newLog(LogLevel level) {
        return new LogImpl(name, level, config);
    }

    public String name() {
        return name;
    }

    @Override
    public Logs contextual() {
        if (LogContext.isDefaultContext()) {
            return this;
        }
        String name = LogContext.current();
        if (!this.name.equals(name)) {
            return new LogsImpl(name, config);
        }
        return this;
    }

    @Override
    public Logs child(Map<String, Object> pairs) {
        return new ChildLogs(name, config, pairs);
    }

    @Override
    public String toString() {
        return name;
    }

    static final class ChildLogs extends LogsImpl {

        private final Map<String, Object> pairs;

        ChildLogs(String name, LoggingConfig config, Map<String, Object> pairs) {
            super(name, config);
            this.pairs = pairs;
        }

        @Override
        LogImpl newLog(LogLevel level) {
            LogImpl result = super.newLog(level);
            result.add(pairs);
            return result;
        }
    }

    @Override
    public Log trace() {
        return newLog(TRACE);
    }

    @Override
    public Log debug() {
        return newLog(DEBUG);
    }

    @Override
    public Log info() {
        return newLog(INFO);
    }

    @Override
    public Log warn() {
        return newLog(WARN);
    }

    @Override
    public Log fatal() {
        return newLog(FATAL);
    }

    @Override
    public Log error() {
        return newLog(ERROR);
    }
}
