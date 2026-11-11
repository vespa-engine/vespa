// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.vespa;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.GroupingValidator;
import com.yahoo.search.grouping.request.DecodeFastMapLookupFunction;
import com.yahoo.search.grouping.request.ExpressionVisitor;
import com.yahoo.search.grouping.request.GroupingExpression;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.Schema;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.vespa.config.search.AttributesConfig;

import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.yahoo.search.grouping.GroupingQueryParser.SELECT_PARAMETER_PARSING;

/**
 * Prepares the {@link GroupingRequest} objects of a query for the
 * search cluster this searcher is deployed for.  Unlike {@link
 * GroupingValidator}, this always runs, as the backend depends on it:
 * <ul>
 *   <li>In indexed mode, it sets the value type of each fast map
 *       lookup ({@code decodefastmaplookup}), resolved from the
 *       schemas in this cluster that the query searches.</li>
 *   <li>In streaming mode, it rejects fast map lookups, which the
 *       streaming backend cannot evaluate.</li>
 * </ul>
 * It runs after {@link GroupingValidator} and before {@link
 * GroupingExecutor}, so that it also sees grouping requests added by
 * searchers running before validation.
 * Invalid requests are signaled by throwing {@link IllegalInputException}.
 *
 * @author arnej27959
 */
@Before({ GroupingExecutor.COMPONENT_NAME, PhaseNames.BACKEND })
@After({ SELECT_PARAMETER_PARSING, GroupingValidator.GROUPING_VALIDATED })
public class GroupingRewriteSearcher extends Searcher {

    private final Set<String> attributes;
    private final String clusterName;
    private final boolean streamingMode;

    /**
     * Constructs a new instance of this searcher.
     *
     * @param clusterConfig    the config for the cluster that this searcher is deployed for
     * @param attributesConfig the attributes available in that cluster
     */
    @Inject
    public GroupingRewriteSearcher(ClusterConfig clusterConfig, AttributesConfig attributesConfig) {
        streamingMode = (clusterConfig.indexMode() == ClusterConfig.IndexMode.Enum.STREAMING);
        clusterName = clusterConfig.clusterName();
        attributes = attributesConfig.attribute().stream().map(AttributesConfig.Attribute::name).collect(Collectors.toSet());
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (streamingMode)
            visitExpressions(query, new StreamingRejecter());
        else
            visitExpressions(query, new FastMapLookupResolver(query, execution.context().schemaInfo()));
        return execution.search(query);
    }

    private static void visitExpressions(Query query, ExpressionVisitor visitor) {
        for (GroupingRequest req : query.getSelect().getGrouping())
            req.getRootOperation().visitExpressions(visitor);
    }

    /**
     * Sets the value type of each fast map lookup, from the schemas of this cluster that the query searches.
     * The value type is serialized to the backend, so it must be the same in all those schemas.
     */
    private class FastMapLookupResolver implements ExpressionVisitor {
        private final Query query;
        private final SchemaInfo schemaInfo;
        private List<Schema> searchedSchemas = null; // resolved on first use, as most grouping has no fast map lookups

        FastMapLookupResolver(Query query, SchemaInfo schemaInfo) {
            this.query = query;
            this.schemaInfo = schemaInfo;
        }

        private List<Schema> searchedSchemas() {
            if (searchedSchemas == null) {
                var cluster = schemaInfo.clusters().get(clusterName);
                if (cluster != null) {
                    Set<String> clusterSchemas = cluster.schemas();
                    searchedSchemas = schemaInfo.newSession(query).schemas().stream()
                            .filter(s -> clusterSchemas.contains(s.name()))
                            .toList();
                } else {
                    searchedSchemas = List.of();
                }
            }
            return searchedSchemas;
        }

        @Override
        public void visitExpression(GroupingExpression exp) {
            if (exp instanceof DecodeFastMapLookupFunction function)
                function.setValueType(resolveValueType(function));
        }

        private Field.Type resolveValueType(DecodeFastMapLookupFunction function) {
            if ( ! attributes.contains(function.getAttributeName()))
                throw unavailable(function);
            var typesBySchema = new TreeMap<String, Field.Type>();
            for (Schema schema : searchedSchemas()) {
                Field field = schema.fields().get(function.getFieldName());
                if (field == null) continue;
                field.fastMapSearch(function.getLookupName())
                     .ifPresent(fastMap -> typesBySchema.put(schema.name(), fastMap.valueType()));
            }
            if (typesBySchema.isEmpty())
                throw unavailable(function);
            if (typesBySchema.values().stream().distinct().count() > 1)
                throw new IllegalInputException("Grouping request references fast map lookup '" + function.getName() +
                                                "', which has different value types in the schemas searched in cluster '" +
                                                clusterName + "': " + typesBySchema +
                                                ". Restrict the query to schemas with the same value type.");
            Field.Type type = typesBySchema.firstEntry().getValue();
            if ( ! isDecodable(type))
                throw new IllegalInputException("Grouping request references fast map lookup '" + function.getName() +
                                                "', whose values of type " + type + " cannot be decoded.");
            return type;
        }

        private IllegalInputException unavailable(DecodeFastMapLookupFunction function) {
            return new IllegalInputException("Grouping request references fast map lookup '" + function.getName() +
                                             "' which is not available in cluster '" + clusterName + "'.");
        }

        private static boolean isDecodable(Field.Type type) {
            return switch (type.kind()) {
                case INT, LONG, FLOAT, DOUBLE, STRING -> true;
                default -> false;
            };
        }

    }

    /**
     * Rejects expressions that the streaming search backend cannot evaluate.
     * This happens even when validation is disabled, as such expressions would otherwise
     * fail mysterously in the backend.
     */
    private class StreamingRejecter implements ExpressionVisitor {

        @Override
        public void visitExpression(GroupingExpression exp) {
            if (exp instanceof DecodeFastMapLookupFunction function) {
                throw new IllegalInputException("Grouping request references fast map lookup '" + function.getName() +
                                                "', but decodefastmaplookup is not supported in streaming mode (cluster '" +
                                                clusterName + "').");
            }
        }
    }

}
