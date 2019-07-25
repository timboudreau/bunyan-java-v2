/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mastfrog.bunyan.java.v2;

import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.util.preconditions.Exceptions;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A factory for log records; for each level, a Log instance may be created and
 * either used try-with-resources style, or using one of the closure accepting
 * methods (in which case, close() is called automatically on exiting the
 * closure).
 *
 * @author Tim Boudreau
 */
public interface Logs {

    /**
     * For constructing static Logs instances. These instances will cache
     * records until either the first LoggingConfig is constructed and becomes
     * the default, or until a threshold number of Log instances have been
     * created and closed, at which point the system decides to initialize
     * logging from system properties (in general, you should create a
     * LoggingConfig instance).
     *
     * @param name The name Log records generated will use
     * @return A Logs
     */
    public static Logs named(String name) {
        return new DelayedDelegationLogs(name);
    }

    default Logs contextual() {
        if (LogContext.isDefaultContext()) {
            return this;
        }
        String name = LogContext.current();
        if (this instanceof DelayedDelegationLogs && name != null
                && name.equals(((DelayedDelegationLogs) this).name)) {
            return this;
        }
        return named(name);
    }

    /**
     * Create a log record builder with <i>trace</i> level logging, which will
     * log its contents when its <code>close()</code> method is called (use
     * try-with-resources to guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A trace-level log record builder
     */
    Log trace();

    /**
     * Create a log record builder with <i>debug</i> level logging, which will
     * log its contents when its <code>close()</code> method is called (use
     * try-with-resources to guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A trace-level log record builder
     */
    Log debug();

    /**
     * Create a log record builder with <i>info</i> level logging, which will
     * log its contents when its <code>close()</code> method is called (use
     * try-with-resources to guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A trace-level log record builder
     */
    Log info();

    /**
     * Create a log record builder with <i>warn</i> level logging, which will
     * log its contents when its <code>close()</code> method is called (use
     * try-with-resources to guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A trace-level log record builder
     */
    Log warn();

    /**
     * Create a log record builder with <i>error</i> level logging, which will
     * log its contents when its <code>close()</code> method is called (use
     * try-with-resources to guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A trace-level log record builder
     */
    Log error();

    /**
     * Create a log record builder with <i>fatal</i> level logging, which will
     * log its contents when its <code>close()</code> method is called (use
     * try-with-resources to guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A trace-level log record builder
     */
    Log fatal();

    /**
     * Create a log record builder with <i>trace</i> level logging and its
     * message set to the passed (non-null) message, which will log its contents
     * when its <code>close()</code> method is called (use try-with-resources to
     * guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A trace-level log record builder
     */
    default Log trace(String msg) {
        return trace().message(msg);
    }

    /**
     * Create a log record builder with <i>debug</i> level logging and its
     * message set to the passed (non-null) message, which will log its contents
     * when its <code>close()</code> method is called (use try-with-resources to
     * guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A debug-level log record builder
     */
    default Log debug(String msg) {
        return debug().message(msg);
    }

    /**
     * Create a log record builder with <i>info</i> level logging and its
     * message set to the passed (non-null) message, which will log its contents
     * when its <code>close()</code> method is called (use try-with-resources to
     * guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A info-level log record builder
     */
    default Log info(String msg) {
        return info().message(msg);
    }

    /**
     * Create a log record builder with <i>warn</i> level logging and its
     * message set to the passed (non-null) message, which will log its contents
     * when its <code>close()</code> method is called (use try-with-resources to
     * guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A warn-level log record builder
     */
    default Log warn(String msg) {
        return warn().message(msg);
    }

    /**
     * Create a log record builder with <i>error</i> level logging and its
     * message set to the passed (non-null) message, which will log its contents
     * when its <code>close()</code> method is called (use try-with-resources to
     * guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A error-level log record builder
     */
    default Log error(String msg) {
        return error().message(msg);
    }

    /**
     * Create a log record builder with <i>fatal</i> level logging and its
     * message set to the passed (non-null) message, which will log its contents
     * when its <code>close()</code> method is called (use try-with-resources to
     * guarantee <code>close()</code> is called, e.g.
     * <code>try (Log log = SOME_LOGS.trace()) { ... }</code>.
     *
     * @return A trace-level log record builder
     */
    default Log fatal(String msg) {
        return fatal().message(msg);
    }

    /**
     * Create a child instance of this log record factory, which will create Log
     * records pre-populated with the passed map's contents.
     *
     * @param pairs Some key/value pairs to include in all records created by
     * the returned instance
     * @return A new Logs instance which incorporates the passed arguments into
     * all log records it creates
     */
    Logs child(Map<String, Object> pairs);

    /**
     * Create a child instance of this log record factory, which will create Log
     * records pre-populated with the passed map's contents.
     *
     * @param key - A key
     * @param value - A value
     * @return A new Logs instance which incorporates the passed arguments into
     * all log records it creates
     */
    default Logs child(String key, Object value) {
        return child(AbstractSingletonMap.ofObject(key, value));
    }

    /**
     * Create a child instance of this log record factory, which will create Log
     * records pre-populated with the passed map's contents.
     *
     * @param key - A key
     * @param value - A value
     * @return A new Logs instance which incorporates the passed arguments into
     * all log records it creates
     */
    default Logs child(String key, int val) {
        return child(AbstractSingletonMap.ofInt(key, val));
    }

