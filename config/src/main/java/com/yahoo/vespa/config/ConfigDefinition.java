// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.yolean.Exceptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Represents one legal def file, or (internally) one array or inner array definition in a def file.
 *
 * @author Vegard Havdal
 */
public class ConfigDefinition {

    public static final Pattern namePattern = Pattern.compile("[a-zA-Z][a-zA-Z0-9-_]*");
    public static final Pattern namespacePattern = Pattern.compile("[a-zA-Z][a-zA-Z0-9-._]*");

    public static final Logger log = Logger.getLogger(ConfigDefinition.class.getName());
    private final String name;
    private final String namespace;
    ConfigDefinition parent = null;

    // TODO: Strings without default are null, could be not OK.
    private final Map<String, StringDef> stringDefs = new LinkedHashMap<>();
    private final Map<String, BoolDef> boolDefs = new LinkedHashMap<>();
    private final Map<String, IntDef> intDefs = new LinkedHashMap<>();
    private final Map<String, LongDef> longDefs = new LinkedHashMap<>();
    private final Map<String, DoubleDef> doubleDefs = new LinkedHashMap<>();
    private final Map<String, EnumDef> enumDefs = new LinkedHashMap<>();
    private final Map<String, RefDef> referenceDefs = new LinkedHashMap<>();
    private final Map<String, FileDef> fileDefs = new LinkedHashMap<>();
    private final Map<String, PathDef> pathDefs = new LinkedHashMap<>();
    private final Map<String, UrlDef> urlDefs = new LinkedHashMap<>();
    private final Map<String, ModelDef> modelDefs = new LinkedHashMap<>();
    private final Map<String, StructDef> structDefs = new LinkedHashMap<>();
    private final Map<String, InnerArrayDef> innerArrayDefs = new LinkedHashMap<>();
    private final Map<String, ArrayDef> arrayDefs = new LinkedHashMap<>();
    private final Map<String, LeafMapDef> leafMapDefs = new LinkedHashMap<>();
    private final Map<String, StructMapDef> structMapDefs = new LinkedHashMap<>();

    static final Integer INT_MIN = -0x80000000;
    static final Integer INT_MAX = 0x7fffffff;

    static final Long LONG_MIN = -0x8000000000000000L;
    static final Long LONG_MAX = 0x7fffffffffffffffL;

    private static final Double DOUBLE_MIN = -1e308d;
    private static final Double DOUBLE_MAX = 1e308d;

