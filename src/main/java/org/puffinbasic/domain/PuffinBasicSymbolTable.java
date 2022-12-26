package org.puffinbasic.domain;

import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.puffinbasic.domain.STObjects.ArrayReferenceValue;
import org.puffinbasic.domain.STObjects.PuffinBasicAtomTypeId;
import org.puffinbasic.domain.STObjects.PuffinBasicType;
import org.puffinbasic.domain.STObjects.STEntry;
import org.puffinbasic.domain.STObjects.STLValue;
import org.puffinbasic.domain.STObjects.STRef;
import org.puffinbasic.domain.STObjects.STTmp;
import org.puffinbasic.domain.STObjects.STVariable;
import org.puffinbasic.domain.STObjects.StructType;
import org.puffinbasic.domain.Scope.GlobalScope;
import org.puffinbasic.domain.Variable.VariableName;
import org.puffinbasic.error.PuffinBasicInternalError;
import org.puffinbasic.error.PuffinBasicRuntimeError;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.puffinbasic.domain.STObjects.PuffinBasicAtomTypeId.COMPOSITE;
import static org.puffinbasic.domain.STObjects.PuffinBasicAtomTypeId.DOUBLE;
import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.BAD_FIELD;
import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.ILLEGAL_FUNCTION_PARAM;
import static org.puffinbasic.error.PuffinBasicRuntimeError.ErrorCode.MISSING_STRUCT;

public class PuffinBasicSymbolTable {

    public interface VariableConsumer {
        void consume(int id, STVariable entry, Variable variable);
    }

    public static final int NULL_ID = -1;

    private final Char2ObjectMap<PuffinBasicAtomTypeId> defaultDataTypes;
    private final Object2ObjectMap<String, StructType> userDefinedTypes;
    private final Object2IntMap<String> labelNameToId;
    private final AtomicInteger idmaker;
    private Scope currentScope;
    private int lastId;
    private int lastLastId;
    private STEntry lastEntry;
    private STEntry lastLastEntry;

    public PuffinBasicSymbolTable() {
        this.defaultDataTypes = new Char2ObjectOpenHashMap<>();
        this.userDefinedTypes = new Object2ObjectOpenHashMap<>();
        this.labelNameToId = new Object2IntOpenHashMap<>();
        this.idmaker = new AtomicInteger();
        this.currentScope = new GlobalScope();
        this.lastId = this.lastLastId = -1;
    }

    private int generateNextId() {
        return idmaker.incrementAndGet();
    }

    public Scope getCurrentScope() {
        return currentScope;
    }

    private Optional<Scope> findScope(Predicate<Scope> predicate) {
        Scope scope = getCurrentScope();
        while (scope != null) {
            if (predicate.test(scope)) {
                return Optional.of(scope);
            } else {
                scope = scope.getSearchScope();
            }
        }
        return Optional.empty();
    }

    private STEntry getEntry(int id) {
        Scope scope = getCurrentScope();
        STEntry entry = scope.getNullableEntry(id);
        if (entry != null) {
            return entry;
        } else {
            scope = scope.getParent();
            while (scope != null) {
                entry = scope.getNullableEntry(id);
                if (entry != null) {
                    return entry;
                }
                scope = scope.getParent();
            }
        }
        throw new PuffinBasicInternalError("Failed to find entry for id: " + id);
    }

    public STEntry get(int id) {
        // Cache for better performance
        if (id == lastId) {
            return lastEntry;
        }
        if (id == lastLastId) {
            return lastLastEntry;
        }
        lastLastId = lastId;
        lastLastEntry = lastEntry;
        lastId = id;
        lastEntry = getEntry(id);
        return lastEntry;
    }

    public int getCompositeVariableIdForVariable(VariableName variableName) {
        Scope scope = findScope(s -> s.containsVariable(variableName)).orElse(getCurrentScope());
        int id = scope.getIdForVariable(variableName);
        if (id == -1) {
            throw new PuffinBasicInternalError("Failed to find variable: " + variableName);
        }
        return id;
    }

    public STEntry getVariable(int id) {
        STEntry entry = get(id);
        if (!entry.isLValue()) {
            throw new PuffinBasicRuntimeError(
                    ILLEGAL_FUNCTION_PARAM,
                    "Entry for id: " + id + " is not a variable"
            );
        }
        return entry;
    }

    public int addVariableOrUDF(
            VariableName variableName,
            Function<VariableName, Variable> variableCreator,
            VariableConsumer consumer)
    {
        Scope scope = findScope(s -> s.containsVariable(variableName)).orElse(getCurrentScope());
        int id = scope.getIdForVariable(variableName);
        STVariable entry;
        if (id == -1) {
            id = generateNextId();
            scope.putVariable(variableName, id);
            Variable variable = variableCreator.apply(variableName);
            entry = variableName.getDataType().createVariableEntry(variable);
            scope.putEntry(id, entry);
        } else {
            entry = (STVariable) get(id);
        }
        consumer.consume(id, entry, entry.getVariable());
        return id;
    }

