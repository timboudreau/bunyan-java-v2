/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
