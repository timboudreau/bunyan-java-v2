/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mastfrog.bunyan.java.v2;

import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractLogSink implements LogSink {

    private final Supplier<LoggingConfig> configSupplier;
    private AbstractLogSink asyncInstance;

    public AbstractLogSink(Supplier<LoggingConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    public AbstractLogSink(LoggingConfig config) {
        this(new LSup(config));
    }

    protected Supplier<LoggingConfig> configSupplier() {
        return configSupplier;
    }

    static final class LSup implements Supplier<LoggingConfig> {

        private final LoggingConfig cfig;

        public LSup(LoggingConfig cfig) {
            this.cfig = cfig;
        }

        @Override
        public LoggingConfig get() {
            return cfig;
        }
    }
}
