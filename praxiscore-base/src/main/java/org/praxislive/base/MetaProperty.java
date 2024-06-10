/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2024 Neil C Smith.
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
package org.praxislive.base;

import java.util.List;
import org.praxislive.core.Call;
import org.praxislive.core.Control;
import org.praxislive.core.PacketRouter;
import org.praxislive.core.Value;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.types.PMap;

/**
 * Simple implementation of {@link ComponentProtocol#META} property, with linked
 * {@link ComponentProtocol#META_MERGE} control.
 */
public final class MetaProperty implements Control {

    private final MergeControl mergeControl;

    private PMap data;
    private long latest;

    public MetaProperty() {
        mergeControl = new MergeControl();
        this.data = PMap.EMPTY;
    }

    @Override
    public void call(Call call, PacketRouter router) throws Exception {
        if (call.isRequest()) {
            List<Value> args = call.args();
            int argCount = args.size();
            long time = call.time();
            if (argCount > 0) {
                if (argCount > 1) {
                    throw new IllegalArgumentException("Too many arguments");
                }
                if (isLatest(time)) {
                    PMap value = PMap.from(args.get(0))
                            .orElseThrow(IllegalArgumentException::new);
                    if (data.isEmpty()) {
                        data = value;
                    } else if (value.isEmpty()) {
                        data = PMap.EMPTY;
                    } else {
                        throw new IllegalStateException("Current or new value must be empty");
                    }
                    setLatest(time);
                }
            }
            if (call.isReplyRequired()) {
                router.route(call.reply(data));
            }
        }
    }

    /**
     * Get the merge control linked to this property.
     *
     * @return merge control
     */
    public Control getMergeControl() {
        return mergeControl;
    }

    /**
     * Get the current value.
     *
     * @return current value
     */
    public PMap getValue() {
        return data;
    }

    private void setLatest(long time) {
        latest = time == 0 ? -1 : time;
    }

    private boolean isLatest(long time) {
        return latest == 0 || (time - latest) >= 0;
    }

    private class MergeControl implements Control {

        @Override
        public void call(Call call, PacketRouter router) throws Exception {
            if (call.isRequest()) {
                List<Value> args = call.args();
                if (args.size() != 1) {
                    throw new IllegalArgumentException("Wrong number of arguments");
                }
                PMap value = PMap.from(args.get(0))
                        .orElseThrow(IllegalArgumentException::new);
                long time = call.time();
                // @TODO check time per key
                if (isLatest(time)) {
                    if (data.isEmpty()) {
                        data = value;
                    } else {
                        data = PMap.merge(data, value, PMap.REPLACE);
                    }
                    setLatest(time);
                }
                if (call.isReplyRequired()) {
                    router.route(call.reply(data));
                }
            }
        }

    }

}
