/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.praxislive.core.types;

import org.praxislive.core.types.PMap;
import org.praxislive.core.Value;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Neil C Smith <http://neilcsmith.net>
 */
public class PMapTest {

    public PMapTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of coerce method, of class PMap.
     */
    @Test
    public void testCoerce() throws Exception {
        PMap m = PMap.of("template", "public void draw(){");
        String mStr = m.toString();
        System.out.println(mStr);
        PMap m2 = PMap.parse(mStr);
        assertTrue(Utils.equivalent(m, m2));
    }

    @Test
    public void testParse() throws Exception {
        PMap m1 = PMap.builder(4)
                .put("key1", "value1")
                .put("key2", 2)
                .put("key3", true)
                .put("key4", 13.66)
                .build();
        PMap m2 = PMap.parse("key1 value1 key2 2 key3 true key4 13.66");
        PMap m3 = PMap.parse(
                "key1 value1;\n"
                + "key2 2  \n"
                + "key3\ttrue\n"
                + "# this is a comment \n"
                + "key4 {13.66}"
        );
        assertTrue(m1.equivalent(m2));
        assertTrue(m2.equivalent(m3));
        assertEquals(13.66, m3.getDouble("key4", 0), 0.001);
    }

}
