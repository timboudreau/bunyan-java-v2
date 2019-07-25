package com.mastfrog.bunyan.java.v2;

/**
 *
 * @author Tim Boudreau
 */
public interface ThrowingLogFunction<E extends Exception, R> {

    R apply(Log log) throws E;

}
