// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

import com.google.inject.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.chain.dependencies.Provides;
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

import java.util.HashSet;
import java.util.Set;

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
    public static final CompoundName PARAM_ENABLED = new CompoundName("validate_" + GroupingQueryParser.PARAM_REQUEST);
    private final Set<String> attributeNames = new HashSet<>();
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
            attributeNames.add(attr.name());
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (enabled && query.properties().getBoolean(PARAM_ENABLED, true)) {
            ExpressionVisitor visitor = new MyVisitor();
            for (GroupingRequest req : GroupingRequest.getRequests(query)) {
                req.getRootOperation().visitExpressions(visitor);
            }
        }
        return execution.search(query);
    }

    private class MyVisitor implements ExpressionVisitor {

        @Override
        public void visitExpression(GroupingExpression exp) {
            if (exp instanceof AttributeValue) {
                String name = ((AttributeValue)exp).getAttributeName();
                if (!attributeNames.contains(name)) {
                    throw new UnavailableAttributeException(clusterName, name);
                }
            }
        }
    }
}