    /**
     * Create a child instance of this log record factory, which will create Log
     * records pre-populated with the passed map's contents.
     *
     * @param key - A key
     * @param value - A value
     * @return A new Logs instance which incorporates the passed arguments into
     * all log records it creates
     */
    default Logs child(String key, long val) {
        return child(AbstractSingletonMap.ofLong(key, val));
    }

    /**
     * Perform trace-level logging, creating a new log record and closing it
     * when the passed consumer exits.
     *
     * @param c A consumer
     * @return this
     */
    default Logs trace(Consumer<Log> c) {
        try (Log log = trace()) {
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    /**
     * Perform trace-level logging, creating a new log record and closing it
     * when the passed consumer exits.
     *
     * @param c A function
     * @return the result of the function
     */
    default <T> T trace(Function<Log, T> c) {
        try (Log log = trace()) {
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    /**
     * Perform trace-level logging, creating a new log record and closing it
     * when the passed consumer exits.
     *
     * @param c A consumer
     * @return the result of the function
     */
    default <T> T ttrace(ThrowingFunction<Log, T> c) {
        try (Log log = trace()) {
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    /**
     * Perform trace-level logging, creating a new log record and closing it
     * when the passed consumer exits.
     *
     * @param c A consumer which may throw an exception
     * @return this
     */
    default <E extends Exception> Logs ttrace(ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = trace()) {
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    /**
     * Perform trace-level logging, creating a new log record and closing it
     * when the passed consumer exits.
     *
     * @param msg The log message
     * @param c A consumer
     * @return the result of the function
     */
    default <E extends Exception, T> T ttrace(String msg, ThrowingLogFunction<E, T> c) {
        try (Log log = trace()) {
            log.message(msg);
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    /**
     * Perform trace-level logging, setting the passed string as the message of
     * the log record, creating a new log record and closing it when the passed
     * consumer exits.
     *
     * @param msg The log message (non-null)
     * @param c A consumer
     * @return this
     */
    default Logs trace(String msg, Consumer<Log> c) {
        try (Log log = trace()) {
            log.message(msg);
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs ttrace(String msg, ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = trace()) {
            log.message(msg);
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default Logs debug(Consumer<Log> c) {
        try (Log log = debug()) {
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs tdebug(ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = debug()) {
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default Logs debug(String msg, Consumer<Log> c) {
        try (Log log = debug()) {
            log.message(msg);
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs tdebug(String msg, ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = debug()) {
            log.message(msg);
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <R> R debug(Function<Log, R> c) {
        try (Log log = debug()) {
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R tdebug(ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = debug()) {
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <R> R debug(String msg, Function<Log, R> c) {
        try (Log log = debug()) {
            log.message(msg);
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R tdebug(String msg, ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = debug()) {
            log.message(msg);
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default Logs info(Consumer<Log> c) {
        try (Log log = info()) {
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs tinfo(ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = info()) {
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default Logs info(String msg, Consumer<Log> c) {
        try (Log log = info()) {
            log.message(msg);
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs tinfo(String msg, ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = info()) {
            log.message(msg);
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <R> R info(Function<Log, R> c) {
        try (Log log = info()) {
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R tinfo(ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = info()) {
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <R> R info(String msg, Function<Log, R> c) {
        try (Log log = info()) {
            log.message(msg);
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R tinfo(String msg, ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = info()) {
            log.message(msg);
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default Logs warn(Consumer<Log> c) {
        try (Log log = warn()) {
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
            c.accept(log);
        }
        return this;
    }

    default <E extends Exception> Logs twarn(ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = warn()) {
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default Logs warn(String msg, Consumer<Log> c) {
        try (Log log = warn()) {
            log.message(msg);
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs twarn(String msg, ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = warn()) {
            log.message(msg);
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <R> R warn(Function<Log, R> c) {
        try (Log log = warn()) {
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R twarn(ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = warn()) {
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <R> R warn(String msg, Function<Log, R> c) {
        try (Log log = warn()) {
            log.message(msg);
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R twarn(String msg, ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = warn()) {
            log.message(msg);
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default Logs fatal(Consumer<Log> c) {
        try (Log log = fatal()) {
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs tfatal(ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = fatal()) {
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default Logs fatal(String msg, Consumer<Log> c) {
        try (Log log = fatal()) {
            log.message(msg);
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs tfatal(String msg, ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = fatal()) {
            log.message(msg);
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <R> R fatal(Function<Log, R> c) {
        try (Log log = fatal()) {
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R tfatal(ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = fatal()) {
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <R> R fatal(String msg, Function<Log, R> c) {
        try (Log log = fatal()) {
            log.message(msg);
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R tfatal(String msg, ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = fatal()) {
            log.message(msg);
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default Logs error(Consumer<Log> c) {
        try (Log log = error()) {
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs terror(ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = error()) {
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default Logs error(String msg, Consumer<Log> c) {
        try (Log log = error()) {
            log.message(msg);
            try {
                c.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <E extends Exception> Logs terror(String msg, ThrowingLogConsumer<E> throwingConsumer) throws E {
        try (Log log = error()) {
            log.message(msg);
            try {
                throwingConsumer.accept(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
        return this;
    }

    default <R> R error(Function<Log, R> c) {
        try (Log log = error()) {
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R terror(ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = error()) {
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <R> R error(String msg, Function<Log, R> c) {
        try (Log log = error()) {
            log.message(msg);
            try {
                return c.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }

    default <E extends Exception, R> R terror(String msg, ThrowingLogFunction<E, R> throwingConsumer) throws E {
        try (Log log = error()) {
            log.message(msg);
            try {
                return throwingConsumer.apply(log);
            } catch (Exception e) {
                log.add(e);
                return Exceptions.chuck(e);
            }
        }
    }
}
