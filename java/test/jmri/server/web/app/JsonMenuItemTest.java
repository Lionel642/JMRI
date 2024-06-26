package jmri.server.web.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Locale;

import org.junit.jupiter.api.*;

/**
 *
 * @author Randall Wood (C) 2017
 */
public class JsonMenuItemTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void setUp() {
        jmri.util.JUnitUtil.setUp();

    }

    @AfterEach
    public void tearDown() {
        jmri.util.JUnitUtil.tearDown();

    }

    @Test
    public void testContstructor() throws Exception {

        Exception ex = Assertions.assertThrows(NullPointerException.class,() -> {
            setCtorNull(); });
        Assertions.assertNotNull(ex, "should have thrown NPE");

        ex = Assertions.assertThrows(IllegalArgumentException.class,() -> {
            Assertions.assertNotNull(new JsonMenuItem(mapper.createObjectNode())); });
        Assertions.assertNotNull(ex, "should have thrown IllegalArgumentException");

        ObjectNode node = mapper.createObjectNode();
        node.put("path", "/");

        Assertions.assertDoesNotThrow( () -> {
            Assertions.assertNotNull(new JsonMenuItem(node)); });

    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings( value = "NP_NONNULL_PARAM_VIOLATION",
        justification = "testing exception when null passed")
    private void setCtorNull() throws Exception {
        Assertions.assertNotNull(new JsonMenuItem(null));
        Assertions.fail("Should have thrown NPE");
    }

    @Test
    public void testGetPath() {
        ObjectNode node = mapper.createObjectNode();
        node.put("path", "/");
        JsonMenuItem mi = new JsonMenuItem(node);
        assertEquals("/", mi.getPath());
    }

    @Test
    public void testGetHref() {
        ObjectNode node = mapper.createObjectNode();
        node.put("path", "/");
        JsonMenuItem mi = new JsonMenuItem(node);
        assertNull(mi.getHref());
        node.put("href", "/");
        mi = new JsonMenuItem(node);
        assertEquals("/", mi.getHref());
    }

    @Test
    public void testGetIconClass() {
        ObjectNode node = mapper.createObjectNode();
        node.put("path", "/");
        JsonMenuItem mi = new JsonMenuItem(node);
        assertNull(mi.getIconClass());
        node.put("iconClass", "/");
        mi = new JsonMenuItem(node);
        assertEquals("/", mi.getIconClass());
    }

    @Test
    public void testGetTitle() {
        ObjectNode node = mapper.createObjectNode();
        node.put("path", "BeanNameTurnout");
        JsonMenuItem mi = new JsonMenuItem(node);
        assertEquals(Bundle.getMessage(Locale.ENGLISH, "BeanNameTurnout"), mi.getTitle(Locale.ENGLISH));
        assertEquals(Bundle.getMessage(Locale.ITALIAN, "BeanNameTurnout"), mi.getTitle(Locale.ITALIAN));
        node.put("title", "/");
        mi = new JsonMenuItem(node);
        assertEquals("/", mi.getTitle(Locale.ENGLISH));
        assertEquals("/", mi.getTitle(Locale.ITALIAN));
    }

    @Test
    public void testGetPosition() {
        ObjectNode node = mapper.createObjectNode();
        node.put("path", "/");
        JsonMenuItem mi = new JsonMenuItem(node);
        assertEquals(300, mi.getPosition());
        node.put("position", 200);
        mi = new JsonMenuItem(node);
        assertEquals(200, mi.getPosition());
    }

    @Test
    public void testIsSeparatedBefore() {
        ObjectNode node = mapper.createObjectNode();
        node.put("path", "/");
        JsonMenuItem mi = new JsonMenuItem(node);
        assertFalse(mi.isSeparatedBefore());
        node.put("separatedBefore", false);
        mi = new JsonMenuItem(node);
        assertFalse(mi.isSeparatedBefore());
        node.put("separatedBefore", true);
        mi = new JsonMenuItem(node);
        assertTrue(mi.isSeparatedBefore());
    }

    @Test
    public void testIsSeparatedAfter() {
        ObjectNode node = mapper.createObjectNode();
        node.put("path", "/");
        JsonMenuItem mi = new JsonMenuItem(node);
        assertFalse(mi.isSeparatedAfter());
        node.put("separatedAfter", false);
        mi = new JsonMenuItem(node);
        assertFalse(mi.isSeparatedAfter());
        node.put("separatedAfter", true);
        mi = new JsonMenuItem(node);
        assertTrue(mi.isSeparatedAfter());
    }

    @Test
    public void testIsDynamic() {
        ObjectNode node = mapper.createObjectNode();
        node.put("path", "/");
        JsonMenuItem mi = new JsonMenuItem(node);
        assertFalse(mi.isDynamic());
        node.put("dynamic", false);
        mi = new JsonMenuItem(node);
        assertFalse(mi.isDynamic());
        node.put("dynamic", true);
        mi = new JsonMenuItem(node);
        assertTrue(mi.isDynamic());
    }

}
