// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldpathupdate;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentCalculator;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.serialization.DocumentUpdateReader;
import com.yahoo.document.serialization.VespaDocumentSerializer6;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Thomas Gundersen
 */
public class AssignFieldPathUpdate extends FieldPathUpdate {

    class SimpleAssignIteratorHandler extends FieldPathIteratorHandler {
        FieldValue newValue;
        boolean removeIfZero;
        boolean createMissingPath;

        SimpleAssignIteratorHandler(FieldValue newValue, boolean removeIfZero, boolean createMissingPath) {
            this.newValue = newValue;
            this.removeIfZero = removeIfZero;
            this.createMissingPath = createMissingPath;
        }

        @Override
        public ModificationStatus doModify(FieldValue fv) {
            if (!fv.getDataType().equals(newValue.getDataType())) {
                throw new IllegalArgumentException("Trying to assign " + newValue + " of type " + newValue.getDataType() + " to an instance of " + fv.getDataType());
            } else {
                if (removeIfZero && (newValue instanceof NumericFieldValue) && ((NumericFieldValue)newValue).getNumber().longValue() == 0) {
                    return ModificationStatus.REMOVED;
                }
                fv.assign(newValue);
            }
            return ModificationStatus.MODIFIED;
        }

        @Override
        public boolean createMissingPath() {
            return createMissingPath;
        }

        @Override
        public boolean onComplex(FieldValue fv) {
            return false;
        }
    }

    class MathAssignIteratorHandler extends FieldPathIteratorHandler {
        DocumentCalculator calc;
        Document doc;
        boolean removeIfZero;
        boolean createMissingPath;

        MathAssignIteratorHandler(String expression, Document doc, boolean removeIfZero, boolean createMissingPath) throws ParseException {
            this.calc = new DocumentCalculator(expression);
            this.doc = doc;
            this.removeIfZero = removeIfZero;
            this.createMissingPath = createMissingPath;
        }

        @Override
        public ModificationStatus doModify(FieldValue fv) {
            if (fv instanceof NumericFieldValue) {
                Map<String, Object> vars = new HashMap<String, Object>();
                for (Map.Entry<String, IndexValue> entry : getVariables().entrySet()) {
                    if (entry.getValue().getKey() != null && entry.getValue().getKey() instanceof NumericFieldValue) {
                        vars.put(entry.getKey(), ((NumericFieldValue)entry.getValue().getKey()).getNumber());
                    } else {
                        vars.put(entry.getKey(), entry.getValue().getIndex());
                    }
                }
                vars.put("value", ((NumericFieldValue)fv).getNumber());

                try {
                    Number d = calc.evaluate(doc, vars);
                    if (removeIfZero && d.longValue() == 0) {
                        return ModificationStatus.REMOVED;
                    } else {
                        fv.assign(calc.evaluate(doc, vars));
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore divide by zero
                    return ModificationStatus.NOT_MODIFIED;
                }
            } else {
                throw new IllegalArgumentException("Trying to perform arithmetic on " + fv + " of type " + fv.getDataType());
            }
            return ModificationStatus.MODIFIED;
        }

        @Override
        public boolean createMissingPath() {
            return createMissingPath;
        }

        @Override
        public boolean onComplex(FieldValue fv) {
            return false;
        }
    }

    FieldValue fieldValue = null;
    String expression = null;
    boolean createMissingPath = true;
    boolean removeIfZero = false;

    // Flag bits
    public static final int ARITHMETIC_EXPRESSION = 1;
    public static final int REMOVE_IF_ZERO = 2;
    public static final int CREATE_MISSING_PATH = 4;

    /**
     * Creates an assignment update that overwrites the old value with the given new value.
     *
     * @param type The document type the assignment works on.
     * @param fieldPath The field path of the field to be overwritten.
     * @param whereClause A document selection string that selects documents and variables to be updated.
     * @param newValue The new value of the assignment.
     */
    public AssignFieldPathUpdate(DocumentType type, String fieldPath, String whereClause, FieldValue newValue) {
        super(FieldPathUpdate.Type.ASSIGN, type, fieldPath, whereClause);
        setNewValue(newValue);
    }

