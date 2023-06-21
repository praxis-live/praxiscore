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
package org.praxislive.core.protocols;

import java.util.List;
import org.praxislive.core.ComponentType;
import org.praxislive.core.types.PArray;

/**
 * Containers can expose an instance of this interface in their lookup to
 * facilitate child container support of the supported-types control. Child
 * containers that wish to support the same component types as their parent can
 * respond to a call to the supported-types control with the result of the
 * query.
 */
public interface SupportedTypes {

    /**
     * Query the supported types, the same as calling the supported-types
     * control on the providing container. The same result will be returned
     * unless the data has changed or otherwise refreshed, so object identity
     * can be used to verify any cached data.
     *
     * @return supported types result
     */
    public Result query();

    /**
     * Supported types result to be returned from {@link #query()}.
     */
    public static final class Result {

        private final List<ComponentType> types;
        private final PArray typesAsArray;

        /**
         * Construct a Result object. The provided list will be copied if not
         * already immutable. The list must not contain null values.
         *
         * @param types supported types
         */
        public Result(List<ComponentType> types) {
            this.types = List.copyOf(types);
            this.typesAsArray = PArray.of(this.types);
        }

        /**
         * List of supported types as immutable list.
         *
         * @return supported types
         */
        public List<ComponentType> types() {
            return types;
        }

        /**
         * List of supported types as PArray for response call.
         *
         * @return supported types
         */
        public PArray typesAsArray() {
            return typesAsArray;
        }

    }

}
