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

package com.otherpackage;

import com.mastfrog.bunyan.java.v2.Log;
import com.mastfrog.bunyan.java.v2.Logs;
import com.mastfrog.bunyan.java.v2.LogsImplTest;
import java.util.concurrent.Phaser;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Placed in a different package so caller logging will have something
 * to find in the stack that is not the package name of the library.
 *
 * @author Tim Boudreau
 */
public final class Hammerer implements Runnable {

    private final Phaser phaser;
    private final int max;
    private final Logs foo;
    private final Logs bar;
    private final Logs boo;

    public Hammerer(Phaser phaser, int max, Logs foo, Logs bar, Logs boo) {
        this.phaser = phaser;
        this.max = max;
        this.foo = foo;
        this.bar = bar;
        this.boo = boo;
    }

    @Override
    public void run() {
        String nm = Thread.currentThread().getName();
        phaser.arriveAndAwaitAdvance();
        for (int i = 0; i < max; i++) {
            try (final Log l = foo.warn()) {
                l.add("warnit").add("t", nm).add("i", i);
            }
            int ix = i;
            try {
                bar.terror((lg) -> {
                    lg.add("hooger");
                    lg.add("t", nm).add("i", ix).add("mul", ix + 3);
                    if (ix % 7 == 0) {
                        Thread.sleep(3);
                        Thread.yield();
                    }
                });
            } catch (Exception ex) {
                Logger.getLogger(LogsImplTest.class.getName()).log(Level.SEVERE, null, ex);
                return;
            }
            if (i % 31 == 0) {
                boo.error((l) -> {
                    l.add("t", nm).add("i", ix).add("booyah", -ix * 50).addLazy("now", () -> System.currentTimeMillis());
                });
            }
        }
    }

}
