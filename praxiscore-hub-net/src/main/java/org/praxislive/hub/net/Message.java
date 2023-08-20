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
package org.praxislive.hub.net;

import java.util.List;
import java.util.Objects;
import org.praxislive.core.ControlAddress;
import org.praxislive.core.Value;
import org.praxislive.core.types.PMap;

/**
 *
 */
sealed interface Message {

    public int matchID();

    public PMap data();

    record Send(int matchID,
            ControlAddress to,
            ControlAddress from,
            List<Value> args,
            PMap data) implements Message {

        public Send     {
            Objects.requireNonNull(to);
            Objects.requireNonNull(from);
            args = List.copyOf(args);
            Objects.requireNonNull(data);
        }

        public Send(int matchID,
                ControlAddress to,
                ControlAddress from,
                List<Value> args) {
            this(matchID, to, from, args, PMap.EMPTY);
        }

    }

    record Service(int matchID,
            String service,
            String control,
            ControlAddress from,
            List<Value> args,
            PMap data) implements Message {

        public Service      {
            Objects.requireNonNull(service);
            Objects.requireNonNull(control);
            Objects.requireNonNull(from);
            args = List.copyOf(args);
            Objects.requireNonNull(data);
        }

        public Service(int matchID,
                String service,
                String control,
                ControlAddress from,
                List<Value> args) {
            this(matchID, service, control, from, args, PMap.EMPTY);
        }

    }

    record Reply(int matchID, List<Value> args, PMap data) implements Message {

        public Reply   {
            args = List.copyOf(args);
            Objects.requireNonNull(data);
        }

        public Reply(int matchID, List<Value> args) {
            this(matchID, args, PMap.EMPTY);
        }

    }

    record Error(int matchID, List<Value> args, PMap data) implements Message {

        public Error   {
            args = List.copyOf(args);
            Objects.requireNonNull(data);
        }

        public Error(int matchID, List<Value> args) {
            this(matchID, args, PMap.EMPTY);
        }

    }

    record System(int matchID, String type, PMap data) implements Message {

        public System   {
            Objects.requireNonNull(type);
            Objects.requireNonNull(data);
        }

    }

}
