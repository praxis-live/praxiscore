/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2025 Neil C Smith.
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
package org.praxislive.code.userapi;

import org.praxislive.core.services.LogLevel;

/**
 *
 *
 */
public class Constants {

    private Constants() {
    }

    /**
     * Error log level.
     */
    public static final LogLevel ERROR = LogLevel.ERROR;

    /**
     * Warning log level.
     */
    public static final LogLevel WARNING = LogLevel.WARNING;

    /**
     * Info log level.
     */
    public static final LogLevel INFO = LogLevel.INFO;

    /**
     * Debug log level.
     */
    public static final LogLevel DEBUG = LogLevel.DEBUG;

    /**
     * Value of PI.
     */
    public static final double PI = Math.PI;

    /**
     * Value of PI / 2.
     */
    @Deprecated
    public static final double HALF_PI = PI / 2;

    /**
     * Value of PI / 3.
     */
    @Deprecated
    public static final double THIRD_PI = PI / 3;

    /**
     * Value of PI / 4.
     */
    @Deprecated
    public static final double QUARTER_PI = PI / 4;

    /**
     * Value of PI * 2.
     */
    @Deprecated
    public static final double TWO_PI = PI * 2;

    /**
     * Value of PI / 180.
     */
    @Deprecated
    public static final double DEG_TO_RAD = PI / 180;

    /**
     * Value of 180 / PI.
     */
    @Deprecated
    public static final double RAD_TO_DEG = 180 / PI;

    /**
     * Plain text media type - {@code text/plain}.
     */
    public static final String MIME_TEXT = "text/plain";

    /**
     * PNG image media type - {@code image/png}.
     */
    public static final String MIME_PNG = "image/png";

    /**
     * Inline SVG media type - {@code image/x.svg-html}. This is an internal
     * unregistered media type for SVG content in a format that is compatible
     * with inlining in HTML.
     */
    public static final String MIME_SVG = "image/x.svg-html";

}
