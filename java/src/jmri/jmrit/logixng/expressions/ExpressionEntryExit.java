package jmri.jmrit.logixng.expressions;

import java.beans.*;
import java.util.*;

import javax.annotation.Nonnull;

import jmri.*;
import jmri.jmrit.entryexit.DestinationPoints;
import jmri.jmrit.logixng.*;
import jmri.jmrit.logixng.util.LogixNG_SelectNamedBean;
import jmri.jmrit.logixng.util.ReferenceUtil;
import jmri.jmrit.logixng.util.parser.*;
import jmri.jmrit.logixng.util.parser.ExpressionNode;
import jmri.jmrit.logixng.util.parser.RecursiveDescentParser;
import jmri.util.TypeConversionUtil;

/**
 * This expression sets the state of a DestinationPoints.
 *
 * @author Daniel Bergqvist Copyright 2018
 */
public class ExpressionEntryExit extends AbstractDigitalExpression
        implements PropertyChangeListener {

    private final LogixNG_SelectNamedBean<DestinationPoints> _selectNamedBean =
            new LogixNG_SelectNamedBean<>(
                    this, DestinationPoints.class, InstanceManager.getDefault(jmri.jmrit.entryexit.EntryExitPairs.class), this);
    private Is_IsNot_Enum _is_IsNot = Is_IsNot_Enum.Is;
    private NamedBeanAddressing _stateAddressing = NamedBeanAddressing.Direct;
    private EntryExitState _entryExitState = EntryExitState.Active;
    private String _stateReference = "";
    private String _stateLocalVariable = "";
    private String _stateFormula = "";
    private ExpressionNode _stateExpressionNode;

    public ExpressionEntryExit(String sys, String user)
            throws BadUserNameException, BadSystemNameException {
        super(sys, user);
    }

    @Override
    public Base getDeepCopy(Map<String, String> systemNames, Map<String, String> userNames) throws ParserException {
        DigitalExpressionManager manager = InstanceManager.getDefault(DigitalExpressionManager.class);
        String sysName = systemNames.get(getSystemName());
        String userName = userNames.get(getSystemName());
        if (sysName == null) sysName = manager.getAutoSystemName();
        ExpressionEntryExit copy = new ExpressionEntryExit(sysName, userName);
        copy.setComment(getComment());
        _selectNamedBean.copy(copy._selectNamedBean);
        copy.setBeanState(_entryExitState);
        copy.set_Is_IsNot(_is_IsNot);
        copy.setStateAddressing(_stateAddressing);
        copy.setStateFormula(_stateFormula);
        copy.setStateLocalVariable(_stateLocalVariable);
        copy.setStateReference(_stateReference);
        return manager.registerExpression(copy);
    }

    public LogixNG_SelectNamedBean<DestinationPoints> getSelectNamedBean() {
        return _selectNamedBean;
    }

    public void set_Is_IsNot(Is_IsNot_Enum is_IsNot) {
        _is_IsNot = is_IsNot;
    }

    public Is_IsNot_Enum get_Is_IsNot() {
        return _is_IsNot;
    }

    public void setStateAddressing(NamedBeanAddressing addressing) throws ParserException {
        _stateAddressing = addressing;
        parseStateFormula();
    }

    public NamedBeanAddressing getStateAddressing() {
        return _stateAddressing;
    }

    public void setBeanState(EntryExitState state) {
        _entryExitState = state;
    }

    public EntryExitState getBeanState() {
        return _entryExitState;
    }

    public void setStateReference(@Nonnull String reference) {
        if ((! reference.isEmpty()) && (! ReferenceUtil.isReference(reference))) {
            throw new IllegalArgumentException("The reference \"" + reference + "\" is not a valid reference");
        }
        _stateReference = reference;
    }

    public String getStateReference() {
        return _stateReference;
    }

    public void setStateLocalVariable(@Nonnull String localVariable) {
        _stateLocalVariable = localVariable;
    }

    public String getStateLocalVariable() {
        return _stateLocalVariable;
    }

    public void setStateFormula(@Nonnull String formula) throws ParserException {
        _stateFormula = formula;
        parseStateFormula();
    }

    public String getStateFormula() {
        return _stateFormula;
    }

    private void parseStateFormula() throws ParserException {
        if (_stateAddressing == NamedBeanAddressing.Formula) {
            Map<String, Variable> variables = new HashMap<>();

            RecursiveDescentParser parser = new RecursiveDescentParser(variables);
            _stateExpressionNode = parser.parseExpression(_stateFormula);
        } else {
            _stateExpressionNode = null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public LogixNG_Category getCategory() {
        return LogixNG_Category.ITEM;
    }

    private String getNewState() throws JmriException {

        switch (_stateAddressing) {
            case Reference:
                return ReferenceUtil.getReference(
                        getConditionalNG().getSymbolTable(), _stateReference);

            case LocalVariable:
                SymbolTable symbolTable =
                        getConditionalNG().getSymbolTable();
                return TypeConversionUtil
                        .convertToString(symbolTable.getValue(_stateLocalVariable), false);

            case Formula:
                return _stateExpressionNode != null
                        ? TypeConversionUtil.convertToString(
                                _stateExpressionNode.calculate(
                                        getConditionalNG().getSymbolTable()), false)
                        : null;

            default:
                throw new IllegalArgumentException("invalid _addressing state: " + _stateAddressing.name());
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean evaluate() throws JmriException {
        DestinationPoints destinationPoints = _selectNamedBean.evaluateNamedBean(getConditionalNG());

        if (destinationPoints == null) return false;

        EntryExitState checkEntryExitState;

        if ((_stateAddressing == NamedBeanAddressing.Direct)) {
            checkEntryExitState = _entryExitState;
        } else {
            checkEntryExitState = EntryExitState.valueOf(getNewState());
        }

        switch (checkEntryExitState) {
            case Inactive:
            case Active:
            case Other:
                EntryExitState currentEntryExitState = EntryExitState.get(destinationPoints.getState());
                if (_is_IsNot == Is_IsNot_Enum.Is) {
                    return currentEntryExitState == checkEntryExitState;
                } else {
                    return currentEntryExitState != checkEntryExitState;
                }
            case Reversed:
                if (_is_IsNot == Is_IsNot_Enum.Is) {
                    return destinationPoints.isReversed();
                } else {
                    return !destinationPoints.isReversed();
                }
            case BiDirection:
                if (_is_IsNot == Is_IsNot_Enum.Is) {
                    return !destinationPoints.isUniDirection();
                } else {
                    return destinationPoints.isUniDirection();
                }
            default:
                throw new IllegalArgumentException("checkEntryExitState has unknown value: "+checkEntryExitState.name());
        }
    }

    @Override
    public FemaleSocket getChild(int index) throws IllegalArgumentException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int getChildCount() {
        return 0;
    }

    @Override
    public String getShortDescription(Locale locale) {
        return Bundle.getMessage(locale, "EntryExit_Short");
    }

    @Override
    public String getLongDescription(Locale locale) {
        String namedBean = _selectNamedBean.getDescription(locale);
        String state;

        switch (_stateAddressing) {
            case Direct:
                state = Bundle.getMessage(locale, "AddressByDirect", _entryExitState._text);
                break;

            case Reference:
                state = Bundle.getMessage(locale, "AddressByReference", _stateReference);
                break;

            case LocalVariable:
                state = Bundle.getMessage(locale, "AddressByLocalVariable", _stateLocalVariable);
                break;

            case Formula:
                state = Bundle.getMessage(locale, "AddressByFormula", _stateFormula);
                break;

            default:
                throw new IllegalArgumentException("invalid _stateAddressing state: " + _stateAddressing.name());
        }

        return Bundle.getMessage(locale, "EntryExit_Long", namedBean, _is_IsNot.toString(), state);
    }

    /** {@inheritDoc} */
    @Override
    public void setup() {
        getSelectNamedBean().setup();
    }

    /** {@inheritDoc} */
    @Override
    public void registerListenersForThisClass() {
        if (!_listenersAreRegistered) {
            _selectNamedBean.addPropertyChangeListener("active", this);
            _selectNamedBean.registerListeners();
            _listenersAreRegistered = true;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void unregisterListenersForThisClass() {
        if (_listenersAreRegistered) {
            _selectNamedBean.removePropertyChangeListener("active", this);
            _selectNamedBean.unregisterListeners();
            _listenersAreRegistered = false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        getConditionalNG().execute();
    }

    /** {@inheritDoc} */
    @Override
    public void disposeMe() {
    }

    public enum EntryExitState {
        Inactive(0x04, Bundle.getMessage("EntryExitStateInactive")),
        Active(0x02, Bundle.getMessage("EntryExitStateActive")),
        Other(-1, Bundle.getMessage("EntryExitOtherStatus")),
        Separator1(-1, Base.SEPARATOR),
        Reversed(-1, Bundle.getMessage("EntryExitReversed")),
        Separator2(-1, Base.SEPARATOR),
        BiDirection(-1, Bundle.getMessage("EntryExitBiDirection"));

        private final int _id;
        private final String _text;

        private EntryExitState(int id, String text) {
            this._id = id;
            this._text = text;
        }

        static public EntryExitState get(int id) {
            switch (id) {
                case 0x04:
                    return Inactive;

                case 0x02:
                    return Active;

                default:
                    return Other;
            }
        }

        public int getID() {
            return _id;
        }

        @Override
        public String toString() {
            return _text;
        }

    }

    /** {@inheritDoc} */
    @Override
    public void getUsageDetail(int level, NamedBean bean, List<NamedBeanUsageReport> report, NamedBean cdl) {
        log.debug("getUsageReport :: ExpressionEntryExit: bean = {}, report = {}", cdl, report);
        _selectNamedBean.getUsageDetail(level, bean, report, cdl, this, LogixNG_SelectNamedBean.Type.Expression);
    }

    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExpressionEntryExit.class);

}
