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
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
public class ComponentInfoTest {
    
    private ComponentInfo info;
    
    public ComponentInfoTest() {
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
        
        info = ComponentInfo.create(controls, ports, interfaces, properties);
    }
    
    /**
     * Test of from method, of class ComponentInfo.
     */
    @Test
    public void testFrom() throws Exception {
        System.out.println("From");
        String ci = info.toString();
        System.out.println(ci);
        ComponentInfo info2 = ComponentInfo.from(PString.of(ci)).orElseThrow();
        System.out.println(info2);
        System.out.println(info.controlInfo("p1"));
        System.out.println(info2.controlInfo("p1"));
        System.out.println(info.controlInfo("ro1"));
        System.out.println(info2.controlInfo("ro1"));
//        assertTrue(Value.equivalent(Value.class, info.getControlInfo("p1").getOutputsInfo()[0], info2.getControlInfo("p1").getOutputsInfo()[0]));
//        assertTrue(Value.equivalent(Value.class, info.getControlInfo("ro1").getOutputsInfo()[0], info2.getControlInfo("ro1").getOutputsInfo()[0]));
//        assertTrue(Value.equivalent(Value.class, info, info2));
        assertTrue(info.equivalent(info2));
        assertTrue(info.controlInfo("p1").equivalent(info2.controlInfo("p1")));
//        assertTrue(info.controlInfo("ro1").getOutputsInfo()[0].equivalent(info2.controlInfo("ro1").getOutputsInfo()[0]));
        assertTrue(info.controlInfo("ro1").outputs().get(0).equivalent(info2.controlInfo("ro1").outputs().get(0)));
        assertTrue(info.portInfo("in").equivalent(info2.portInfo("in")));
    }
   
}