    public ConfigDefinition(String name, String namespace) {
        this.name = name;
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    /** Returns the parent ConfigDefinition, or null if this is the root. */
    private ConfigDefinition getParent() {
        return parent;
    }

    /** Returns the root ConfigDefinition, might be this. */
    private ConfigDefinition getRoot() {
        ConfigDefinition ancestor = this;
        while (ancestor.getParent() != null) {
            ancestor = ancestor.getParent();
        }
        return ancestor;
    }

    private static void defFail(String id, String val, String type, Exception e) {
        throw new IllegalArgumentException("Invalid value '" + val + "' for " + type + " '" + id + "': " +
                                           Exceptions.toMessageString(e));
    }

    public void verify(String id, String val) {
        if (stringDefs.containsKey(id)) {
            verifyString(id);
        } else if (enumDefs.containsKey(id)) {
            verifyEnum(id ,val);
        } else if (referenceDefs.containsKey(id)) {
            verifyReference(id);
        } else if (fileDefs.containsKey(id)) {
            verifyFile(id);
        } else if (pathDefs.containsKey(id)) {
            verifyPath(id);
        } else if (urlDefs.containsKey(id)) {
            verifyUrl(id);
        } else if (modelDefs.containsKey(id)) {
            verifyModel(id);
        } else if (boolDefs.containsKey(id)) {
            verifyBool(id, val);
        } else if (intDefs.containsKey(id)) {
            verifyInt(id, val);
        } else if (longDefs.containsKey(id)) {
            verifyLong(id, val);
        } else if (doubleDefs.containsKey(id)) {
            verifyDouble(id, val);
        } else if (structDefs.containsKey(id)) {
            verifyStruct(id);
        } else if (arrayDefs.containsKey(id)) {
            verifyArray(id);
        } else if (innerArrayDefs.containsKey(id)) {
            verifyInnerArray(id);
        } else if (leafMapDefs.containsKey(id)) {
		    verifyLeafMap(id);
        } else if (structMapDefs.containsKey(id)) {
            verifyStructMap(id);
        } else {
            throw new IllegalArgumentException("No such field in definition " + getRoot().getNamespace() + "." +
                                               getRoot().getName() + ": " + getAncestorString() + id);
        }
    }

    private void verifyDouble(String id, String val) {
        try {
            verifyDouble(id, Double.parseDouble(val));
        } catch (NumberFormatException e) {
            defFail(id, val, "double", e);
        }
    }

    private void verifyBool(String id, String val) {
        if ("true".equalsIgnoreCase(val) || "false".equalsIgnoreCase(val)) {
            verifyBool(id);
        } else {
            defFail(id, val, "bool", null);
        }
    }

    public void verify(String id) {
        verify(id, null);
    }

    /**
     * Compares def-versions. Examples: 2 is higher than 1, and 2-0-0 is higher than 1-2-2 but the same as 2.
     */
    public static class VersionComparator implements Comparator<String> {
        int[] parseVersion(String version) {
            int[] result = {0, 0, 0};
            String[] v = version.split("-");

            for (int i = 0; i < 3; i++) {
                if (v.length > i) result[i] = Integer.parseInt(v[i]);
            }

            return result;
        }

        public int compare(String o1, String o2) throws ClassCastException {
            int[] version1 = parseVersion(o1);
            int[] version2 = parseVersion(o2);

            for (int i = 0; i < 3; i ++) {
                int diff = version1[i] - version2[i];
                if (diff != 0) return diff;
            }

            return 0;
        }
    }

    /**
     * String based ("untyped") type specification used by parser and arrays. May have the name of the field which it describes.
     * The index number is used to export data in correct order.
     * @author vegardh
     *
     */
    public static class TypeSpec {
        private final String type; // TODO Class?
        private Integer index;
        private final String name;
        private final Object defVal;
        private final Object min;
        private final Object max;
        private final List<String> enumVals;

        public TypeSpec(String name, String type, Object defVal, String enumValsCommaSep, Object min, Object max) {
            this.name=name;
            this.type = type;
            this.defVal = defVal;
            this.enumVals = getEnumVals(enumValsCommaSep);
            this.min = min;
            this.max = max;
        }

        private List<String> getEnumVals(String commaSep) {
            if (commaSep==null) {
                return null;
            }
            List<String> in = new ArrayList<>();
            for (String val: commaSep.split(",")) {
                in.add(val.trim());
            }
            return in;
        }
        public String getName() {
            return name;
        }
        public String getType() {
            return type;
        }
        public Object getDef() {
            return defVal;
        }
        public Object getMin() {
            return min;
        }
        public Object getMax() {
            return max;
        }
        public List<String> getEnumVals() {
            return enumVals;
        }

        void checkValue(String id, String val, int index) {
            if ("int".equals(getType())) {
                checkInt(id, val, index);
            } else if ("long".equals(getType())) {
                checkLong(id, val, index);
            } else if ("double".equals(getType())) {
                checkDouble(id, val, index);
            } else if ("enum".equals(getType())) {
                checkEnum(id, val, index);
            }
        }

        private boolean checkEnum(String id, String val, int index) {
            if (!getEnumVals().contains(val)) {
                ConfigDefinition.failInvalidEnum(val, id, id+"["+index+"]");
                return false;
            }
            return true;
        }

        private void checkDouble(String id, String val, int index) {
            try {
                checkDouble(Double.parseDouble(val), id, index);
            } catch (NumberFormatException e) {
                ConfigDefinition.defFail(id, val, "double", e);
            }
        }

        private void checkLong(String id, String val, int index) {
            try {
                checkLong(Long.parseLong(val), id, index);
            } catch (NumberFormatException e) {
                ConfigDefinition.defFail(id, val, "long", e);
            }
        }

        private void checkInt(String id, String val, int index) {
            try {
                checkInt(Integer.parseInt(val), id, index);
            } catch (NumberFormatException e) {
                ConfigDefinition.defFail(id, val, "int", e);
            }
        }

        private void checkInt(Integer theVal, String id, int arrayIndex) {
            if ( ! "int".equals(getType()))
                throw new IllegalArgumentException("Illegal value '" + theVal + "' for array '" + id + "'");
            if (getMax() != null && theVal > (Integer)getMax())
                ConfigDefinition.failTooBig(theVal, getMax(), id, id+"["+arrayIndex+"]");
            if (getMin() != null && theVal < (Integer)getMin())
                ConfigDefinition.failTooSmall(theVal, getMin(), id, id+"["+arrayIndex+"]");
        }

        private void checkLong(Long theVal, String id, int arrayIndex) {
            if ( ! "long".equals(getType()))
                throw new IllegalArgumentException("Illegal value '" + theVal + "' for array '" + id + "'");
            if (getMax() != null && theVal > (Long)getMax())
                ConfigDefinition.failTooBig(theVal, getMax(), id, id+"["+arrayIndex+"]");
            if (getMin() != null && theVal < (Long)getMin())
                ConfigDefinition.failTooSmall(theVal, getMin(), id, id+"["+arrayIndex+"]");
        }

        private void checkDouble(Double theVal, String id, int arrayIndex) {
            if (!"double".equals(getType()))
                throw new IllegalArgumentException("Illegal value '" + theVal + "' for array " + id +
                                                   ", array type is " + getType());
            if (getMax() != null && (theVal > (Double)getMax()))
                ConfigDefinition.failTooBig(theVal, getMax(), id, id + "[" + arrayIndex + "]");
            if (getMin() != null && theVal < (Double)getMin())
                ConfigDefinition.failTooSmall(theVal, getMin(), id, id + "[" + arrayIndex + "]");
        }

        public void setIndex(Integer index) {
            this.index = index;
        }
        public Integer getIndex() {
            return index;
        }
    }

    /**
     * A ConfigDefinition that represents a struct, e.g. a.foo, a.bar where 'a' is the struct. Can be thought
     * of as an inner array with only one element.
     */
    public static class StructDef extends ConfigDefinition {
        StructDef(String name, ConfigDefinition parent) {
            super(name, parent.getNamespace());
            this.parent = parent;
        }
    }

    /**
     * An InnerArray def is a ConfigDefinition with n scalar types of defs, and maybe sub-InnerArrays
     * @author vegardh
     *
     */
    public static class InnerArrayDef extends ConfigDefinition {
        InnerArrayDef(String name, ConfigDefinition parent) {
            super(name, parent.getNamespace());
            this.parent = parent;
        }
    }

    /**
     * An array def is a ConfigDefinition with only one other type of scalar def.
     * @author vegardh
     *
     */
    public static class ArrayDef extends ConfigDefinition {
        private TypeSpec typeSpec;
        ArrayDef(String name, ConfigDefinition parent) {
            super(name, parent.getNamespace());
            this.parent = parent;
        }
        public TypeSpec getTypeSpec() {
            return typeSpec;
        }
        public void setTypeSpec(TypeSpec typeSpec) {
            this.typeSpec = typeSpec;
        }

        public void verify(String val, int index) {
            if (val != null && getTypeSpec() != null) {
                TypeSpec spec = getTypeSpec();
                spec.checkValue(getName(), val, index);
            }
        }
    }

    /** Def of a myMap{} int. */
    public static class LeafMapDef extends ConfigDefinition {
        private TypeSpec typeSpec;
        LeafMapDef(String name, ConfigDefinition parent) {
            super(name, parent.getNamespace());
            this.parent = parent;
        }
        public TypeSpec getTypeSpec() {
            return typeSpec;
        }
        public void setTypeSpec(TypeSpec typeSpec) {
            this.typeSpec = typeSpec;
        }
    }

    /** Def of a myMap{}.myInt int. */
    public static class StructMapDef extends ConfigDefinition {
        StructMapDef(String name, ConfigDefinition parent) {
            super(name, parent.getNamespace());
            this.parent = parent;
        }
    }

    /** A Default specification where instances _may_ have a default value. */
    public interface DefaultValued<T> {
        T getDefVal();
    }

    public static class EnumDef implements DefaultValued<String>{
        private final List<String> vals;
        private final String defVal;
        EnumDef(List<String> vals, String defVal) {
            if (defVal!=null && !vals.contains(defVal)) {
                throw new IllegalArgumentException("Def val "+defVal+" is not in given vals "+vals);
            }
            this.vals = vals;
            this.defVal = defVal;
        }
        List<String> getVals() {
            return vals;
        }

        @Override
        public String getDefVal() {
            return defVal;
        }
    }

    public static class StringDef implements DefaultValued<String> {
        private final String defVal;
        StringDef(String def) {
            this.defVal=def;
        }
        @Override
        public String getDefVal() {
            return defVal;
        }
    }

    public static class BoolDef implements DefaultValued<Boolean> {
       private final Boolean defVal;
       BoolDef(Boolean def) {
           this.defVal=def;
       }
       @Override
       public Boolean getDefVal() {
           return defVal;
       }
    }

    /**
     * The type is called 'double' in .def files, but it is a 64-bit IEE 754 double,
     * which means it must be represented as a double in Java.
     */
    public static class DoubleDef implements DefaultValued<Double> {
        private final Double defVal;
        private final Double min;
        private final Double max;
        DoubleDef(Double defVal, Double min, Double max) {
            super();
            this.defVal = defVal;
            this.min = Objects.requireNonNullElse(min, DOUBLE_MIN);
            this.max = Objects.requireNonNullElse(max, DOUBLE_MAX);
        }

        @Override
        public Double getDefVal() {
            return defVal;
        }
        Double getMin() {
            return min;
        }
        Double getMax() {
            return max;
        }
    }

    public static class IntDef implements DefaultValued<Integer>{
        private final Integer defVal;
        private final Integer min;
        private final Integer max;
        IntDef(Integer def, Integer min, Integer max) {
            super();
            this.defVal = def;
            this.min = Objects.requireNonNullElse(min, INT_MIN);
            this.max = Objects.requireNonNullElse(max, INT_MAX);
        }

        @Override
        public Integer getDefVal() {
            return defVal;
        }
        public Integer getMin() {
            return min;
        }
        public Integer getMax() {
            return max;
        }
    }

    public static class LongDef implements DefaultValued<Long>{
        private final Long defVal;
        private final Long min;
        private final Long max;
        LongDef(Long def, Long min, Long max) {
            super();
            this.defVal = def;
            this.min = Objects.requireNonNullElse(min, LONG_MIN);
            this.max = Objects.requireNonNullElse(max, LONG_MAX);
        }

        @Override
        public Long getDefVal() {
            return defVal;
        }
        public Long getMin() {
            return min;
        }
        public Long getMax() {
            return max;
        }
    }

    public static class RefDef implements DefaultValued<String>{
        private final String defVal;

        RefDef(String defVal) {
            super();
            this.defVal = defVal;
        }

        @Override
        public String getDefVal() {
            return defVal;
        }
    }

    public static class FileDef implements DefaultValued<String>{
        private final String defVal;

        FileDef(String defVal) {
            super();
            this.defVal = defVal;
        }

        @Override
        public String getDefVal() {
            return defVal;
        }
    }

    public static class PathDef implements DefaultValued<String> {
        private final String defVal;

        PathDef(String defVal) {
            this.defVal = defVal;
        }

        @Override
        public String getDefVal() {
            return defVal;
        }
    }

    public static class UrlDef implements DefaultValued<String> {
        private final String defVal;

        UrlDef(String defVal) {
            this.defVal = defVal;
        }

        @Override
        public String getDefVal() {
            return defVal;
        }
    }

    /** A value which may be either an url or a path. */
    public static class ModelDef {

    }

    public void addEnumDef(String id, EnumDef def) {
        enumDefs.put(id, def);
    }

    public void addInnerArrayDef(String id) {
        innerArrayDefs.put(id, new InnerArrayDef(id, this));
    }

    public void addLeafMapDef(String id) {
	leafMapDefs.put(id, new LeafMapDef(id, this));
    }

    public void addEnumDef(String id, List<String> vals, String defVal) {
        List<String> in = new ArrayList<>();
        for (String ins: vals) {
            in.add(ins.trim());
        }
        enumDefs.put(id, new EnumDef(in, defVal));
    }

    public void addEnumDef(String id, String valsCommaSep, String defVal) {
        String[] valArr = valsCommaSep.split(",");
        addEnumDef(id, Arrays.asList(valArr), defVal);
    }

    public void addStringDef(String id, String defVal) {
        stringDefs.put(id, new StringDef(defVal));
    }

    public void addStringDef(String id) {
        stringDefs.put(id, new StringDef(null));
    }

    public void addIntDef(String id, Integer defVal, Integer min, Integer max) {
        intDefs.put(id, new IntDef(defVal, min, max));
    }

    public void addIntDef(String id, Integer defVal) {
        addIntDef(id, defVal, INT_MIN, INT_MAX);
    }

    public void addIntDef(String id) {
        addIntDef(id, null);
    }

    public void addLongDef(String id, Long defVal, Long min, Long max) {
        longDefs.put(id, new LongDef(defVal, min, max));
    }

    public void addLongDef(String id, Long defVal) {
        addLongDef(id, defVal, LONG_MIN, LONG_MAX);
    }

    public void addLongDef(String id) {
        addLongDef(id, null);
    }

    public void addBoolDef(String id) {
        boolDefs.put(id, new BoolDef(null));
    }

    public void addBoolDef(String id, Boolean defVal) {
        boolDefs.put(id, new BoolDef(defVal));
    }

    public void addDoubleDef(String id, Double defVal, Double min, Double max) {
        doubleDefs.put(id, new DoubleDef(defVal, min, max));
    }

    public void addDoubleDef(String id, Double defVal) {
        addDoubleDef(id, defVal, DOUBLE_MIN, DOUBLE_MAX);
    }

    public void addDoubleDef(String id) {
        addDoubleDef(id, null);
    }

    public void addReferenceDef(String refId, String defVal) {
        referenceDefs.put(refId, new RefDef(defVal));
    }

    public void addReferenceDef(String refId) {
        referenceDefs.put(refId, new RefDef(null));
    }

    public void addFileDef(String refId, String defVal) {
        fileDefs.put(refId, new FileDef(defVal));
    }

    public void addFileDef(String refId) {
        fileDefs.put(refId, new FileDef(null));
    }

    public void addPathDef(String refId, String defVal) {
        pathDefs.put(refId, new PathDef(defVal));
    }

    public void addPathDef(String refId) {
        pathDefs.put(refId, new PathDef(null));
    }

    public void addUrlDef(String url, String defVal) {
        urlDefs.put(url, new UrlDef(defVal));
    }

    public void addModelDef(String modelName) {
        modelDefs.put(modelName, new ModelDef());
    }

    public void addUrlDef(String url) {
        urlDefs.put(url, new UrlDef(null));
    }

    public Map<String, StringDef> getStringDefs() {
        return stringDefs;
    }

    public Map<String, BoolDef> getBoolDefs() {
        return boolDefs;
    }

    public Map<String, IntDef> getIntDefs() {
        return intDefs;
    }

    public Map<String, LongDef> getLongDefs() {
        return longDefs;
    }

    public Map<String, DoubleDef> getDoubleDefs() {
        return doubleDefs;
    }

    public Map<String, RefDef> getReferenceDefs() {
        return referenceDefs;
    }

    public Map<String, FileDef> getFileDefs() {
        return fileDefs;
    }

    public Map<String, PathDef> getPathDefs() { return pathDefs; }

    public Map<String, UrlDef> getUrlDefs() { return urlDefs; }

    public Map<String, ModelDef> getModelDefs() { return modelDefs; }

    public Map<String, InnerArrayDef> getInnerArrayDefs() {
        return innerArrayDefs;
    }

    public Map<String, LeafMapDef> getLeafMapDefs() {
		return leafMapDefs;
	}

    public Map<String, StructMapDef> getStructMapDefs() {
		return structMapDefs;
	}

    public InnerArrayDef innerArrayDef(String name) {
        InnerArrayDef ret = innerArrayDefs.get(name);
        if (ret != null) return ret;
        ret = new InnerArrayDef(name, this);
        innerArrayDefs.put(name, ret);
        return ret;
    }

    public Map<String, StructDef> getStructDefs() {
        return structDefs;
    }

    public StructDef structDef(String name) {
        StructDef ret = structDefs.get(name);
        if (ret != null) return ret;
        ret = new StructDef(name, this);
        structDefs.put(name, ret);
        return ret;
    }

    public Map<String, EnumDef> getEnumDefs() {
        return enumDefs;
    }

    public ArrayDef arrayDef(String name) {
        ArrayDef ret = arrayDefs.get(name);
        if (ret != null) return ret;
        ret = new ArrayDef(name, this);
        arrayDefs.put(name, ret);
        return ret;
    }

    public Map<String, ArrayDef> getArrayDefs() {
        return arrayDefs;
    }

    public StructMapDef structMapDef(String name) {
        StructMapDef ret = structMapDefs.get(name);
        if (ret != null) return ret;
        ret = new StructMapDef(name, this);
        structMapDefs.put(name, ret);
        return ret;
    }

    public LeafMapDef leafMapDef(String name) {
        LeafMapDef ret = leafMapDefs.get(name);
        if (ret != null) return ret;
        ret = new LeafMapDef(name, this);
        leafMapDefs.put(name, ret);
        return ret;
    }

    /** Throws if the given value is not legal. */
    private void verifyDouble(String id, Double val) {
        DoubleDef def = doubleDefs.get(id);
        if (def == null)
            throw new IllegalArgumentException("No such double in " + verifyWarning(id));
        if (val == null) return;
        if (def.getMin() != null && val < def.getMin())
            failTooSmall(val, def.getMin(), toString(), getAncestorString()+id);
        if (def.getMax() != null && val > def.getMax())
            failTooBig(val, def.getMax(), toString(), getAncestorString()+id);
    }

    /** Throws if the given value is not legal. */
    private void verifyEnum(String id, String val) {
        EnumDef def = enumDefs.get(id);
        if (def == null)
            throw new IllegalArgumentException("No such enum in " + verifyWarning(id));
        if ( ! def.getVals().contains(val))
            throw new IllegalArgumentException("Invalid enum value '" + val + "' in def " + this +
                                               " enum '" + getAncestorString() + id + "'");
    }

    /**
     * Throws if the given value is not legal
     */
    private void verifyInt(String id, Integer val) {
        IntDef def = intDefs.get(id);
        if (def == null)
            throw new IllegalArgumentException("No such integer in " + verifyWarning(id));
        if (val == null) return;
        if (def.getMin() != null && val < def.getMin())
            failTooSmall(val, def.getMin(), name, id);
        if (def.getMax() != null && val > def.getMax())
            failTooBig(val, def.getMax(), name, id);
    }

    private void verifyInt(String id, String val) {
        try {
            verifyInt(id, Integer.parseInt(val));
        } catch (NumberFormatException e) {
            ConfigDefinition.defFail(id, val, "int", e);
        }
    }

    private void verifyLong(String id, String val) {
        try {
            verifyLong(id, Long.parseLong(val));
        } catch (NumberFormatException e) {
            ConfigDefinition.defFail(id, val, "long", e);
        }
    }

    /** Throws if the given value is not legal. */
    private void verifyLong(String id, Long val) {
        LongDef def = longDefs.get(id);
        if (def == null)
            throw new IllegalArgumentException("No such long in " + verifyWarning(id));
        if (val == null) return;
        if (def.getMin() != null && val < def.getMin())
            failTooSmall(val, def.getMin(), name, id);
        if (def.getMax() != null && val > def.getMax())
            failTooBig(val, def.getMax(), name, id);
    }

    private static void failTooSmall(Object val, Object min, String defName, String valKey) {
        throw new IllegalArgumentException("Value '" + valKey + "' outside range " +
                                           "in definition '" + defName + "': " + val + "<" + min);
    }

    private static void failTooBig(Object val, Object max, String defName, String valKey) {
        throw new IllegalArgumentException("Value '" + valKey + "' outside range " +
                                           "in definition '" + defName + "': " + val + ">" + max);
    }

    private static void failInvalidEnum(Object val, String defName, String defKey) {
        throw new IllegalArgumentException("Invalid enum value '" + val + "' for '" + defKey +
                                           "' in definition '" + defName);
    }

    private void verifyString(String id) {
        if ( ! stringDefs.containsKey(id))
            throw new IllegalArgumentException("No such string in " + verifyWarning(id));
    }

    private void verifyReference(String id) {
        if ( ! referenceDefs.containsKey(id))
            throw new IllegalArgumentException("No such reference in " + verifyWarning(id));
    }

    private void verifyFile(String id) {
        if ( ! fileDefs.containsKey(id))
            throw new IllegalArgumentException("No such file in " + verifyWarning(id));
    }

    private void verifyPath(String id) {
        if ( ! pathDefs.containsKey(id))
            throw new IllegalArgumentException("No such path in " + verifyWarning(id));
    }

    private void verifyUrl(String id) {
        if ( ! urlDefs.containsKey(id))
            throw new IllegalArgumentException("No such url in " + verifyWarning(id));
    }

    private void verifyModel(String field) {
        if ( ! modelDefs.containsKey(field))
            throw new IllegalArgumentException("No such model in " + verifyWarning(field));
    }

    private void verifyBool(String id) {
        if ( ! boolDefs.containsKey(id))
            throw new IllegalArgumentException("No such bool in " + verifyWarning(id));
    }

    private void verifyArray(String id) {
        String message = "No such array in " + verifyWarning(id);
        if ( ! arrayDefs.containsKey(id)) {
            if (innerArrayDefs.containsKey(id))
                message += ". However, the definition does contain an inner array with the same name";
            throw new IllegalArgumentException(message);
        }
    }

    private void verifyInnerArray(String id) {
        String message = "No such inner array in " + verifyWarning(id);
        if ( ! innerArrayDefs.containsKey(id)) {
            if (arrayDefs.containsKey(id))
                message += ". However, the definition does contain an array with the same name";
            throw new IllegalArgumentException(message);
        }
    }

    private void verifyStruct(String id) {
        if ( ! structDefs.containsKey(id))
            throw new IllegalArgumentException("No such struct in " + verifyWarning(id));
    }

    private void verifyLeafMap(String id) {
	    if ( ! leafMapDefs.containsKey(id))
            throw new IllegalArgumentException("No such leaf map in " + verifyWarning(id));
    }

    private void verifyStructMap(String id) {
    	if ( ! structMapDefs.containsKey(id))
            throw new IllegalArgumentException("No such struct map in " + verifyWarning(id));
	}

    private String verifyWarning(String id) {
        return "definition '" + getRoot().toString() + "': " + getAncestorString() + id;
    }

    /**
     * Returns a string composed of the ancestors of this ConfigDefinition, skipping the root (which is the name
     * of the .def file). For example, if this is an array called 'leafArray' and a child of 'innerArray' which
     * is again a child of 'myStruct', then the returned string will be 'myStruct.innerArray.leafArray.'
     * The trailing '.' is included for the caller's convenience.
     *
     * @return a string composed of the ancestors of this ConfigDefinition, not including the root
     */
    private String getAncestorString() {
        StringBuilder ret = new StringBuilder();
        ConfigDefinition ancestor = this;
        while (ancestor.getParent() != null) {
            ret.insert(0, ancestor.getName() + ".");
            ancestor = ancestor.getParent();
        }
        return ret.toString();
    }

    @Override
    public String toString() {
        return getNamespace() + "." + getName();
    }

}
