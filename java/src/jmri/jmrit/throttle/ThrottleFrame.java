package jmri.jmrit.throttle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

import jmri.DccLocoAddress;
import jmri.DccThrottle;
import jmri.InstanceManager;
import jmri.LocoAddress;
import jmri.ThrottleManager;
import jmri.configurexml.LoadXmlConfigAction;
import jmri.configurexml.StoreXmlConfigAction;
import jmri.jmrit.XmlFile;
import jmri.jmrit.jython.Jynstrument;
import jmri.jmrit.jython.JynstrumentFactory;
import jmri.jmrit.roster.RosterEntry;
import jmri.util.FileUtil;
import jmri.util.iharder.dnd.URIDrop;
import jmri.util.swing.JmriJOptionPane;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

/**
 * Should be named ThrottlePanel but was already existing with that name and
 * don't want to break dependencies (particularly in Jython code)
 *
 * @author Glen Oberhauser
 * @author Andrew Berridge Copyright 2010
 */
public class ThrottleFrame extends JDesktopPane implements ComponentListener, AddressListener {

    private DccThrottle throttle;
    private final ThrottleManager throttleManager;
    private final ThrottlesTableModel allThrottlesTableModel = InstanceManager.getDefault(ThrottleFrameManager.class).getThrottlesListPanel().getTableModel();

    private final Integer BACKPANEL_LAYER = Integer.MIN_VALUE;
    private final Integer PANEL_LAYER_FRAME = 1;
    private final Integer PANEL_LAYER_PANEL = 2;

    private static final int ADDRESS_PANEL_INDEX = 0;
    private static final int CONTROL_PANEL_INDEX = 1;
    private static final int FUNCTION_PANEL_INDEX = 2;
    private static final int SPEED_DISPLAY_INDEX = 3;
    private static final int NUM_FRAMES = 4;

    private JInternalFrame[] frameList;
    private int activeFrame;

    private final ThrottleWindow throttleWindow;

    private ControlPanel controlPanel;
    private FunctionPanel functionPanel;
    private AddressPanel addressPanel;
    private BackgroundPanel backgroundPanel;
    private FrameListener frameListener;
    private SpeedPanel speedPanel;

    private String title;
    private String lastUsedSaveFile = null;

    private boolean isEditMode = true;
    private boolean willSwitch = false;
    private boolean isLoadingDefault = false;

    private static final String DEFAULT_THROTTLE_FILENAME = "JMRI_ThrottlePreference.xml";

    public static String getDefaultThrottleFolder() {
        return FileUtil.getUserFilesPath() + "throttle" + File.separator;
    }

    public static String getDefaultThrottleFilename() {
        return getDefaultThrottleFolder() + DEFAULT_THROTTLE_FILENAME;
    }

    public ThrottleFrame(ThrottleWindow tw) {
        this(tw, InstanceManager.getDefault(ThrottleManager.class));
    }

    public ThrottleFrame(ThrottleWindow tw, ThrottleManager tm) {
        super();
        throttleWindow = tw;
        throttleManager = tm;
        initGUI();
        applyPreferences();
        InstanceManager.getDefault(ThrottleFrameManager.class).getThrottlesListPanel().getTableModel().addThrottleFrame(tw,this);
    }

    public ThrottleWindow getThrottleWindow() {
        return throttleWindow;
    }

    public ControlPanel getControlPanel() {
        return controlPanel;
    }

    public FunctionPanel getFunctionPanel() {
        return functionPanel;
    }

    public AddressPanel getAddressPanel() {
        return addressPanel;
    }

    public RosterEntry getRosterEntry() {
        return addressPanel.getRosterEntry();
    }

    public void toFront() {
        if (throttleWindow == null) {
            return;
        }
        throttleWindow.toFront(title);
    }

    public SpeedPanel getSpeedPanel() {
        return speedPanel;
    }

    /**
     * Sets the location of a throttle frame on the screen according to x and y
     * coordinates
     *
     * @see java.awt.Component#setLocation(int, int)
     */
    @Override
    public void setLocation(int x, int y) {
        if (throttleWindow == null) {
            return;
        }
        throttleWindow.setLocation(new Point(x, y));
    }

    public void setTitle(String txt) {
        title = txt;
    }

    public String getTitle() {
        return title;
    }

    private void saveThrottle(String sfile) {
        // Save throttle: title / window position
        // as strongly linked to extended throttles and roster presence, do not save function buttons and background window as they're stored in the roster entry
        XmlFile xf = new XmlFile() {
        };   // odd syntax is due to XmlFile being abstract
        xf.makeBackupFile(sfile);
        File file = new File(sfile);
        try {
            //The file does not exist, create it before writing
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                if (!parentDir.mkdir()) { // make directory and check result
                    log.error("could not make parent directory");
                }
            }
            if (!file.createNewFile()) { // create file, check success
                log.error("createNewFile failed");
            }
        } catch (IOException exp) {
            log.error("Exception while writing the throttle file, may not be complete: {}", exp.getMessage());
        }

