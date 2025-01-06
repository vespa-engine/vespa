// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.grouping.request.AttributeMapLookupValue;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.grouping.request.AttributeValue;
import com.yahoo.search.grouping.request.ExpressionVisitor;
import com.yahoo.search.grouping.request.GroupingExpression;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

import java.util.HashMap;

import static com.yahoo.search.grouping.GroupingQueryParser.SELECT_PARAMETER_PARSING;

/**
 * This searcher ensure that all {@link GroupingRequest} objects attached to a {@link Query} makes sense to the search
 * cluster for which this searcher has been deployed. This searcher uses exceptions to signal invalid grouping
 * requests.
 *
 * @author Simon Thoresen Hult
 */
@Before(PhaseNames.BACKEND)
@After(SELECT_PARAMETER_PARSING)
@Provides(GroupingValidator.GROUPING_VALIDATED)
public class GroupingValidator extends Searcher {

    public static final String GROUPING_VALIDATED = "GroupingValidated";
    public static final CompoundName PARAM_ENABLED = CompoundName.from("validate_" + GroupingQueryParser.PARAM_REQUEST);
    private final HashMap<String, AttributesConfig.Attribute> attributes = new HashMap<>();
    private final String clusterName;
    private final boolean enabled;

    /**
     * Constructs a new instance of this searcher with the given component id and config.
     *
     * @param clusterConfig The config for the cluster that this searcher is deployed for.
     */
    @Inject
    public GroupingValidator(ClusterConfig clusterConfig, AttributesConfig attributesConfig) {
        enabled = (clusterConfig.indexMode() != ClusterConfig.IndexMode.Enum.STREAMING);
        clusterName = clusterConfig.clusterName();
        for (AttributesConfig.Attribute attr : attributesConfig.attribute()) {
            attributes.put(attr.name(), attr);
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (enabled && query.properties().getBoolean(PARAM_ENABLED, true)) {
            ExpressionVisitor visitor = new MyVisitor();
            for (GroupingRequest req : query.getSelect().getGrouping())
                req.getRootOperation().visitExpressions(visitor);
        }
        return execution.search(query);
    }

    static private String datatypeAsString(AttributesConfig.Attribute attribute) {
        var datatype = attribute.datatype();
        if (datatype == AttributesConfig.Attribute.Datatype.TENSOR && !attribute.tensortype().isEmpty()) {
            return attribute.tensortype();
        }
        return switch (datatype) {
            case STRING -> "string";
            case BOOL -> "bool";
            case UINT2 -> "uint2";
            case UINT4 -> "uint4";
            case INT8 -> "byte";
            case INT16 -> "short";
            case INT32 -> "int";
            case INT64 -> "long";
            case FLOAT16 -> "float16";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case PREDICATE -> "predicate";
            case TENSOR -> "tensor";
            case REFERENCE -> "reference";
            case RAW -> "raw";
            case NONE -> "none";
        };
    }

    static private String typeAsString(AttributesConfig.Attribute attribute) {
        var collectiontype = attribute.collectiontype();
        return switch(collectiontype) {
            case SINGLE -> datatypeAsString(attribute);
            case ARRAY -> "array<" + datatypeAsString(attribute) + ">";
            case WEIGHTEDSET -> "weightedset<" + datatypeAsString(attribute) + ">";
        };
    }

    static private boolean isPrimitiveAttribute(AttributesConfig.Attribute attribute) {
        var datatype = attribute.datatype();
        return  datatype == AttributesConfig.Attribute.Datatype.INT8 ||
                datatype == AttributesConfig.Attribute.Datatype.INT16 ||
                datatype == AttributesConfig.Attribute.Datatype.INT32 ||
                datatype == AttributesConfig.Attribute.Datatype.INT64 ||
                datatype == AttributesConfig.Attribute.Datatype.STRING ||
                datatype == AttributesConfig.Attribute.Datatype.FLOAT ||
                datatype == AttributesConfig.Attribute.Datatype.DOUBLE;
    }

    static private boolean isSingleRawBoolOrReferenceAttribute(AttributesConfig.Attribute attribute) {
        var datatype = attribute.datatype();
        return  (datatype == AttributesConfig.Attribute.Datatype.RAW ||
                datatype == AttributesConfig.Attribute.Datatype.BOOL ||
                datatype == AttributesConfig.Attribute.Datatype.REFERENCE) &&
                attribute.collectiontype() == AttributesConfig.Attribute.Collectiontype.SINGLE;
    }

    private void verifyHasAttribute(String attributeName, boolean isMapLookup) {
        var attribute = attributes.get(attributeName);
        if (attribute == null) {
            throw new UnavailableAttributeException(clusterName, attributeName);
        }
        if (isPrimitiveAttribute(attribute) || (!isMapLookup && isSingleRawBoolOrReferenceAttribute(attribute))) {
            return;
        }
        throw new IllegalInputException("Grouping request references attribute '" +
                attributeName + "' with unsupported type '" + typeAsString(attribute) + "'" +
                (isMapLookup ? " for map lookup" : ""));
    }

    private void verifyCompatibleAttributeTypes(String keyAttributeName,
                                                String keySourceAttributeName) {
        AttributesConfig.Attribute keyAttribute = attributes.get(keyAttributeName);
        AttributesConfig.Attribute keySourceAttribute = attributes.get(keySourceAttributeName);
        if (!keySourceAttribute.datatype().equals(keyAttribute.datatype())) {
            throw new IllegalInputException("Grouping request references key source attribute '" +
                                            keySourceAttributeName + "' with data type '" + datatypeAsString(keySourceAttribute) +
                                            "' that is different than data type '" + datatypeAsString(keyAttribute) + "' of key attribute '" +
                                            keyAttributeName + "'");
        }
        if (!keySourceAttribute.collectiontype().equals(AttributesConfig.Attribute.Collectiontype.Enum.SINGLE)) {
            throw new IllegalInputException("Grouping request references key source attribute '" +
                                            keySourceAttributeName + "' with type '" + typeAsString(keySourceAttribute) + "' " +
                                            "which is not of single value type");
        }
    }

    private class MyVisitor implements ExpressionVisitor {

        @Override
        public void visitExpression(GroupingExpression exp) {
            if (exp instanceof AttributeMapLookupValue mapLookup) {
                verifyHasAttribute(mapLookup.getKeyAttribute(), true);
                verifyHasAttribute(mapLookup.getValueAttribute(), true);
                if (mapLookup.hasKeySourceAttribute()) {
                    verifyHasAttribute(mapLookup.getKeySourceAttribute(), true);
                    verifyCompatibleAttributeTypes(mapLookup.getKeyAttribute(), mapLookup.getKeySourceAttribute());
                }
            } else if (exp instanceof AttributeValue) {
                verifyHasAttribute(((AttributeValue) exp).getAttributeName(), false);
            }
        }
    }
}
