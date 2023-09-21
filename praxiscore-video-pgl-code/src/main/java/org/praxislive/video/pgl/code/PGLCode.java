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
package org.praxislive.video.pgl.code;

import java.util.List;
import java.util.stream.Stream;
import org.praxislive.code.CodeFactory;
import org.praxislive.code.CodeUtils;

/**
 * PGL code utility functions.
 */
public class PGLCode {

    private static final List<String> DEFAULT_IMPORTS
            = Stream.concat(CodeUtils.defaultImports().stream(),
                    Stream.of("org.praxislive.video.pgl.code.userapi.*",
                            "static org.praxislive.video.pgl.code.userapi.Constants.*"))
                    .toList();

    private static final CodeFactory.Base<P2DCodeDelegate> BASE_2D
            = CodeFactory.base(P2DCodeDelegate.class,
                    DEFAULT_IMPORTS,
                    (task, delegate) -> new P2DCodeContext(new P2DCodeConnector(task, delegate)));

    private static final CodeFactory.Base<P3DCodeDelegate> BASE_3D
            = CodeFactory.base(P3DCodeDelegate.class,
                    DEFAULT_IMPORTS,
                    (task, delegate) -> new P3DCodeContext(new P3DCodeConnector(task, delegate)));

    private PGLCode() {

    }

    /**
     * Access {@link CodeFactory.Base} for {@link P2DCodeDelegate}.
     *
     * @return code factory base for P2DCodeDelegate.
     */
    public static CodeFactory.Base<P2DCodeDelegate> base2D() {
        return BASE_2D;
    }

    /**
     * Access {@link CodeFactory.Base} for {@link P3DCodeDelegate}.
     *
     * @return code factory base for P3DCodeDelegate.
     */
    public static CodeFactory.Base<P3DCodeDelegate> base3D() {
        return BASE_3D;
    }

}
