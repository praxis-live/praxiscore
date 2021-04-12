/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2021 Neil C Smith.
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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 *
 */
class ChildRegistry {

    static final ChildRegistry INSTANCE = new ChildRegistry();
    
    private final Set<Process> processes;
    
    private ChildRegistry() {
        processes = new HashSet<>();
        Runtime.getRuntime().addShutdownHook(new Thread(this::terminate));
    }
    
    synchronized void add(Process child) {
        processes.add(child);
    }
    
    synchronized void remove(Process child) {
        processes.remove(child);
    }
    
    private synchronized void terminate() {
        processes.forEach(this::terminate);
        processes.clear();
    }
    
    private void terminate(Process process) {
        if (process.isAlive()) {
            boolean exited = false;
            process.destroy();
            try {
                exited = process.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
            }
            if (!exited) {
                process.destroyForcibly();
            }
        }
        
    }
    
    
}
