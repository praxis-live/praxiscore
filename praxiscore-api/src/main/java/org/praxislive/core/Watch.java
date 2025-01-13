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
package org.praxislive.core;

import org.praxislive.core.types.PMap;

/**
 * Utilities related to Watch controls. A Watch control is a
 * {@link ControlInfo.Type#Function} control for accessing data of a component.
 * It is similar to a read-only property, but designed for data that should only
 * be calculated on demand, perhaps asynchronously.
 * <p>
 * Watch controls have a {@code watch} attribute in their info, with a map value
 * of information about the available data. The map should include the mime type
 * of the returned data. The response value type of the control will usually be
 * {@link PString} for textual mime types and {@link PBytes} for binary mime
 * types. Specifically defined private mime types may specify other value types.
 * <p>
 * Watch controls may accept an optional query argument as a map of attributes
 * for the requested data (eg. size).
 */
public final class Watch {

    /**
     * The key under which the Watch information is stored in the control info.
     */
    public static final String WATCH_KEY = "watch";

    /**
     * The key for the mime type inside the Watch information.
     */
    public static final String MIME_KEY = "mime";

    /**
     * Optional Watch information key for relating a Watch to a port. The value,
     * if present, should be the ID of a port on the component.
     */
    public static final String RELATED_PORT_KEY = "related-port";

    private Watch() {
    }

    /**
     * Create info for a simple Watch function that accepts no query argument,
     * and returns data with the provided mime type wrapped in the provided
     * response type.
     *
     * @param mimeType mime type of response
     * @param responseType value type of response
     * @return watch control info
     */
    public static ControlInfo info(String mimeType,
            Class<? extends Value> responseType) {
        return Info.control().function()
                .outputs(Info.argument().type(responseType).build())
                .property(WATCH_KEY, PMap.of(MIME_KEY, mimeType))
                .build();
    }

    /**
     * Check whether a control info reflects a Watch control.
     *
     * @param info control info
     * @return true if a Watch control
     */
    public static boolean isWatch(ControlInfo info) {
        return info.properties().get(WATCH_KEY) != null;
    }

}
