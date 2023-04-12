// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.grouping.request.AttributeMapLookupValue;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.container.QrSearchersConfig;
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
     * @param qrsConfig     The shared config for all searchers.
     * @param clusterConfig The config for the cluster that this searcher is deployed for.
     */
    @Inject
    public GroupingValidator(QrSearchersConfig qrsConfig, ClusterConfig clusterConfig,
                             AttributesConfig attributesConfig) {
        int clusterId = clusterConfig.clusterId();
        QrSearchersConfig.Searchcluster.Indexingmode.Enum indexingMode = qrsConfig.searchcluster(clusterId).indexingmode();
        enabled = (indexingMode != QrSearchersConfig.Searchcluster.Indexingmode.STREAMING);
        clusterName = enabled ? qrsConfig.searchcluster(clusterId).name() : null;
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

    private void verifyHasAttribute(String attributeName) {
        if (!attributes.containsKey(attributeName)) {
            throw new UnavailableAttributeException(clusterName, attributeName);
        }
    }

    private void verifyCompatibleAttributeTypes(String keyAttributeName,
                                                String keySourceAttributeName) {
        AttributesConfig.Attribute keyAttribute = attributes.get(keyAttributeName);
        AttributesConfig.Attribute keySourceAttribute = attributes.get(keySourceAttributeName);
        if (!keySourceAttribute.datatype().equals(keyAttribute.datatype())) {
            throw new IllegalInputException("Grouping request references key source attribute '" +
                                            keySourceAttributeName + "' with data type '" + keySourceAttribute.datatype() +
                                            "' that is different than data type '" + keyAttribute.datatype() + "' of key attribute '" +
                                            keyAttributeName + "'");
        }
        if (!keySourceAttribute.collectiontype().equals(AttributesConfig.Attribute.Collectiontype.Enum.SINGLE)) {
            throw new IllegalInputException("Grouping request references key source attribute '" +
                                            keySourceAttributeName + "' which is not of single value type");
        }
    }

    private class MyVisitor implements ExpressionVisitor {

        @Override
        public void visitExpression(GroupingExpression exp) {
            if (exp instanceof AttributeMapLookupValue) {
                AttributeMapLookupValue mapLookup = (AttributeMapLookupValue) exp;
                verifyHasAttribute(mapLookup.getKeyAttribute());
                verifyHasAttribute(mapLookup.getValueAttribute());
                if (mapLookup.hasKeySourceAttribute()) {
                    verifyHasAttribute(mapLookup.getKeySourceAttribute());
                    verifyCompatibleAttributeTypes(mapLookup.getKeyAttribute(), mapLookup.getKeySourceAttribute());
                }
            } else if (exp instanceof AttributeValue) {
                verifyHasAttribute(((AttributeValue) exp).getAttributeName());
            }
        }
    }
}