        try {
            Element root = new Element("throttle-config");
            root.setAttribute("noNamespaceSchemaLocation",  // NOI18N
                    "http://jmri.org/xml/schema/throttle-config.xsd",  // NOI18N
                    org.jdom2.Namespace.getNamespace("xsi",
                            "http://www.w3.org/2001/XMLSchema-instance"));  // NOI18N
            Document doc = new Document(root);

            // add XSLT processing instruction
            // <?xml-stylesheet type="text/xsl" href="XSLT/throttle.xsl"?>
            java.util.Map<String,String> m = new java.util.HashMap<String, String>();
            m.put("type", "text/xsl");
            m.put("href", jmri.jmrit.XmlFile.xsltLocation + "throttle-config.xsl");
            org.jdom2.ProcessingInstruction p = new org.jdom2.ProcessingInstruction("xml-stylesheet", m);
            doc.addContent(0,p);

            Element throttleElement = getXml();
            // don't save the loco address or consist address
            //   throttleElement.getChild("AddressPanel").removeChild("locoaddress");
            //   throttleElement.getChild("AddressPanel").removeChild("locoaddress");
            if ((this.getRosterEntry() != null) &&
                    (getDefaultThrottleFolder() + addressPanel.getRosterEntry().getId().trim() + ".xml").compareTo(sfile) == 0) // don't save function buttons labels, they're in roster entry
            {
                throttleElement.getChild("FunctionPanel").removeChildren("FunctionButton");
                saveRosterChanges();
            } 

            root.setContent(throttleElement);
            xf.writeXML(file, doc);
            setLastUsedSaveFile(sfile);
        } catch (IOException ex) {
            log.warn("Exception while storing throttle xml: {}", ex.getMessage());
        }
    }

    private void loadDefaultThrottle() {
        if (isLoadingDefault) { // avoid looping on this method
            return; 
        }
        isLoadingDefault = true;
        String dtf = InstanceManager.getDefault(ThrottlesPreferences.class).getDefaultThrottleFilePath();
        if (dtf == null || dtf.isEmpty()) {
            return;
        }
        log.debug("Loading default throttle file : {}", dtf);
        loadThrottle(dtf);
        setLastUsedSaveFile(null);
        isLoadingDefault = false;
    }

    public void loadThrottle() {
        JFileChooser fileChooser = jmri.jmrit.XmlFile.userFileChooser(Bundle.getMessage("PromptXmlFileTypes"), "xml");
        fileChooser.setCurrentDirectory(new File(getDefaultThrottleFolder()));
        fileChooser.setDialogType(JFileChooser.OPEN_DIALOG);
        java.io.File file = LoadXmlConfigAction.getFile(fileChooser, this);
        if (file == null) {
            return ;
        }
        loadThrottle(file.getAbsolutePath());
    }

    public void loadThrottle(String sfile) {
        if (sfile == null) {
            loadThrottle();
            return;
        }
        log.debug("Loading throttle file : {}", sfile);
        boolean switchAfter = false;
        if (!isEditMode) {
            setEditMode(true);
            switchAfter = true;
        }

        try {
            XmlFile xf = new XmlFile() {
            };   // odd syntax is due to XmlFile being abstract
            xf.setValidate(XmlFile.Validate.CheckDtdThenSchema);
            File f = new File(sfile);
            Element root = xf.rootFromFile(f);
            Element conf = root.getChild("ThrottleFrame");
            // File looks ok
            setLastUsedSaveFile(sfile);
            // close all existing Jynstruments
            Component[] cmps = getComponents();
            for (Component cmp : cmps) {
                try {
                    if (cmp instanceof JInternalFrame) {
                        JInternalFrame jyf = (JInternalFrame) cmp;
                        Component[] cmps2 = jyf.getContentPane().getComponents();
                        for (Component cmp2 : cmps2) {
                            if (cmp2 instanceof Jynstrument) {
                                ((Jynstrument) cmp2).exit();
                                jyf.dispose();
                            }
                        }
                    }
                } catch (Exception ex) {
                    log.debug("Got exception (no panic) {}", ex.getMessage());
                }
            }
            // and finally load all preferences
            setXml(conf);
        } catch (FileNotFoundException ex) {
            // Don't show error dialog if file is not found
            log.debug("Loading throttle exception: {}", ex.getMessage());
            log.debug("Tried loading throttle file \"{}\" , reverting to default, if any", sfile);
            loadDefaultThrottle(); // revert to loading default one
        } catch (NullPointerException | IOException | JDOMException ex) {
            log.debug("Loading throttle exception: {}", ex.getMessage());
            log.debug("Tried loading throttle file \"{}\" , reverting to default, if any", sfile);
            jmri.configurexml.ConfigXmlManager.creationErrorEncountered(
                    null, "parsing file " + sfile,
                    "Parse error", null, null, ex);
            loadDefaultThrottle(); // revert to loading default one
        }
//     checkPosition();
        if (switchAfter) {
            setEditMode(false);
        }
    }

    /**
     * Place and initialize the GUI elements.
     * <ul>
     * <li> ControlPanel
     * <li> FunctionPanel
     * <li> AddressPanel
     * <li> SpeedPanel
     * <li> JMenu
     * </ul>
     */
    private void initGUI() {
        frameListener = new FrameListener();

        controlPanel = new ControlPanel(throttleManager);
        controlPanel.setResizable(true);
        controlPanel.setClosable(true);
        controlPanel.setIconifiable(true);
        controlPanel.setTitle(Bundle.getMessage("ThrottleMenuViewControlPanel"));
        controlPanel.pack();
        controlPanel.setVisible(true);
        controlPanel.setEnabled(false);
        controlPanel.addInternalFrameListener(frameListener);

        functionPanel = new FunctionPanel();
        functionPanel.setResizable(true);
        functionPanel.setClosable(true);
        functionPanel.setIconifiable(true);
        functionPanel.setTitle(Bundle.getMessage("ThrottleMenuViewFunctionPanel"));

        // assumes button width of 54, height of 30 (set in class FunctionButton) with
        // horiz and vert gaps of 5 each (set in FunctionPanel class)
        // with 3 buttons across and 6 rows high
        int width = 3 * (FunctionButton.getButtonWidth()) + 2 * 3 * 5 + 10;   // = 192
        int height = 8 * (FunctionButton.getButtonHeight()) + 2 * 6 * 5 + 20; // = 240 (but there seems to be another 10 needed for some LAFs)

        functionPanel.setSize(width, height);
        functionPanel.setLocation(controlPanel.getWidth(), 0);
        functionPanel.setVisible(true);
        functionPanel.setEnabled(false);
        functionPanel.addInternalFrameListener(frameListener);

        speedPanel = new SpeedPanel();
        speedPanel.setResizable(true);
        speedPanel.setVisible(false);
        speedPanel.setClosable(true);
        speedPanel.setIconifiable(true);
        speedPanel.setTitle(Bundle.getMessage("ThrottleMenuViewSpeedPanel"));
        speedPanel.addInternalFrameListener(frameListener);
        speedPanel.pack();

        addressPanel = new AddressPanel(throttleManager);
        addressPanel.setResizable(true);
        addressPanel.setClosable(true);
        addressPanel.setIconifiable(true);
        addressPanel.setTitle(Bundle.getMessage("ThrottleMenuViewAddressPanel"));
        addressPanel.pack();
        if (addressPanel.getWidth()<functionPanel.getWidth()) {
            addressPanel.setSize(functionPanel.getWidth(),addressPanel.getHeight());
        }
        addressPanel.setLocation(controlPanel.getWidth(), functionPanel.getHeight());
        addressPanel.setVisible(true);
        addressPanel.addInternalFrameListener(frameListener);
        functionPanel.setAddressPanel(addressPanel); // so the function panel can get access to the roster
        controlPanel.setAddressPanel(addressPanel);
        speedPanel.setAddressPanel(addressPanel);

        if (controlPanel.getHeight() < functionPanel.getHeight() + addressPanel.getHeight()) {
            controlPanel.setSize(controlPanel.getWidth(), functionPanel.getHeight() + addressPanel.getHeight());
        }
        if (controlPanel.getHeight() > functionPanel.getHeight() + addressPanel.getHeight()) {
            addressPanel.setSize(addressPanel.getWidth(), controlPanel.getHeight() - functionPanel.getHeight());
        }
        if (functionPanel.getWidth() < addressPanel.getWidth()) {
            functionPanel.setSize(addressPanel.getWidth(), functionPanel.getHeight());
        }

        speedPanel.setSize(addressPanel.getWidth() + controlPanel.getWidth(), addressPanel.getHeight() / 2);
        speedPanel.setLocation(0, controlPanel.getHeight());

        addressPanel.addAddressListener(controlPanel);
        addressPanel.addAddressListener(functionPanel);
        addressPanel.addAddressListener(speedPanel);
        addressPanel.addAddressListener(this);

        add(controlPanel, PANEL_LAYER_FRAME);
        add(functionPanel, PANEL_LAYER_FRAME);
        add(addressPanel, PANEL_LAYER_FRAME);
        add(speedPanel, PANEL_LAYER_FRAME);

        backgroundPanel = new BackgroundPanel();
        backgroundPanel.setAddressPanel(addressPanel); // reusing same way to do it than existing thing in functionPanel
        addComponentListener(backgroundPanel); // backgroudPanel warned when desktop resized
        addressPanel.addAddressListener(backgroundPanel);
        addressPanel.setBackgroundPanel(backgroundPanel); // so that it's changeable when browsing through rosters
        add(backgroundPanel, BACKPANEL_LAYER);

        addComponentListener(this); // to force sub windows repositionning

        frameList = new JInternalFrame[NUM_FRAMES];
        frameList[ADDRESS_PANEL_INDEX] = addressPanel;
        frameList[CONTROL_PANEL_INDEX] = controlPanel;
        frameList[FUNCTION_PANEL_INDEX] = functionPanel;
        frameList[SPEED_DISPLAY_INDEX] = speedPanel;
        activeFrame = ADDRESS_PANEL_INDEX;

        setPreferredSize(new Dimension(Math.max(controlPanel.getWidth() + functionPanel.getWidth(), controlPanel.getWidth() + addressPanel.getWidth()),
                Math.max(addressPanel.getHeight() + functionPanel.getHeight(), controlPanel.getHeight())));

        // #JYNSTRUMENT# Bellow prepare drag'n drop receptacle:
        new URIDrop(backgroundPanel, uris -> {
                if (isEditMode) {
                    for (URI uri : uris ) {
                        ynstrument(new File(uri).getPath());
                    }
                }
            });

        try {
            addressPanel.setSelected(true);
        } catch (PropertyVetoException ex) {
            log.error("Error selecting InternalFrame: {}", ex.getMessage());
        }
    }

    // #JYNSTRUMENT# here instantiate the Jynstrument, put it in a component, initialize the context and start it
    public JInternalFrame ynstrument(String path) {
        if (path == null) {
            return null;
        }
        Jynstrument it = JynstrumentFactory.createInstrument(path, this); // everything is there
        if (it == null) {
            log.error("Error while creating Jynstrument {}", path);
            return null;
        }
        setTransparentBackground(it);
        JInternalFrame newiFrame = new JInternalFrame(it.getClassName());
        newiFrame.add(it);
        newiFrame.addInternalFrameListener(frameListener);
        newiFrame.setDoubleBuffered(true);
        newiFrame.setResizable(true);
        newiFrame.setClosable(true);
        newiFrame.setIconifiable(true);
        newiFrame.getContentPane().addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
            }

            @Override
            public void componentRemoved(ContainerEvent e) {
                Container c = e.getContainer();
                while ((!(c instanceof JInternalFrame)) && (!(c instanceof TranslucentJPanel))) {
                    c = c.getParent();
                }
                c.setVisible(false);
                remove(c);
                repaint();
            }
        });
        newiFrame.pack();
        add(newiFrame, PANEL_LAYER_FRAME);
        newiFrame.setVisible(true);
        return newiFrame;
    }

    // make sure components are inside this frame bounds
    private void checkPosition(Component comp) {
        if ((this.getWidth() < 1) || (this.getHeight() < 1)) {
            return;
        }

        Rectangle pos = comp.getBounds();

        if (pos.width > this.getWidth()) { // Component largest than container
            pos.width = this.getWidth() - 2;
            pos.x = 1;
        }
        if (pos.x + pos.width > this.getWidth()) // Component to large
        {
            pos.x = this.getWidth() - pos.width - 1;
        }
        if (pos.x < 0) // Component to far on the left
        {
            pos.x = 1;
        }

        if (pos.height > this.getHeight()) { // Component higher than container
            pos.height = this.getHeight() - 2;
            pos.y = 1;
        }
        if (pos.y + pos.height > this.getHeight()) // Component to low
        {
            pos.y = this.getHeight() - pos.height - 1;
        }
        if (pos.y < 0) // Component to high
        {
            pos.y = 1;
        }

        comp.setBounds(pos);
    }

    public void makeAllComponentsInBounds() {
        Component[] cmps = getComponents();
        for (Component cmp : cmps) {
            checkPosition(cmp);
        }
    }

    private HashMap<Container, JInternalFrame> contentPanes;

    public void applyPreferences() {
        ThrottlesPreferences preferences = InstanceManager.getDefault(ThrottlesPreferences.class);

        backgroundPanel.setVisible(  (preferences.isUsingExThrottle()) && (preferences.isUsingRosterImage()));

        controlPanel.applyPreferences();
        functionPanel.applyPreferences();
        addressPanel.applyPreferences();
        backgroundPanel.applyPreferences();
        loadDefaultThrottle();
    }

    private static class TranslucentJPanel extends JPanel {

        private final Color TRANS_COL = new Color(100, 100, 100, 100);

        public TranslucentJPanel() {
            super();
            setOpaque(false);
        }

        @Override
        public void paintComponent(Graphics g) {
            g.setColor(TRANS_COL);
            g.fillRoundRect(0, 0, getSize().width, getSize().height, 10, 10);
            super.paintComponent(g);
        }
    }

    private void playRendering() {
        Component[] cmps = getComponentsInLayer(PANEL_LAYER_FRAME);
        contentPanes = new HashMap<>();
        for (Component cmp : cmps) {
            if ((cmp instanceof JInternalFrame) && (cmp.isVisible())) {
                translude((JInternalFrame)cmp);
            }
        }
    }

    private void translude(JInternalFrame jif) {
        Dimension cpSize = jif.getContentPane().getSize();
        Point cpLoc = jif.getContentPane().getLocationOnScreen();
        TranslucentJPanel pane = new TranslucentJPanel();
        pane.setLayout(new BorderLayout());
        contentPanes.put(pane, jif);
        pane.add(jif.getContentPane(), BorderLayout.CENTER);
        setTransparent(pane, true);
        jif.setContentPane(new JPanel());
        jif.setVisible(false);
        Point loc = new Point(cpLoc.x - this.getLocationOnScreen().x, cpLoc.y - this.getLocationOnScreen().y);
        add(pane, PANEL_LAYER_PANEL);
        pane.setLocation(loc);
        pane.setSize(cpSize);
    }

    private void editRendering() {
        Component[] cmps = getComponentsInLayer(PANEL_LAYER_PANEL);
        for (Component cmp : cmps) {
            if (cmp instanceof JPanel) {
                JPanel pane = (JPanel) cmp;
                JInternalFrame jif = contentPanes.get(pane);
                jif.setContentPane((Container) pane.getComponent(0));
                setTransparent(jif, false);
                jif.setVisible(true);
                remove(pane);
            }
        }
    }

    public void setEditMode(boolean mode) {
        if (mode == isEditMode)
            return;
        if (isVisible()) {
            if (!mode) {
                playRendering();
            } else {
                editRendering();
            }
            isEditMode = mode;
            willSwitch = false;
        } else {
            willSwitch = true;
        }
        throttleWindow.updateGUI();
    }

    public boolean getEditMode() {
        return isEditMode;
    }

    /**
     * Handle my own destruction.
     * <ol>
     * <li> dispose of sub windows.
     * <li> notify my manager of my demise.
     * </ol>
     */
    public void dispose() {
        log.debug("Disposing {}", getTitle());
        URIDrop.remove(backgroundPanel);
        addressPanel.removeAddressListener(this);
        // should the throttle list table stop listening to that throttle?
        if (throttle!=null &&  allThrottlesTableModel.getNumberOfEntriesFor((DccLocoAddress) throttle.getLocoAddress()) == 1 ) {
            throttleManager.removeListener(throttle.getLocoAddress(), allThrottlesTableModel);
            allThrottlesTableModel.fireTableDataChanged();
        }
        
        // remove from the throttle list table
        InstanceManager.getDefault(ThrottleFrameManager.class).getThrottlesListPanel().getTableModel().removeThrottleFrame(this, addressPanel.getCurrentAddress());
        // check for any special disposing in InternalFrames
        controlPanel.destroy();
        functionPanel.destroy();
        speedPanel.destroy();
        backgroundPanel.destroy();
        // dispose of this last because it will release and destroy the throttle.
        addressPanel.destroy();
    }

    public void saveRosterChanges() {
        RosterEntry rosterEntry = addressPanel.getRosterEntry();
        if (rosterEntry == null) {
            JmriJOptionPane.showMessageDialog(this, Bundle.getMessage("ThrottleFrameNoRosterItemMessageDialog"),
                Bundle.getMessage("ThrottleFrameNoRosterItemTitleDialog"), JmriJOptionPane.ERROR_MESSAGE);
            return;
        }
        if ((!InstanceManager.getDefault(ThrottlesPreferences.class).isSavingThrottleOnLayoutSave()) && (JmriJOptionPane.showConfirmDialog(this, Bundle.getMessage("ThrottleFrameRosterChangeMesageDialog"),
            Bundle.getMessage("ThrottleFrameRosterChangeTitleDialog"), JmriJOptionPane.YES_NO_OPTION) != JmriJOptionPane.YES_OPTION)) {
            return;
        }
        functionPanel.saveFunctionButtonsToRoster(rosterEntry);
        controlPanel.saveToRoster(rosterEntry);
    }

    /**
     * An extension of InternalFrameAdapter for listening to the closing of of
     * this frame's internal frames.
     *
     * @author glen
     */
    class FrameListener extends InternalFrameAdapter {

        /**
         * Listen for the closing of an internal frame and set the "View" menu
         * appropriately. Then hide the closing frame
         *
         * @param e The InternalFrameEvent leading to this action
         */
        @Override
        public void internalFrameClosing(InternalFrameEvent e) {
            if (e.getSource() == controlPanel) {
                throttleWindow.getViewControlPanel().setSelected(false);
                controlPanel.setVisible(false);
            } else if (e.getSource() == addressPanel) {
                throttleWindow.getViewAddressPanel().setSelected(false);
                addressPanel.setVisible(false);
            } else if (e.getSource() == functionPanel) {
                throttleWindow.getViewFunctionPanel().setSelected(false);
                functionPanel.setVisible(false);
            } else if (e.getSource() == speedPanel) {
                throttleWindow.getViewSpeedPanel().setSelected(false);
                speedPanel.setVisible(false);
            } else {
                try { // #JYNSTRUMENT#, Very important, clean the Jynstrument
                    if ((e.getSource() instanceof JInternalFrame)) {
                        Component[] cmps = ((JInternalFrame) e.getSource()).getContentPane().getComponents();
                        int i = 0;
                        while ((i < cmps.length) && (!(cmps[i] instanceof Jynstrument))) {
                            i++;
                        }
                        if ((i < cmps.length) && (cmps[i] instanceof Jynstrument)) {
                            ((Jynstrument) cmps[i]).exit();
                        }
                    }
                } catch (Exception exc) {
                    log.debug("Got exception, can ignore: ", exc);
                }
            }
        }

        /**
         * Listen for the activation of an internal frame record this property
         * for correct processing of the frame cycling key.
         *
         * @param e The InternalFrameEvent leading to this action
         */
        @Override
        public void internalFrameActivated(InternalFrameEvent e) {
            if (e.getSource() == controlPanel) {
                activeFrame = CONTROL_PANEL_INDEX;
            } else if (e.getSource() == addressPanel) {
                activeFrame = ADDRESS_PANEL_INDEX;
            } else if (e.getSource() == functionPanel) {
                activeFrame = FUNCTION_PANEL_INDEX;
            } else if (e.getSource() == functionPanel) {
                activeFrame = SPEED_DISPLAY_INDEX;
            }
        }
    }

    /**
     * Collect the prefs of this object into XML Element
     * <ul>
     * <li> Window prefs
     * <li> ControlPanel
     * <li> FunctionPanel
     * <li> AddressPanel
     * <li> SpeedPanel
     * </ul>
     *
     *
     * @return the XML of this object.
     */
    public Element getXml() {
        boolean switchAfter = false;
        if (!isEditMode) {
            setEditMode(true);
            switchAfter = true;
        }

        Element me = new Element("ThrottleFrame");

        if (((javax.swing.plaf.basic.BasicInternalFrameUI) getControlPanel().getUI()).getNorthPane() != null) {
            Dimension bDim = ((javax.swing.plaf.basic.BasicInternalFrameUI) getControlPanel().getUI()).getNorthPane().getPreferredSize();
            me.setAttribute("border", Integer.toString(bDim.height));
        }

        ArrayList<Element> children = new ArrayList<>(1);

//        children.add(WindowPreferences.getPreferences(this));  // not required as it is in ThrottleWindow
        children.add(controlPanel.getXml());
        children.add(functionPanel.getXml());
        children.add(addressPanel.getXml());
        children.add(speedPanel.getXml());
        // Save Jynstruments
        Component[] cmps = getComponents();
        for (Component cmp : cmps) {
            try {
                if (cmp instanceof JInternalFrame) {
                    Component[] cmps2 = ((JInternalFrame) cmp).getContentPane().getComponents();
                    int j = 0;
                    while ((j < cmps2.length) && (!(cmps2[j] instanceof Jynstrument))) {
                        j++;
                    }
                    if ((j < cmps2.length) && (cmps2[j] instanceof Jynstrument)) {
                        Jynstrument jyn = (Jynstrument) cmps2[j];
                        Element elt = new Element("Jynstrument");
                        elt.setAttribute("JynstrumentFolder", FileUtil.getPortableFilename(jyn.getFolder()));
                        ArrayList<Element> jychildren = new ArrayList<>(1);
                        jychildren.add(WindowPreferences.getPreferences((JInternalFrame) cmp));
                        Element je = jyn.getXml();
                        if (je != null) {
                            jychildren.add(je);
                        }
                        elt.setContent(jychildren);
                        children.add(elt);
                    }
                }
            } catch (Exception ex) {
                log.debug("Got exception (no panic) {}", ex.getMessage());
            }
        }
        me.setContent(children);
        if (switchAfter) {
            setEditMode(false);
        }
        return me;
    }

    public Element getXmlFile() {
        if (getLastUsedSaveFile() == null) { // || (getRosterEntry()==null))
            return null;
        }
        Element me = new Element("ThrottleFrame");
        me.setAttribute("ThrottleXMLFile", FileUtil.getPortableFilename(getLastUsedSaveFile()));
        return me;
    }

    /**
     * Set the preferences based on the XML Element.
     * <ul>
     * <li> Window prefs
     * <li> Frame title
     * <li> ControlPanel
     * <li> FunctionPanel
     * <li> AddressPanel
     * <li> SpeedPanel
     * </ul>
     *
     * @param e The Element for this object.
     */
    public void setXml(Element e) {
        if (e == null) {
            return;
        }

        String sfile = e.getAttributeValue("ThrottleXMLFile");
        if (sfile != null) {
            loadThrottle(FileUtil.getExternalFilename(sfile));
            return;
        }

        boolean switchAfter = false;
        if (!isEditMode) {
            setEditMode(true);
            switchAfter = true;
        }

        int bSize = 23;
        // Get InternalFrame border size
        if (e.getAttribute("border") != null) {
            bSize = Integer.parseInt((e.getAttribute("border").getValue()));
        }
        Element controlPanelElement = e.getChild("ControlPanel");
        controlPanel.setXml(controlPanelElement);
        if (((javax.swing.plaf.basic.BasicInternalFrameUI) controlPanel.getUI()).getNorthPane() != null) {
            ((javax.swing.plaf.basic.BasicInternalFrameUI) controlPanel.getUI()).getNorthPane().setPreferredSize(new Dimension(0, bSize));
        }
        Element functionPanelElement = e.getChild("FunctionPanel");
        functionPanel.setXml(functionPanelElement);
        if (((javax.swing.plaf.basic.BasicInternalFrameUI) functionPanel.getUI()).getNorthPane() != null) {
            ((javax.swing.plaf.basic.BasicInternalFrameUI) functionPanel.getUI()).getNorthPane().setPreferredSize(new Dimension(0, bSize));
        }
        Element addressPanelElement = e.getChild("AddressPanel");
        addressPanel.setXml(addressPanelElement);
        if (((javax.swing.plaf.basic.BasicInternalFrameUI) addressPanel.getUI()).getNorthPane() != null) {
            ((javax.swing.plaf.basic.BasicInternalFrameUI) addressPanel.getUI()).getNorthPane().setPreferredSize(new Dimension(0, bSize));
        }
        Element speedPanelElement = e.getChild("SpeedPanel");
        if (speedPanelElement != null) { // older throttle configs may not have this element
            speedPanel.setXml(speedPanelElement);
            if (((javax.swing.plaf.basic.BasicInternalFrameUI) speedPanel.getUI()).getNorthPane() != null) {
                ((javax.swing.plaf.basic.BasicInternalFrameUI) speedPanel.getUI()).getNorthPane().setPreferredSize(new Dimension(0, bSize));
            }
        }

        List<Element> jinsts = e.getChildren("Jynstrument");
        if ((jinsts != null) && (jinsts.size() > 0)) {
            for (Element jinst : jinsts) {
                JInternalFrame jif = ynstrument(FileUtil.getExternalFilename(jinst.getAttributeValue("JynstrumentFolder")));
                Element window = jinst.getChild("window");
                if (jif != null) {
                    if (window != null) {
                        WindowPreferences.setPreferences(jif, window);
                    }
                    Component[] cmps2 = jif.getContentPane().getComponents();
                    int j = 0;
                    while ((j < cmps2.length) && (!(cmps2[j] instanceof Jynstrument))) {
                        j++;
                    }
                    if ((j < cmps2.length) && (cmps2[j] instanceof Jynstrument)) {
                        ((Jynstrument) cmps2[j]).setXml(jinst);
                    }

                    jif.repaint();
                }
            }
        }
        setFrameTitle();
        if (switchAfter) {
            setEditMode(false);
        }
    }

    /**
     * setFrameTitle - set the frame title based on type, text and address
     */
    public void setFrameTitle() {
        String winTitle = Bundle.getMessage("ThrottleTitle");
        if (throttleWindow.getTitleTextType().compareTo("text") == 0) {
            winTitle = throttleWindow.getTitleText();
        } else  if ( throttle != null) {
            String addr  = addressPanel.getCurrentAddress().toString();        
            if (throttleWindow.getTitleTextType().compareTo("address") == 0) {
                winTitle = addr;         
            } else if (throttleWindow.getTitleTextType().compareTo("addressText") == 0) {
                winTitle = addr + " " + throttleWindow.getTitleText();
            } else if (throttleWindow.getTitleTextType().compareTo("textAddress") == 0) {
                winTitle = throttleWindow.getTitleText() + " " + addr;
            } else if (throttleWindow.getTitleTextType().compareTo("rosterID") == 0) {
                if ( (addressPanel.getRosterEntry() != null) && (addressPanel.getRosterEntry().getId() != null)
                        && (addressPanel.getRosterEntry().getId().length() > 0)) {
                    winTitle = addressPanel.getRosterEntry().getId();
                } else {
                    winTitle = addr; // better than nothing in that particular case
                }
            }
        }
        throttleWindow.setTitle(winTitle);        
    }

    @Override
    public void componentHidden(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentResized(ComponentEvent e) {
//  checkPosition ();
    }

    @Override
    public void componentShown(ComponentEvent e) {
        throttleWindow.setCurrentThrottleFrame(this);
        if (willSwitch) {
            setEditMode(this.throttleWindow.isEditMode());
            repaint();
        }
        throttleWindow.updateGUI();
        // bring addresspanel to front if no allocated throttle
        if (addressPanel.getThrottle() == null && throttleWindow.isEditMode()) {
            if (!addressPanel.isVisible()) {
                addressPanel.setVisible(true);
            }
            if (addressPanel.isIcon()) {
                try {
                    addressPanel.setIcon(false);
                } catch (PropertyVetoException ex) {
                    log.debug("JInternalFrame uniconify, vetoed");
                }
            }
            addressPanel.requestFocus();
            addressPanel.toFront();
            try {
                addressPanel.setSelected(true);
            } catch (java.beans.PropertyVetoException ex) {
                log.debug("JInternalFrame selection, vetoed");
            }
        }
    }

    public void saveThrottle() {
        if (getRosterEntry() != null) {
            saveThrottle(getDefaultThrottleFolder() + addressPanel.getRosterEntry().getId().trim() + ".xml");
        } else if (getLastUsedSaveFile() != null) {
            saveThrottle(getLastUsedSaveFile());
        }
    }

    public void saveThrottleAs() {
        JFileChooser fileChooser = jmri.jmrit.XmlFile.userFileChooser(Bundle.getMessage("PromptXmlFileTypes"), "xml");
        fileChooser.setCurrentDirectory(new File(getDefaultThrottleFolder()));
        fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
        java.io.File file = StoreXmlConfigAction.getFileName(fileChooser);
        if (file == null) {
            return;
        }
        saveThrottle(file.getAbsolutePath());
    }

    public void activateNextJInternalFrame() {
        try {
            int initialFrame = activeFrame; // avoid infinite loop
            do {
                activeFrame = (activeFrame + 1) % NUM_FRAMES;
                frameList[activeFrame].setSelected(true);
            } while ((frameList[activeFrame].isClosed() || frameList[activeFrame].isIcon() || (!frameList[activeFrame].isVisible())) && (initialFrame != activeFrame));
        } catch (PropertyVetoException ex) {
            log.warn("Exception selecting internal frame:{}", ex.getMessage());
        }
    }

    public void activatePreviousJInternalFrame() {
        try {
            int initialFrame = activeFrame; // avoid infinite loop
            do {
                activeFrame--;
                if (activeFrame < 0) {
                    activeFrame = NUM_FRAMES - 1;
                }
                frameList[activeFrame].setSelected(true);
            } while ((frameList[activeFrame].isClosed() || frameList[activeFrame].isIcon() || (!frameList[activeFrame].isVisible())) && (initialFrame != activeFrame));
        } catch (PropertyVetoException ex) {
            log.warn("Exception selecting internal frame:{}", ex.getMessage());
        }
    }

    @Override
    public void notifyAddressChosen(LocoAddress l) {
    }

    @Override
    public void notifyAddressReleased(LocoAddress la) {
        if (throttle == null) {
            log.debug("notifyAddressReleased() throttle already null, called for loc {}",la);
            return;
        }
        if (allThrottlesTableModel.getNumberOfEntriesFor((DccLocoAddress) throttle.getLocoAddress()) == 1 )  {
            throttleManager.removeListener(throttle.getLocoAddress(), allThrottlesTableModel);
        }        
        throttle = null;
        setLastUsedSaveFile(null);        
        setFrameTitle();
        throttleWindow.updateGUI(); 
        allThrottlesTableModel.fireTableDataChanged();        
    }

    @Override
    public void notifyAddressThrottleFound(DccThrottle t) {
        if (throttle != null) {
            log.debug("notifyAddressThrottleFound() throttle non null, called for loc {}",t.getLocoAddress());
            return;
        }
        throttle = t;
        if ((InstanceManager.getDefault(ThrottlesPreferences.class).isUsingExThrottle())
                && (InstanceManager.getDefault(ThrottlesPreferences.class).isAutoLoading()) && (addressPanel != null)) {
            if ((addressPanel.getRosterEntry() != null)
                    && ((getLastUsedSaveFile() == null) || (getLastUsedSaveFile().compareTo(getDefaultThrottleFolder() + addressPanel.getRosterEntry().getId().trim() + ".xml") != 0))) {
                loadThrottle(getDefaultThrottleFolder() + addressPanel.getRosterEntry().getId().trim() + ".xml");
                setLastUsedSaveFile(getDefaultThrottleFolder() + addressPanel.getRosterEntry().getId().trim() + ".xml");
            } else if ((addressPanel.getRosterEntry() == null)
                    && ((getLastUsedSaveFile() == null) || (getLastUsedSaveFile().compareTo(getDefaultThrottleFolder() + addressPanel.getCurrentAddress()+ ".xml") != 0))) {
                loadThrottle(getDefaultThrottleFolder() + throttle.getLocoAddress().getNumber() + ".xml");
                setLastUsedSaveFile(getDefaultThrottleFolder() + throttle.getLocoAddress().getNumber() + ".xml");
            }
        } else {
            if ((addressPanel != null) && (addressPanel.getRosterEntry() == null)) { // no known roster entry
                loadDefaultThrottle();
            }
        }
        setFrameTitle();
        throttleWindow.updateGUI();
        throttleManager.attachListener(throttle.getLocoAddress(), allThrottlesTableModel);        
        allThrottlesTableModel.fireTableDataChanged();
    }

    
    @Override
    public void notifyConsistAddressChosen(LocoAddress l) {
        notifyAddressChosen(l);
    }

    
    @Override
    public void notifyConsistAddressReleased(LocoAddress la) {
        notifyAddressReleased(la);
    }

    @Override
    public void notifyConsistAddressThrottleFound(DccThrottle throttle) {
        notifyAddressThrottleFound(throttle);
    }

    public String getLastUsedSaveFile() {
        return lastUsedSaveFile;
    }

    public void setLastUsedSaveFile(String lusf) {
        lastUsedSaveFile = lusf;
        throttleWindow.updateGUI();
    }

    // some utilities to turn a component background transparent
    public static void setTransparentBackground(JComponent jcomp) {
        if (jcomp instanceof JPanel) //OS X: Jpanel components are enough
        {
            jcomp.setBackground(new Color(0, 0, 0, 0));
        }
        setTransparentBackground(jcomp.getComponents());
    }

    public static void setTransparentBackground(Component[] comps) {
        for (Component comp : comps) {
            try {
                if (comp instanceof JComponent) {
                    setTransparentBackground((JComponent) comp);
                }
            } catch (Exception e) {
                // Do nothing, just go on
            }
        }
    }

// some utilities to turn a component background transparent
    public static void setTransparent(JComponent jcomp) {
        setTransparent(jcomp, true);
    }

    public static void setTransparent(JComponent jcomp, boolean transparency) {
        if (jcomp instanceof JPanel) { //OS X: Jpanel components are enough
            jcomp.setOpaque(!transparency);
        }
        setTransparent(jcomp.getComponents(), transparency);
    }

    private static void setTransparent(Component[] comps, boolean transparency) {
        for (Component comp : comps) {
            try {
                if (comp instanceof JComponent) {
                    setTransparent((JComponent) comp, transparency);
                }
            } catch (Exception e) {
                // Do nothing, just go on
            }
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ThrottleFrame.class);
}
