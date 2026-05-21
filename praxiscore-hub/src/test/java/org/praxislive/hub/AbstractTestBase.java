/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * version 3 for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License version 3
 * along with this work; if not, see http://www.gnu.org/licenses/
 *
 *
 * Please visit https://www.praxislive.org if you need additional information or
 * have any questions.
 */
package org.praxislive.hub;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

abstract class AbstractTestBase {

    public static final boolean VERBOSE = Boolean.getBoolean("praxis.test.verbose");
    public static final int TIMEOUT = Integer.getInteger("praxis.test.timeout", 10000);

    @BeforeEach
    public void beforeEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("START TEST : " + info.getDisplayName());
        }
    }

    @AfterEach
    public void afterEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("END TEST : " + info.getDisplayName());
            System.out.println("=====================================");
        }
    }

    protected void log(Object output) {
        if (VERBOSE) {
            System.out.println(output);
            System.out.println("");
        }
    }

    protected void repeatUntil(Supplier<Boolean> poll) throws TimeoutException {
        long start = System.nanoTime();
        long timeout = TimeUnit.MILLISECONDS.toNanos(TIMEOUT);
        do {
            if (poll.get()) {
                return;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException ex) {
            }
        } while (System.nanoTime() - start < timeout);
        throw new TimeoutException();
    }

}
