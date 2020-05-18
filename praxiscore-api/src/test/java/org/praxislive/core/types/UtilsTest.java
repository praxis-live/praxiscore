
package org.praxislive.core.types;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 */
public class UtilsTest {
    
    @Test
    public void testDoBraced() {
        var str1 = "This is a {test}";
        var str2 = "void init() {\nSystem.out.println(\"\\{\");\n}";
        var str3 = "This is {} unbalanced {at this time";
        var str4 = "This is {}} balanced {but goes under level 0";
        var str5 = "Quote this single line string";
        var str6 = "Brace this\nmultiline\nstring";
        
        assertEquals("{" + str1 + "}", Utils.doBraced(str1));
        assertEquals("{" + str2 + "}", Utils.doBraced(str2));
        assertNull(Utils.doBraced(str3));
        assertNull(Utils.doBraced(str4));
        assertNull(Utils.doBraced(str5));
        assertEquals("{" + str6 + "}", Utils.doBraced(str6));
        
        
    }
    
    
}
