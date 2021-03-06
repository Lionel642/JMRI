package jmri.jmrit.beantable.light;

import jmri.util.JUnitUtil;

import org.junit.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Paul Bender Copyright (C) 2017
 * @author Steve Young Copyright (C) 2021
 */
public class AddEditSingleLightControlFrameTest {
    
    @Test
    public void testCTor() {
        Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        LightControlPane lcp = new LightControlPane();
        AddEditSingleLightControlFrame t = new AddEditSingleLightControlFrame(lcp,null);
        Assert.assertNotNull("exists",t);
        lcp.dispose();
    }
    
    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
    }
    
    @AfterEach
    public void tearDown() {
        JUnitUtil.tearDown();
    }
    
}
