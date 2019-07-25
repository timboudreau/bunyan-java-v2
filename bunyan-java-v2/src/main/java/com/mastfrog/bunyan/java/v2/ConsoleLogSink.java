package com.mastfrog.bunyan.java.v2;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class ConsoleLogSink extends AbstractLogSink {

    private final boolean disabled;

    public ConsoleLogSink(Supplier<LoggingConfig> configSupplier) {
        super(configSupplier);
        disabled = Boolean.getBoolean("disable.console.logger"); // for tests
    }

    @Override
    @JsonValue
    public String toString() {
        return "console";
    }

    @Override
    public void push(JSONContext ctx, Map<String, Object> logrecord) {
        if (disabled) {
            return;
        }
        try {
            CharSequence result = ctx.writeValueAsString(logrecord);
            assert result != null : ctx + " returned null for " + logrecord;
            System.out.println(result);
        } catch (Exception ex) {
            LoggingLogging.log("Exception writing json", ex, true);
        }
    }
}
