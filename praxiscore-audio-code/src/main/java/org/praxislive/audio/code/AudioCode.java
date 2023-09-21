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
package org.praxislive.audio.code;

import java.util.List;
import java.util.stream.Stream;
import org.praxislive.code.CodeFactory;
import org.praxislive.code.CodeUtils;

/**
 * Audio code utility functions.
 */
public class AudioCode {

    private static final List<String> DEFAULT_IMPORTS
            = Stream.concat(CodeUtils.defaultImports().stream(),
                    Stream.of("org.jaudiolibs.pipes.*",
                            "org.jaudiolibs.pipes.units.*",
                            "org.praxislive.audio.code.userapi.*",
                            "static org.praxislive.audio.code.userapi.AudioConstants.*"))
            .toList();

    private static final CodeFactory.Base<AudioCodeDelegate> BASE
            = CodeFactory.base(AudioCodeDelegate.class,
                    DEFAULT_IMPORTS,
                    (task, delegate) -> new AudioCodeContext(new AudioCodeConnector(task, delegate)));
    
    private AudioCode() {

    }
    
    /**
     * Access {@link CodeFactory.Base} for {@link AudioCodeDelegate}.
     *
     * @return code factory base for AudioCodeDelegate.
     */
    public static CodeFactory.Base<AudioCodeDelegate> base() {
        return BASE;
    }
}
