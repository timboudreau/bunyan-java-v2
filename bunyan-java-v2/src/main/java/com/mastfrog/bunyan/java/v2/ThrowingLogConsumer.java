package com.mastfrog.bunyan.java.v2;

/**
 *
 * @author Tim Boudreau
 */
public interface ThrowingLogConsumer<E extends Exception> {

    void accept(Log log) throws E;

}
