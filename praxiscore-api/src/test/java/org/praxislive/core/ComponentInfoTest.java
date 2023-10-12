
package org.praxislive.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.praxislive.core.protocols.ComponentProtocol;
import org.praxislive.core.protocols.StartableProtocol;
import org.praxislive.core.types.PMap;
import org.praxislive.core.types.PNumber;
import org.praxislive.core.types.PString;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class ComponentInfoTest {
    
    private ComponentInfo info;
    
    public ComponentInfoTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
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
    
    @After
    public void tearDown() {
        info = null;
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
