package com.mastfrog.bunyan.java.util.logging;

import com.mastfrog.bunyan.java.v2.Log;
import com.mastfrog.bunyan.java.v2.Logs;
import com.mastfrog.util.collections.AtomicLinkedQueue;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 *
 * @author Tim Boudreau
 */
public class BunyanHandler extends Handler {

    private final AtomicReference<Consumer<LogRecord>> sink = new AtomicReference<>(new Queuer());

    @SuppressWarnings("LeakingThisInConstructor")
    public BunyanHandler() {
        HandlerRegistry.register(this);
    }

    void setLoggers(Logs loggers) {
        Consumer<LogRecord> nue = loggers == null ? new Queuer() : new Sender(loggers);
        Consumer<LogRecord> old = sink.getAndSet(nue);
        if (old instanceof Queuer) {
            Queuer q = (Queuer) old;
            for (LogRecord evt : q.drain()) {
                nue.accept(evt);
            }
        }
    }

    @Override
    public void publish(LogRecord record) {
        sink.get().accept(record);
    }

    @Override
    public void flush() {
        // do nothing
    }

    @Override
    public void close() {
        sink.getAndSet(new NoOp());
    }

    private final class Queuer implements Consumer<LogRecord> {

        private final AtomicLinkedQueue<LogRecord> cache = new AtomicLinkedQueue<>();

        @Override
        public void accept(LogRecord t) {
            cache.add(t);
        }

        List<LogRecord> drain() {
            return cache.drain();
        }
    }

    private final class Sender implements Consumer<LogRecord> {

        private final Logs loggers;

        public Sender(Logs loggers) {
            this.loggers = loggers;
        }

        private Log logFor(LogRecord record) {
            Level level = record.getLevel();
            switch (level.intValue()) {
                case 1000:
                    return loggers.error();
                case 900:
                    return loggers.warn();
                case 800:
                    return loggers.info();
                default:
                    if (level.intValue() > 1000) {
                        return loggers.fatal();
                    }
                    return loggers.debug();
            }
        }

        @Override
        public void accept(LogRecord t) {
            try ( Log log = logFor(t).add("logger", t.getLoggerName())) {
                log.add("at", Instant.ofEpochMilli(t.getMillis()));
                Object[] params = t.getParameters();
                if (params != null && params.length > 0) {
                    log.add("params", params);
                }
                if (params != null) {
                    log.add(MessageFormat.format(t.getMessage(), params));
                } else {
                    log.add(t.getMessage());
                }
                if (t.getThrown() != null) {
                    log.add(t.getThrown());
                }
            }
        }
    }

    private static final class NoOp implements Consumer<LogRecord> {

        @Override
        public void accept(LogRecord t) {
            // do nothing
        }
    }

}
