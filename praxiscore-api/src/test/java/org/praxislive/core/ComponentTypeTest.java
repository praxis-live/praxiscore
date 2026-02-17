/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2026 Neil C Smith.
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 *
 * 
 */
public class ComponentTypeTest {

    public ComponentTypeTest() {
    }

    /**
     * Test of create method, of class ComponentType.
     */
    @Test
    public void testCreate() {
        System.out.println("create");
        String[] types = new String[] {
            "root:audio",
            "core:test-this_one",
            " root:audio",
            "_root:audio",
            "root:audio:",
            "Not a valid type",
            "core:test&this",
            "notvalid",
            "core:array:random"
        };
        boolean[] allowed = new boolean[] {
            true, true, false, false, false, false, false, false, true
        };
        for (int i=0; i < types.length; i++) {
            try {
                System.out.println("Testing - " + types[i]);
                System.out.println("Created Type - "
                        + ComponentType.parse(types[i]));
                if (! allowed[i] ) {
                    fail("Illegal type allowed through");
                }
            } catch (Exception ex) {
                if (allowed[i]) {
                    fail("Allowed type failed");
                }
            }
        }
    }

}
