// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaxmlparser;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.serialization.DocumentUpdateReader;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public class VespaXMLUpdateReader extends VespaXMLFieldReader implements DocumentUpdateReader {
    public VespaXMLUpdateReader(String fileName, DocumentTypeManager docTypeManager) throws Exception {
        super(fileName, docTypeManager);
    }

    public VespaXMLUpdateReader(InputStream stream, DocumentTypeManager docTypeManager) throws Exception {
        super(stream, docTypeManager);
    }

    public VespaXMLUpdateReader(XMLStreamReader reader, DocumentTypeManager docTypeManager) {
        super(reader, docTypeManager);
    }

    private Optional<String> condition = Optional.empty();

    public Optional<String> getCondition() {
        return condition;
    }

    public boolean hasFieldPath() {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (reader.getAttributeName(i).toString().equals("fieldpath")) {
                return true;
            }
        }

        return false;
    }

    public void read(DocumentUpdate update) {
       try {
           // First fetch attributes.
           DocumentType doctype = null;

           for (int i = 0; i < reader.getAttributeCount(); i++) {
               final String attributeName = reader.getAttributeName(i).toString();
               final String attributeValue = reader.getAttributeValue(i);

               if ("documentid".equals(attributeName) || "id".equals(attributeName)) {
                   update.setId(new DocumentId(attributeValue));
               } else if ("documenttype".equals(attributeName) || "type".equals(attributeName)) {
                   doctype = docTypeManager.getDocumentType(attributeValue);
                   update.setDocumentType(doctype);
               } else if ("create-if-non-existent".equals(attributeName)) {
                   if ("true".equals(attributeValue)) {
                       update.setCreateIfNonExistent(true);
                   } else if ("false".equals(attributeValue)) {
                       update.setCreateIfNonExistent(false);
                   } else {
                       throw newDeserializeException("'create-if-non-existent' must be either 'true' or 'false', was '" + attributeValue +"'");
                   }
               } else if ("condition".equals(attributeName)) {
                   condition = Optional.of(attributeValue);
               }
           }

           if (doctype == null) {
               throw newDeserializeException("Must specify document type. " + reader.getLocation());
           }

           // Then fetch fields
           while (reader.hasNext()) {
               int type = reader.next();

               if (type == XMLStreamReader.START_ELEMENT) {
                   final String currentName = reader.getName().toString();
                   if (hasFieldPath()) {
                       if ("assign".equals(currentName)) {
                           update.addFieldPathUpdate(new AssignFieldPathUpdate(doctype, this));
                           skipToEnd("assign");
                       } else if ("add".equals(currentName)) {
                           update.addFieldPathUpdate(new AddFieldPathUpdate(doctype, this));
                           skipToEnd("add");
                       } else if ("remove".equals(currentName)) {
                           update.addFieldPathUpdate(new RemoveFieldPathUpdate(doctype, this));
                           skipToEnd("remove");
                       } else {
                           throw newDeserializeException("Unknown field path update operation " + reader.getName());
                       }
                   } else {
                       if ("assign".equals(currentName)) {
                           update.addFieldUpdate(readAssign(update));
                           skipToEnd("assign");
                       } else if ("add".equals(currentName)) {
                           update.addFieldUpdate(readAdd(update));
                           skipToEnd("add");
                       } else if ("remove".equals(currentName)) {
                           update.addFieldUpdate(readRemove(update));
                           skipToEnd("remove");
                       } else if ("alter".equals(currentName)) {
                           update.addFieldUpdate(readAlter(update));
                           skipToEnd("alter");
                       } else if ("increment".equals(currentName) ||
                               "decrement".equals(currentName) ||
                               "multiply".equals(currentName) ||
                               "divide".equals(currentName)) {
                           update.addFieldUpdate(readArithmeticField(update, currentName));
                           skipToEnd(currentName);
                       } else {
                           throw newDeserializeException("Unknown update operation " + reader.getName());
                       }
                   }
               } else if (type == XMLStreamReader.END_ELEMENT) {
                   return;
               }
           }
       } catch (XMLStreamException e) {
           throw newException(e);
       }
    }

    FieldUpdate readAdd(DocumentUpdate update) throws XMLStreamException {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if ("field".equals(reader.getAttributeName(i).toString())) {
                Field f = update.getDocumentType().getField(reader.getAttributeValue(i));

                FieldValue value = f.getDataType().createFieldValue();
                value.deserialize(f, this);

                if (value instanceof Array) {
                    List<FieldValue> l = ((Array)value).getValues();
                    return FieldUpdate.createAddAll(f, l);
                } else if (value instanceof WeightedSet) {
                    return FieldUpdate.createAddAll(f, ((WeightedSet) value));
                } else {
                    throw newDeserializeException("Add operation only applicable to multivalue lists");
                }

            }
        }
        throw newDeserializeException("Add update without field attribute");
    }


    FieldUpdate readRemove(DocumentUpdate update) throws XMLStreamException {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if ("field".equals(reader.getAttributeName(i).toString())) {
                Field f = update.getDocumentType().getField(reader.getAttributeValue(i));

                FieldValue value = f.getDataType().createFieldValue();
                value.deserialize(f, this);

                if (value instanceof Array) {
                    List<FieldValue> l = ((Array)value).getValues();
                    return FieldUpdate.createRemoveAll(f, l);
                } else if (value instanceof WeightedSet) {
                    return FieldUpdate.createRemoveAll(f, ((WeightedSet)value));
                } else {
                    throw newDeserializeException("Remove operation only applicable to multivalue lists");
                }

            }
        }
        throw newDeserializeException("Remove update without field attribute");
    }

    FieldUpdate readAssign(DocumentUpdate update) throws XMLStreamException {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if ("field".equals(reader.getAttributeName(i).toString())) {
                Field f = update.getDocumentType().getField(reader.getAttributeValue(i));

                if (f == null) {
                    throw newDeserializeException("Field " + reader.getAttributeValue(i) + " not found.");
                }

                FieldValue value = f.getDataType().createFieldValue();
                value.deserialize(f, this);
                return FieldUpdate.createAssign(f, value);
            }
        }
        throw newDeserializeException("Assignment update without field attribute");
    }


    FieldUpdate readAlter(DocumentUpdate update) throws XMLStreamException {
        Field f = null;
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if ("field".equals(reader.getAttributeName(i).toString())) {
                f = update.getDocumentType().getField(reader.getAttributeValue(i));
            }
        }

        if (f == null) {
            throw newDeserializeException("Alter update without \"field\" attribute");
        }

        FieldUpdate fu = FieldUpdate.create(f);

        while (reader.hasNext()) {
            int type = reader.next();
            if (type == XMLStreamReader.START_ELEMENT) {
                if ("increment".equals(reader.getName().toString()) ||
                    "decrement".equals(reader.getName().toString()) ||
                    "multiply".equals(reader.getName().toString()) ||
                    "divide".equals(reader.getName().toString())) {
                    update.addFieldUpdate(readArithmetic(update, reader.getName().toString(), f, fu));
                    skipToEnd(reader.getName().toString());
                } else {
                    throw newDeserializeException("Element \"" + reader.getName() + "\" not appropriate within alter element");
                }
            } else if (type == XMLStreamReader.END_ELEMENT) {
                break;
            }
        }

        return fu;
    }

    FieldUpdate readArithmeticField(DocumentUpdate update, String type) throws XMLStreamException {
        Field f = null;
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if ("field".equals(reader.getAttributeName(i).toString())) {
                f = update.getDocumentType().getField(reader.getAttributeValue(i));
            }
        }

        if (f == null) {
            throw newDeserializeException("Assignment update without \"field\" attribute");
        }

        FieldUpdate fu = FieldUpdate.create(f);
        readArithmetic(update, type, f, fu);
        return fu;
    }

    FieldUpdate readArithmetic(DocumentUpdate update, String type, Field f, FieldUpdate fu) throws XMLStreamException {
        Double by = null;

        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if ("by".equals(reader.getAttributeName(i).toString())) {
                by = Double.parseDouble(reader.getAttributeValue(i));
            }
        }

        if (by == null) {
            throw newDeserializeException("Assignment update without \"by\" attribute");
        }

        FieldValue key = null;
        do {
            reader.next();
            if (reader.getEventType() == XMLStreamReader.START_ELEMENT) {
                if ("key".equals(reader.getName().toString())) {
                    if (f.getDataType() instanceof WeightedSetDataType) {
                        DataType nestedType = ((WeightedSetDataType)f.getDataType()).getNestedType();
                        key = nestedType.createFieldValue();
                        key.deserialize(this);
                    } else if (f.getDataType() instanceof MapDataType) {
                        key = ((MapDataType)f.getDataType()).getKeyType().createFieldValue();
                        key.deserialize(this);
                    } else if (f.getDataType() instanceof ArrayDataType) {
                        key = new IntegerFieldValue(Integer.parseInt(reader.getElementText()));
                    } else {
                        throw newDeserializeException("Key tag only applicable for weighted sets and maps");
                    }
                    skipToEnd("key");
                } else {
                    throw newDeserializeException("\"" + reader.getName() + "\" not appropriate within " + type + " element.");
                }
            }
        } while (reader.getEventType() != XMLStreamReader.END_ELEMENT);

        if (key != null) {
            if ("increment".equals(type)) { fu.addValueUpdate(ValueUpdate.createIncrement(key, by)); }
            if ("decrement".equals(type)) { fu.addValueUpdate(ValueUpdate.createDecrement(key, by)); }
            if ("multiply".equals(type)) { fu.addValueUpdate(ValueUpdate.createMultiply(key, by)); }
            if ("divide".equals(type)) { fu.addValueUpdate(ValueUpdate.createDivide(key, by)); }
        } else {
            if ("increment".equals(type)) { fu.addValueUpdate(ValueUpdate.createIncrement(by)); }
            if ("decrement".equals(type)) { fu.addValueUpdate(ValueUpdate.createDecrement(by)); }
            if ("multiply".equals(type)) { fu.addValueUpdate(ValueUpdate.createMultiply(by)); }
            if ("divide".equals(type)) { fu.addValueUpdate(ValueUpdate.createDivide(by)); }
        }

        return fu;
    }

    public void read(FieldUpdate update) {
    }

    public void read(FieldPathUpdate update) {
        String whereClause = null;
        String fieldPath = null;

        for (int i = 0; i < reader.getAttributeCount(); i++) {
            if (reader.getAttributeName(i).toString().equals("where")) {
                whereClause = reader.getAttributeValue(i);
            } else if (reader.getAttributeName(i).toString().equals("fieldpath")) {
                fieldPath = reader.getAttributeValue(i);
            }
        }

        if (fieldPath != null) {
            update.setFieldPath(fieldPath);
        } else {
            throw newDeserializeException("Field path is required for document updates.");
        }

        if (whereClause != null) {
            try {
                update.setWhereClause(whereClause);
            } catch (ParseException e) {
                throw newException(e);
            }
        }
    }

    public void read(AssignFieldPathUpdate update) {
        try {
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                if (reader.getAttributeName(i).toString().equals("removeifzero")) {
                    update.setRemoveIfZero(Boolean.parseBoolean(reader.getAttributeValue(i)));
                } else if (reader.getAttributeName(i).toString().equals("createmissingpath")) {
                    update.setCreateMissingPath(Boolean.parseBoolean(reader.getAttributeValue(i)));
                }
            }
            DataType dt = update.getFieldPath().getResultingDataType();

            if (dt instanceof NumericDataType) {
                update.setExpression(reader.getElementText());
            } else {
                FieldValue fv = dt.createFieldValue();
                fv.deserialize(resolveField(update), this);
                update.setNewValue(fv);
            }
        } catch (XMLStreamException e) {
            throw newException(e);
        }
    }

    public void read(AddFieldPathUpdate update) {
        DataType dt = update.getFieldPath().getResultingDataType();
        FieldValue fv = dt.createFieldValue();
        fv.deserialize(resolveField(update), this);
        update.setNewValues((Array)fv);
    }

    public void read(RemoveFieldPathUpdate update) {
    }

    private static Field resolveField(FieldPathUpdate update) {
        String orig = update.getOriginalFieldPath();
        if (orig == null) {
            return null;
        }
        FieldPath path = update.getFieldPath();
        if (path == null) {
            return null;
        }
        DataType type = path.getResultingDataType();
        if (type == null) {
            return null;
        }
        return new Field(orig, type);
    }
}
