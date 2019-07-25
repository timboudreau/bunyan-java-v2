/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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

import com.fasterxml.jackson.annotation.JsonValue;
import com.mastfrog.util.preconditions.ConfigurationError;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
final class LogLevel implements Comparable<LogLevel> {

    private final String name;
    private final int intValue;
    static final int FATAL_LEVEL = 60;
    static final int ERROR_LEVEL = 50;
    static final int WARN_LEVEL = 40;
    static final int INFO_LEVEL = 30;
    static final int DEBUG_LEVEL = 20;
    static final int TRACE_LEVEL = 10;
    static final String FATAL_NAME = "fatal";
    static final String ERROR_NAME = "error";
    static final String WARN_NAME = "warn";
    static final String INFO_NAME = "info";
    static final String DEBUG_NAME = "debug";
    static final String TRACE_NAME = "trace";
    static final LogLevel FATAL = new LogLevel(FATAL_NAME, FATAL_LEVEL);
    static final LogLevel ERROR = new LogLevel(ERROR_NAME, ERROR_LEVEL);
    static final LogLevel WARN = new LogLevel(WARN_NAME, WARN_LEVEL);
    static final LogLevel INFO = new LogLevel(INFO_NAME, INFO_LEVEL);
    static final LogLevel DEBUG = new LogLevel(DEBUG_NAME, DEBUG_LEVEL);
    static final LogLevel TRACE = new LogLevel(TRACE_NAME, TRACE_LEVEL);
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");

    public static LogLevel valueOf(String level) {
        if (DIGITS.matcher(level).matches()) {
            return valueOf(Integer.parseInt(level));
        }
        switch (level.toLowerCase()) {
            case FATAL_NAME:
                return FATAL;
            case ERROR_NAME:
                return ERROR;
            case WARN_NAME:
            case "warning":
                return WARN;
            case INFO_NAME:
                return INFO;
            case DEBUG_NAME:
                return DEBUG;
            case TRACE_NAME:
                return TRACE;
            default:
                throw new ConfigurationError("Not one of fatal, error, "
                        + "warn, info, debug, trace: '" + level + "'");
        }
    }

    public static LogLevel valueOf(int val) {
        switch (val) {
            case FATAL_LEVEL:
                return FATAL;
            case ERROR_LEVEL:
                return ERROR;
            case WARN_LEVEL:
                return WARN;
            case INFO_LEVEL:
                return INFO;
            case DEBUG_LEVEL:
                return DEBUG;
            case TRACE_LEVEL:
                return TRACE;
            default:
                if (val > FATAL_LEVEL) {
                    return new LogLevel("none", val);
                } else if (val > ERROR_LEVEL) {
                    return ERROR;
                } else if (val > WARN_LEVEL) {
                    return WARN;
                } else if (val > INFO_LEVEL) {
                    return INFO;
                } else if (val > DEBUG_LEVEL) {
                    return DEBUG;
                } else {
                    return TRACE;
                }
        }
    }

    LogLevel(String name, int intValue) {
        this.intValue = intValue;
        this.name = name;
    }

    public final int intValue() {
        return intValue;
    }

    @JsonValue
    public final String name() {
        return name;
    }

    @Override
    public final String toString() {
        return name + "(" + intValue + ")";
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        } else if (o instanceof LogLevel) {
            LogLevel ll = (LogLevel) o;
            return ll.intValue == intValue;
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return intValue * 71;
    }

    @Override
    public final int compareTo(LogLevel o) {
        return Integer.compare(intValue, o.intValue);
    }

    boolean isSevere() {
        return intValue >= ERROR_LEVEL;
    }
}
