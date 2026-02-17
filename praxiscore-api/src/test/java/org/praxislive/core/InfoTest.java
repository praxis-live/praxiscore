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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.types.PBoolean;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

/**
 *
 */
public class InfoTest {
    
    private ComponentInfo compareInfo;
    
    public InfoTest() {
    }
    
    @BeforeEach
    public void setUp() {
        Set<String> interfaces = new LinkedHashSet<>(2);
        interfaces.add(Protocol.Type.of(ComponentProtocol.class).name());
        interfaces.add(Protocol.Type.of(StartableProtocol.class).name());
        
        Map<String, ControlInfo> controls = new LinkedHashMap<>();
        controls.put("p1", ControlInfo.createPropertyInfo(PNumber.info(0, 1), PNumber.ONE, PMap.of(ControlInfo.KEY_TRANSIENT, true)));
        controls.put("p2", ControlInfo.createPropertyInfo(ArgumentInfo.of(PString.class, PMap.of("template", "public void draw(){")), PString.EMPTY, PMap.EMPTY));
        controls.put("ro1", ControlInfo.createReadOnlyPropertyInfo(PNumber.info(0, 1), PMap.EMPTY));
        controls.put("t1", ControlInfo.createActionInfo(PMap.of("key", "value")));
        
        Map<String, PortInfo> ports = new LinkedHashMap<>();
        ports.put("in", PortInfo.create(ControlPort.class, PortInfo.Direction.IN, PMap.EMPTY));
        ports.put("out", PortInfo.create(ControlPort.class, PortInfo.Direction.OUT, PMap.EMPTY));
        
        PMap properties = PMap.of(ComponentInfo.KEY_DYNAMIC, true);
        
        compareInfo = ComponentInfo.create(controls, ports, interfaces, properties);
    }
    
    @Test
    public void testCreate() {
        ComponentInfo info = Info.component(cmp -> cmp
                .protocol(ComponentProtocol.class)
                .protocol(StartableProtocol.class)
                .control("p1", c -> c.property()
                        .input(a -> a.number().min(0).max(1)).defaultValue(PNumber.ONE)
                        .property(ControlInfo.KEY_TRANSIENT, PBoolean.TRUE))
                .control("p2", c -> c.property()
                        .input(a -> a.string().template("public void draw(){"))
                        .defaultValue(PString.EMPTY))
                .control("ro1", c -> c.readOnlyProperty()
                        .output(a -> a.number().min(0).max(1)))
                .control("t1", c -> c.action().property("key", PString.of("value")))
                .port("in", p -> p.input(ControlPort.class))
                .port("out", p -> p.output(ControlPort.class))
                .property(ComponentInfo.KEY_DYNAMIC, PBoolean.TRUE)
                
        );
        
        System.out.println(info);
        System.out.println(compareInfo);
        assertEquals(compareInfo, info);
        
        ComponentInfo info2 = Info.component(cmp -> cmp.merge(info));
        assertEquals(info, info2);
    }
    
}
