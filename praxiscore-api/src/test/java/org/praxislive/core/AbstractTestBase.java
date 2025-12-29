package org.praxislive.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

public abstract class AbstractTestBase {
    
    private static final boolean VERBOSE = Boolean.getBoolean("praxis.test.verbose");
    
    @BeforeEach
    public void beforeEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("START TEST : " + info.getDisplayName());
        }
    }

    @AfterEach
    public void afterEach(TestInfo info) {
        if (VERBOSE) {
            System.out.println("END TEST : " + info.getDisplayName());
            System.out.println("=====================================");
        }
    }

    protected void log(Object output) {
        if (VERBOSE) {
            System.out.println(output);
            System.out.println("");
        }
    }
    
}