    public int addCompositeVariable(
            VariableName variableName,
            STVariable variable)
    {
        Scope scope = findScope(s -> s.containsVariable(variableName)).orElse(getCurrentScope());
        int id = generateNextId();
        scope.putVariable(variableName, id);
        scope.putEntry(id, variable);
        return id;
    }

    public int addLabel(String label) {
        int id = labelNameToId.getOrDefault(label, -1);
        if (id == -1) {
            id = addLabel();
            labelNameToId.put(label, id);
        }
        return id;
    }

    public int addLabel() {
        Scope scope = getCurrentScope();
        int id = generateNextId();
        STObjects.STLabel entry = new STObjects.STLabel();
        scope.putEntry(id, entry);
        return id;
    }

    public int addGotoTarget() {
        Scope scope = getCurrentScope();
        int id = generateNextId();
        STTmp entry = PuffinBasicAtomTypeId.INT32.createTmpEntry();
        scope.putEntry(id, entry);
        return id;
    }

    public int addArrayReference(STLValue lvalue) {
        STObjects.STValue ref = new ArrayReferenceValue(lvalue);
        int id = generateNextId();
        STEntry entry = new STLValue(ref, lvalue.getType());
        getCurrentScope().putEntry(id, entry);
        return id;
    }

    public int addTmp(PuffinBasicType type, Consumer<STEntry> consumer) {
        Scope scope = getCurrentScope();
        int id = generateNextId();
        STObjects.AbstractSTEntry entry = type.canBeLValue() ? new STLValue(null, type) : new STTmp(null, type);
        entry.createAndSetInstance(this);
        scope.putEntry(id, entry);
        consumer.accept(entry);
        return id;
    }

    public int addTmp(PuffinBasicAtomTypeId dataType, Consumer<STEntry> consumer) {
        Scope scope = getCurrentScope();
        int id = generateNextId();
        STTmp entry = dataType.createTmpEntry();
        scope.putEntry(id, entry);
        consumer.accept(entry);
        return id;
    }

    public int addRef(PuffinBasicType type) {
        Scope scope = getCurrentScope();
        int id = generateNextId();
        STRef entry = new STRef(type);
        scope.putEntry(id, entry);
        return id;
    }

    public int addTmpCompatibleWith(int srcId) {
        Scope scope = getCurrentScope();
        PuffinBasicAtomTypeId dataType = scope.getEntry(srcId).getType().getAtomTypeId();
        int id = generateNextId();
        scope.putEntry(id, dataType.createTmpEntry());
        return id;
    }

    public PuffinBasicAtomTypeId getDataTypeFor(String varname, String suffix) {
        Scope scope = getCurrentScope();
        if (scope.containsVariable(new VariableName(varname, null, COMPOSITE))) {
            return COMPOSITE;
        }
        if (varname.length() == 0) {
            throw new PuffinBasicInternalError("Empty variable name: " + varname);
        }
        if (suffix == null) {
            char firstChar = varname.charAt(0);
            return defaultDataTypes.getOrDefault(firstChar, DOUBLE);
        } else {
            return PuffinBasicAtomTypeId.lookup(suffix);
        }
    }

    public void setDefaultDataType(char c, PuffinBasicAtomTypeId dataType) {
        defaultDataTypes.put(c, dataType);
    }

    public void addStructType(String name, StructType type) {
        userDefinedTypes.put(name, type);
    }

    public void checkUnused(String name) {
        if (userDefinedTypes.containsKey(name)) {
            throw new PuffinBasicRuntimeError(
                    BAD_FIELD,
                    "Name: " + name + " is already used!"
            );
        }
    }

    public StructType getStructType(String name) {
        StructType type = userDefinedTypes.get(name);
        if (type == null) {
            throw new PuffinBasicRuntimeError(
                    MISSING_STRUCT,
                    "Missing struct: " + name
            );
        }
        return type;
    }

    public void pushDeclarationScope(int funcId, boolean localScope) {
        currentScope = getCurrentScope().createChild(funcId, localScope);
    }

    public void pushRuntimeScope(int funcId, int callerInstrId) {
        Scope funcDeclScope = getCurrentScope().getChild(funcId);
        if (funcDeclScope == null) {
            throw new PuffinBasicInternalError("Failed to find scope for id: " + funcId);
        }
        currentScope = funcDeclScope.createRuntimeScope(callerInstrId);
    }

    public void popScope() {
        Scope parent = getCurrentScope().getParent();
        if (parent == null) {
            throw new PuffinBasicInternalError("Scope underflow!");
        }
        currentScope = parent;
    }
}
