package jmri.jmrix.can.adapters.gridconnect.usblcc.serialdriver.configurexml;

import org.jdom2.Element;

import jmri.jmrix.PortAdapter;
import jmri.jmrix.can.adapters.gridconnect.usblcc.serialdriver.ConnectionConfig;
import jmri.jmrix.can.adapters.gridconnect.usblcc.serialdriver.SerialDriverAdapter;
import jmri.jmrix.configurexml.AbstractSerialConnectionConfigXml;

/**
 * Handle XML persistance of layout connections by persistening the
 * SerialDriverAdapter (and connections). Note this is named as the XML version
 * of a ConnectionConfig object, but it's actually persisting the
 * SerialDriverAdapter.
 * <p>
 * This class is invoked from jmrix.JmrixConfigPaneXml on write, as that class
 * is the one actually registered. Reads are brought here directly via the class
 * attribute in the XML.
 *
 * @author Bob Jacobsen Copyright: Copyright (c) 2003
 * @author Andrew Crosland 2008
 * @author Andrew Crosland 2023
 */
public class ConnectionConfigXml extends AbstractSerialConnectionConfigXml {

    public ConnectionConfigXml() {
        super();
    }

    @Override
    protected void getInstance() {
        adapter = new SerialDriverAdapter();
    }

    @Override
    protected void getInstance(Object object) {
        adapter = ((ConnectionConfig) object).getAdapter();
    }

    @Override
    protected void register() {
        this.register(new ConnectionConfig(adapter));
    }

    @Override
    protected void loadOptions(Element shared, Element perNode, PortAdapter adapter) {
        super.loadOptions(shared, perNode, adapter);

        jmri.jmrix.openlcb.configurexml.ConnectionConfigXml.maybeLoadOlcbProfileSettings(
                shared.getParentElement(), perNode.getParentElement(), adapter);
    }

    @Override
    protected void extendElement(Element e) {
        jmri.jmrix.openlcb.configurexml.ConnectionConfigXml.maybeSaveOlcbProfileSettings(
                e, adapter);
    }
}
