// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.*;
import com.yahoo.document.fieldpathupdate.AddFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FieldValueAdapter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Thoresen
 */
public class FieldPathUpdateAdapter implements UpdateAdapter {

    private final DocumentAdapter adapter;
    private final FieldPathUpdate update;

    public FieldPathUpdateAdapter(DocumentAdapter documentAdapter, FieldPathUpdate fieldUpdate) {
        adapter = documentAdapter;
        update = fieldUpdate;
    }

    @Override
    public Expression getExpression(Expression expression) {
        return expression;
    }

    @Override
    public DocumentUpdate getOutput() {
        Document doc = adapter.getFullOutput();
        DocumentUpdate upd = new DocumentUpdate(doc.getDataType(), doc.getId());
        createUpdatesAt(new ArrayList<>(), adapter.getUpdatableOutput(), 0, upd);
        return upd;
    }

    @Override
    public DataType getInputType(Expression exp, String fieldName) {
        return adapter.getInputType(exp, fieldName);
    }

    @Override
    public FieldValue getInputValue(String fieldName) {
        return adapter.getInputValue(fieldName);
    }

    @Override
    public FieldValue getInputValue(FieldPath fieldPath) {
        return adapter.getInputValue(fieldPath);
    }

    @Override
    public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
        adapter.tryOutputType(exp, fieldName, valueType);
    }

    @Override
    public FieldValueAdapter setOutputValue(Expression exp, String fieldName, FieldValue fieldValue) {
        return adapter.setOutputValue(exp, fieldName, fieldValue);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void createUpdatesAt(List<FieldPathEntry> path, FieldValue value, int idx, DocumentUpdate out) {
        FieldPath updatePath = update.getFieldPath();
        if (idx < updatePath.size()) {
            FieldPathEntry pathEntry = updatePath.get(idx);
            FieldPathEntry.Type type = pathEntry.getType();
            if (type == FieldPathEntry.Type.STRUCT_FIELD) {
                if (!(value instanceof StructuredFieldValue)) {
                    throw new IllegalArgumentException("Expected structured field value, got " +
                                                       value.getClass().getName() + ".");
                }
                for (Iterator<Map.Entry<Field, FieldValue>> it = ((StructuredFieldValue)value).iterator(); it.hasNext();) {
                    Map.Entry<Field, FieldValue> structEntry = it.next();
                    List<FieldPathEntry> nextPath = new ArrayList<>(path);
                    nextPath.add(FieldPathEntry.newStructFieldEntry(structEntry.getKey()));
                    createUpdatesAt(nextPath, structEntry.getValue(), idx + 1, out);
                }
            } else if (type == FieldPathEntry.Type.MAP_KEY) {
                if (value instanceof WeightedSet) {
                    WeightedSet wset = (WeightedSet)value;
                    for (Iterator<FieldValue> it = wset.fieldValueIterator(); it.hasNext();) {
                        FieldValue wsetEntry = it.next();
                        List<FieldPathEntry> nextPath = new ArrayList<>(path);
                        nextPath.add(FieldPathEntry.newMapLookupEntry(wsetEntry, DataType.INT));
                        createUpdatesAt(nextPath, new IntegerFieldValue(wset.get(wsetEntry)), idx + 1, out);
                    }
                } else if (value instanceof MapFieldValue) {
                    MapFieldValue<FieldValue, FieldValue> map = (MapFieldValue)value;
                    for (Map.Entry<FieldValue, FieldValue> entry : map.entrySet()) {
                        List<FieldPathEntry> nextPath = new ArrayList<>(path);
                        FieldValue nextVal = entry.getValue();
                        nextPath.add(FieldPathEntry.newMapLookupEntry(entry.getKey(), nextVal.getDataType()));
                        createUpdatesAt(nextPath, nextVal, idx + 1, out);
                    }
                } else {
                    throw new IllegalArgumentException("Expected map or weighted set, got " +
                                                       value.getClass().getName() + ".");
                }
            } else {
                path.add(pathEntry);
                createUpdatesAt(new ArrayList<>(path), value, idx + 1, out);
            }
        } else if (update instanceof AddFieldPathUpdate) {
            if (!(value instanceof Array)) {
                throw new IllegalStateException("Expected array, got " +
                                                value.getClass().getName() + ".");
            }
            out.addFieldPathUpdate(new AddFieldPathUpdate(update.getDocumentType(), new FieldPath(path).toString(),
                                                          update.getOriginalWhereClause(), (Array)value));
        } else if (update instanceof AssignFieldPathUpdate) {
            out.addFieldPathUpdate(new AssignFieldPathUpdate(update.getDocumentType(), new FieldPath(path).toString(),
                                                             update.getOriginalWhereClause(), value));
        } else if (update instanceof RemoveFieldPathUpdate) {
            out.addFieldPathUpdate(new RemoveFieldPathUpdate(update.getDocumentType(), new FieldPath(path).toString(),
                                                             update.getOriginalWhereClause()));
        }
    }
}