    public AssignFieldPathUpdate(DocumentType type, String fieldPath, FieldValue newValue) {
        super(FieldPathUpdate.Type.ASSIGN, type, fieldPath, null);
        setNewValue(newValue);
    }

    /**
     * Creates an assign statement based on a mathematical expression.
     *
     * @param type The document type the assignment works on.
     * @param fieldPath The field path of the field to be overwritten.
     * @param whereClause A document selection string that selects documents and variables to be updated.
     * @param expression The mathematical expression to apply. Use $value to signify the previous value of the field.
     */
    public AssignFieldPathUpdate(DocumentType type, String fieldPath, String whereClause, String expression) {
        super(FieldPathUpdate.Type.ASSIGN, type, fieldPath, whereClause);
        setExpression(expression);
    }

    /**
     * Creates an assign update from a serialized object.
     *
     * @param type The document type the assignment will work on.
     * @param reader A reader that can deserialize something into this object.
     */
    public AssignFieldPathUpdate(DocumentType type, DocumentUpdateReader reader) {
        super(FieldPathUpdate.Type.ASSIGN, type, reader);
        reader.read(this);
    }

    public AssignFieldPathUpdate(DocumentType type, String fieldPath) {
        super(FieldPathUpdate.Type.ASSIGN, type, fieldPath, null);
    }

    /**
     * Turns this assignment into a literal one.
     *
     * @param value The new value to assign to the document.
     */
    public void setNewValue(FieldValue value) {
        fieldValue = value;
        expression = null;
    }

    /**
     *
     * @return Returns the value to assign, or null if this is a mathematical expression.
     */
    public FieldValue getNewValue() {
        return fieldValue;
    }

    /**
     * Turns this assignment into a mathematical expression assignment.
     *
     * @param value The expression to use for assignment.
     */
    public void setExpression(String value) {
        expression = value;
        fieldValue = null;
    }

    /**
     *
     * @return Returns the arithmetic expression to assign, or null if this is not a mathematical expression.
     */
    public String getExpression() {
        return expression;
    }

    /**
     * If set to true, and the new value assigned evaluates to a numeric value of 0, removes the value instead of setting it.
     * Default is false.
     */
    public void setRemoveIfZero(boolean removeIfZero) {
        this.removeIfZero = removeIfZero;
    }

    /**
     * If set to true, and any part of the field path specified does not exist (except for array indexes), we create the path as necessary.
     * Default is true.
     */
    public void setCreateMissingPath(boolean createMissingPath) {
        this.createMissingPath = createMissingPath;
    }

    /**
     *
     * @return Returns true if this assignment is an arithmetic operation.
     */
    public boolean isArithmetic() {
        return expression != null;
    }

    FieldPathIteratorHandler getIteratorHandler(Document doc) {
        if (expression != null) {
            try {
                return new MathAssignIteratorHandler(expression, doc, removeIfZero, createMissingPath);
            } catch (ParseException e) {
                return null;
            }
        } else {
            return new SimpleAssignIteratorHandler(fieldValue, removeIfZero, createMissingPath);
        }
    }

    @Override
    public void serialize(VespaDocumentSerializer6 data) {
        data.write(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        AssignFieldPathUpdate that = (AssignFieldPathUpdate) o;

        if (createMissingPath != that.createMissingPath) return false;
        if (removeIfZero != that.removeIfZero) return false;
        if (expression != null ? !expression.equals(that.expression) : that.expression != null) return false;
        if (fieldValue != null ? !fieldValue.equals(that.fieldValue) : that.fieldValue != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (fieldValue != null ? fieldValue.hashCode() : 0);
        result = 31 * result + (expression != null ? expression.hashCode() : 0);
        result = 31 * result + (createMissingPath ? 1 : 0);
        result = 31 * result + (removeIfZero ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Assign: " + super.toString() + " : " + (isArithmetic() ? getExpression() : getNewValue().toString());
    }

    public boolean getCreateMissingPath() {
        return createMissingPath;
    }

    public boolean getRemoveIfZero() {
        return removeIfZero;
    }

    public FieldValue getFieldValue() {
        return fieldValue;
    }

}
