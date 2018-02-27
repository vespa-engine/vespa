// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.document.*;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.indexinglanguage.ExpressionConverter;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Simon Thoresen
 */
public class IndexingValidation extends Processor {

    public IndexingValidation(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        if ( ! validate) return;

        VerificationContext context = new VerificationContext(new MyAdapter(search));
        for (SDField field : search.allConcreteFields()) {
            ScriptExpression script = field.getIndexingScript();
            try {
                script.verify(context);
                MyConverter converter = new MyConverter();
                for (StatementExpression exp : script) {
                    converter.convert(exp); // TODO: stop doing this explicitly when visiting a script does not branch
                }
            } catch (VerificationException e) {
                fail(search, field, "For expression '" + e.getExpression() + "': " + e.getMessage());
            }
        }
    }

    private static class MyConverter extends ExpressionConverter {

        final Set<String> outputs = new HashSet<>();
        final Set<String> prevNames = new HashSet<>();

        @Override
        protected ExpressionConverter branch() {
            MyConverter ret = new MyConverter();
            ret.outputs.addAll(outputs);
            ret.prevNames.addAll(prevNames);
            return ret;
        }

        @Override
        protected boolean shouldConvert(Expression exp) {
            if (exp instanceof OutputExpression) {
                String fieldName = ((OutputExpression)exp).getFieldName();
                if (outputs.contains(fieldName) && !prevNames.contains(fieldName)) {
                    throw new VerificationException(exp, "Attempting to assign conflicting values to field '" +
                                                         fieldName + "'.");
                }
                outputs.add(fieldName);
                prevNames.add(fieldName);
            }
            if (exp.createdOutputType() != null) {
                prevNames.clear();
            }
            return false;
        }

        @Override
        protected Expression doConvert(Expression exp) {
            throw new UnsupportedOperationException();
        }
    }

    private static class MyAdapter implements FieldTypeAdapter {

        final Search search;

        public MyAdapter(Search search) {
            this.search = search;
        }

        @Override
        public DataType getInputType(Expression exp, String fieldName) {
            SDField field = search.getDocumentField(fieldName);
            if (field == null) {
                throw new VerificationException(exp, "Input field '" + fieldName + "' not found.");
            }
            return field.getDataType();
        }

        @Override
        public void tryOutputType(Expression exp, String fieldName, DataType valueType) {
            String fieldDesc;
            DataType fieldType;
            if (exp instanceof AttributeExpression) {
                Attribute attribute = search.getAttribute(fieldName);
                if (attribute == null) {
                    throw new VerificationException(exp, "Attribute '" + fieldName + "' not found.");
                }
                fieldDesc = "attribute";
                fieldType = attribute.getDataType();
            } else if (exp instanceof IndexExpression) {
                SDField field = search.getConcreteField(fieldName);
                if (field == null) {
                    throw new VerificationException(exp, "Index field '" + fieldName + "' not found.");
                }
                fieldDesc = "index field";
                fieldType = field.getDataType();
            } else if (exp instanceof SummaryExpression) {
                SummaryField field = search.getSummaryField(fieldName);
                if (field == null) {
                    throw new VerificationException(exp, "Summary field '" + fieldName + "' not found.");
                }
                fieldDesc = "summary field";
                fieldType = field.getDataType();
            } else {
                throw new UnsupportedOperationException();
            }
            if ( ! fieldType.isAssignableFrom(valueType) &&
                 ! fieldType.isAssignableFrom(createCompatType(valueType))) {
                throw new VerificationException(exp, "Can not assign " + valueType.getName() + " to " + fieldDesc +
                                                     " '" + fieldName + "' which is " + fieldType.getName() + ".");
            }
        }

        private static DataType createCompatType(DataType origType) {
            if (origType instanceof ArrayDataType) {
                return DataType.getArray(createCompatType(((ArrayDataType)origType).getNestedType()));
            } else if (origType instanceof MapDataType) {
                MapDataType mapType = (MapDataType)origType;
                return DataType.getMap(createCompatType(mapType.getKeyType()),
                                       createCompatType(mapType.getValueType()));
            } else if (origType instanceof WeightedSetDataType) {
                return DataType.getWeightedSet(createCompatType(((WeightedSetDataType)origType).getNestedType()));
            } else if (origType == PositionDataType.INSTANCE) {
                return DataType.LONG;
            } else {
                return origType;
            }
        }
    }

}
