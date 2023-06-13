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
package org.praxislive.core.services;

import java.util.Optional;
import java.util.stream.Stream;
import org.praxislive.core.ComponentAddress;
import org.praxislive.core.RootHub;

/**
 * Facility to query service addresses. Instances of this interface should be
 * acquired from {@link RootHub#getLookup()}.
 */
public interface Services {

    /**
     * Locate the primary implementation of the requested service, if available.
     *
     * @param service class of service to locate
     * @return optional of service address
     */
    public Optional<ComponentAddress> locate(Class<? extends Service> service);

    /**
     * Locate all the available implementations of the requested service, if
     * available. The primary implementation will be the first implementation in
     * the stream.
     *
     * @param service class of service to locate
     * @return stream of addresses, or empty stream
     */
    public Stream<ComponentAddress> locateAll(Class<? extends Service> service);

}
