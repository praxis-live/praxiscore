/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2020 Neil C Smith.
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

import java.util.Optional;
import org.praxislive.core.Value;

/**
 *
 */
public abstract class Table {
    
    public final static Table EMPTY = new Empty();
    
    public abstract Optional<Value> valueAt(int row, int column);
    
    public abstract int rowCount();
    
    public abstract int columnCount();
    
    private static class Empty extends Table {

        @Override
        public Optional<Value> valueAt(int row, int column) {
            return Optional.empty();
        }

        @Override
        public int rowCount() {
            return 0;
        }

        @Override
        public int columnCount() {
            return 0;
        }
        
    }
    
}
