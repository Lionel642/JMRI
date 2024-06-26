package jmri.util.startup.configurexml;

import java.lang.reflect.InvocationTargetException;

import jmri.util.startup.PerformActionModel;
import jmri.util.startup.StartupActionsManager;
import jmri.InstanceManager;
import jmri.SystemConnectionMemo;
import jmri.jmrix.swing.SystemConnectionAction;

import org.jdom2.Attribute;
import org.jdom2.Element;

/**
 * Handle XML persistence of PerformActionModel objects.
 *
 * @author Bob Jacobsen Copyright (c) 2003
 * @see jmri.util.startup.PerformActionModelFactory
 */
public class PerformActionModelXml extends jmri.configurexml.AbstractXmlAdapter {

    public PerformActionModelXml() {
        // no state to set
    }

    /**
     * Default implementation for storing the model contents
     *
     * @param o Object to store, of type PerformActonModel
     * @return Element containing the complete info
     */
    @Override
    public Element store(Object o) {
        Element element = new Element("perform");
        PerformActionModel g = (PerformActionModel) o;

        element.setAttribute("name", g.getClassName());
        element.setAttribute("type", "Action");
        element.setAttribute("enabled", g.isEnabled() ? "yes" : "no");
        element.setAttribute("class", this.getClass().getName());
        Element property = new Element("property"); // NOI18N
        property.setAttribute("name", "systemPrefix"); // NOI18N
        property.setAttribute("value", g.getSystemPrefix());
        element.addContent(property);
        return element;
    }

    /**
     * Object should be loaded after basic GUI constructed
     *
     * @return true to defer loading
     * @see jmri.configurexml.AbstractXmlAdapter#loadDeferred()
     * @see jmri.configurexml.XmlAdapter#loadDeferred()
     */
    @Override
    public boolean loadDeferred() {
        return true;
    }

    @Override
    public boolean load(Element shared, Element perNode) {
        boolean result = true;
        String className = shared.getAttribute("name").getValue();
        PerformActionModel model = new PerformActionModel();

        Attribute enabled = shared.getAttribute("enabled");
        if (enabled != null) {
            model.setEnabled("yes".equals(enabled.getValue()));
        } else {
            model.setEnabled(true);
        }

        model.setClassName(className);
        shared.getChildren("property").forEach(child -> { // NOI18N
            String value = child.getAttributeValue("value"); // NOI18N
            if (child.getAttributeValue("name").equals("systemPrefix") // NOI18N
                    && value != null) {
                // handle the situation where the model expects a system prefix
                // but was not saved with one in a pre-4.19.7 JMRI instance
                // TODO: at some point (June 2022 release?) change entire
                // try/catch block to just "model.setSystemPrefix(value);"
                try {
                    Class<?> ac = Class.forName(className);
                    if (value.isEmpty() && SystemConnectionAction.class.isAssignableFrom(ac)) {
                        SystemConnectionAction<?> a = (SystemConnectionAction<?>) ac.getConstructor().newInstance();
                        InstanceManager.getList(SystemConnectionMemo.class)
                                .forEach(memo -> a.getSystemConnectionMemoClasses().stream()
                                .filter(mc -> memo.getClass().isAssignableFrom(mc))
                                .forEach(mc -> model.setSystemPrefix(memo.getSystemPrefix())));
                    } else {
                        model.setSystemPrefix(value);
                    }
                } catch (ClassNotFoundException
                        | InstantiationException
                        | IllegalAccessException
                        | IllegalArgumentException
                        | InvocationTargetException
                        | NoSuchMethodException
                        | SecurityException ex) {
                    // ignore to allow manager to handle later
                    log.warn("While trying to do {}, encountered exception", className, ex);
                }
            }
        });
        InstanceManager.getDefault(StartupActionsManager.class).addAction(model);
        return result;
    }

    // initialize logging
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PerformActionModelXml.class);

}
