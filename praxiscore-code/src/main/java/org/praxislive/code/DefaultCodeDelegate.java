/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2023 Neil C Smith.
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
package org.praxislive.code;

import java.util.List;
import java.util.Random;

/**
 * Default base for code delegates providing a variety of functions.
 */
public class DefaultCodeDelegate extends CodeDelegate implements DefaultDelegateAPI {

    final static List<String> DEFAULT_IMPORTS = List.of(
        "java.util.*",
        "java.util.function.*",
        "java.util.stream.*",
        "org.praxislive.core.*",
        "org.praxislive.core.types.*",
        "org.praxislive.code.userapi.*",
        "static org.praxislive.code.userapi.Constants.*"
    );

    protected final Random RND;

    public DefaultCodeDelegate() {
        RND = new Random();
    }

    /**
     * Return a random number between zero and max (exclusive)
     *
     * @param max the upper bound of the range
     * @return
     */
    @Override
    public final double random(double max) {
        return RND.nextDouble() * max;
    }

    /**
     * Return a random number between min (inclusive) and max (exclusive)
     *
     * @param min the lower bound of the range
     * @param max the upper bound of the range
     * @return
     */
    @Override
    public final double random(double min, double max) {
        if (min >= max) {
            return min;
        }
        return random(max - min) + min;
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    @Override
    public final double randomOf(double... values) {
        return values[RND.nextInt(values.length)];
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    @Override
    public final int randomOf(int... values) {
        return values[RND.nextInt(values.length)];
    }

    /**
     * Return a random element from an array of values.
     *
     * @param values list of values, may not be empty
     * @return random element
     */
    @Override
    public final String randomOf(String... values) {
        return values[RND.nextInt(values.length)];
    }

}
