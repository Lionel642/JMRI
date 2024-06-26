package jmri.jmrix.loconet;

import jmri.Sensor;
import jmri.SensorManager;
import jmri.util.JUnitUtil;

import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for the jmri.jmrix.loconet.LnSensorManagerTurnout class.
 *
 * @author Bob Jacobsen Copyright 2001
 */
public class LnSensorManagerTest extends jmri.managers.AbstractSensorMgrTestBase {

    private LocoNetInterfaceScaffold lnis = null;

    @Override
    public String getSystemName(int i) {
        return "LS" + i;
    }

    @Test
    public void testLnSensorCreate() {
        Assert.assertNotNull("exists", l);
    }

    @Test
    public void testByAddress() {
        // sample turnout object
        Sensor t = l.newSensor("LS22", "test");

        // test get
        Assert.assertTrue(t == l.getByUserName("test"));
        Assert.assertTrue(t == l.getBySystemName("LS22"));
    }

    @Test
    @Override
    public void testMisses() {
        // sample turnout object
        Sensor t = l.newSensor("LS22", "test");
        Assert.assertNotNull("exists", t);

        // try to get nonexistant turnouts
        Assert.assertNull( l.getByUserName("foo"));
        Assert.assertNull( l.getBySystemName("bar"));
    }

    @Test
    public void testLocoNetMessages() {
        // send messages for 21, 22
        // notify the Ln that somebody else changed it...
        LocoNetMessage m1 = new LocoNetMessage(4);
        m1.setOpCode(0xb2);         // OPC_INPUT_REP
        m1.setElement(1, 0x15);     // all but lowest bit of address
        m1.setElement(2, 0x60);     // Aux (low addr bit high), sensor high
        m1.setElement(3, 0x38);
        lnis.sendTestMessage(m1);

        // see if sensor exists
        Assert.assertNotNull( l.getBySystemName("LS44"));
    }

    @Test
    public void testAsAbstractFactory() {
        // ask for a Sensor, and check type
        SensorManager t = jmri.InstanceManager.sensorManagerInstance();

        Sensor o = t.newSensor("LS21", "my name");

        if (log.isDebugEnabled()) {
            log.debug("received sensor value {}", o);
        }
        
        Assert.assertNotNull(o);
        Assert.assertTrue(o instanceof LnSensor );

        // make sure loaded into tables

        Assert.assertNotNull( t.getBySystemName("LS21"));
        Assert.assertNotNull( t.getByUserName("my name"));

    }

    @Test
    public void testDeprecationWarningSensorNumberFormat() {
        boolean excep= false;
        String s = "";
        try {
            s = l.createSystemName("3:5", "L");
        } catch (jmri.JmriException e) {
            excep = true;
        }
        Assert.assertEquals("no exception during createSystemName for arguments '3:5', 'L'", false, excep);
        Assert.assertEquals("check createSystemName for arguments '3:5', 'L'", "LS37", s);
        jmri.util.JUnitAppender.assertWarnMessage(
                "LnSensorManager.createSystemName(curAddress, prefix) support for curAddress using the '3:5' format is deprecated as of JMRI 4.17.4 and will be removed in a future JMRI release.  Use the curAddress format '37' instead.");
    }

    @Test
    public void testSetGetRestingTime() {
        Assert.assertEquals("check default resting time", 1250, ((LnSensorManager)l).getRestingTime());

        ((LnSensorManager)l).setRestingTime(600);
        Assert.assertEquals("check 1st set of resting time", 600, ((LnSensorManager)l).getRestingTime());
        
        ((LnSensorManager)l).setRestingTime(500);
        Assert.assertEquals("check 2nd set of resting time", 500, ((LnSensorManager)l).getRestingTime());
        
        ((LnSensorManager)l).setRestingTime(499);
        Assert.assertEquals("check 1st range check on set of resting time", 500, ((LnSensorManager)l).getRestingTime());
        
        ((LnSensorManager)l).setRestingTime(200001);
        Assert.assertEquals("check 2nd range check on set of resting time", 200000, ((LnSensorManager)l).getRestingTime());
        
    }

    @Override
    @BeforeEach
    public void setUp() {
        JUnitUtil.setUp();
        // prepare an interface
        LocoNetSystemConnectionMemo memo = new LocoNetSystemConnectionMemo();
        lnis = new LocoNetInterfaceScaffold(memo);
        memo.setLnTrafficController(lnis);
        Assert.assertNotNull("exists", lnis);

        // create and register the manager object
        l = new LnSensorManager(memo, false);
        jmri.InstanceManager.setSensorManager(l);
    }
    
    @AfterEach
    public void tearDown() {
        l.dispose();
        lnis = null;
        JUnitUtil.tearDown();
    }

    private final static Logger log = LoggerFactory.getLogger(LnSensorManagerTest.class);

}
