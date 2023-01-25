// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Information about all the schemas configured in the application this container is a part of.
 *
 * Usage:
 * <code>
 *   SchemaInfo.Session session = schemaInfo.newSession(query); // once when starting to process a query
 *   session.get(...) // access information about the schema(s) relevant to the query
 * </code>
 *
 * This is immutable.
 *
 * @author bratseth
 */
// NOTES:
// This should replace IndexFacts, and probably DocumentDatabase.
// It replicates the schema resolution mechanism in IndexFacts, but does not yet contain any field information.
// To replace IndexFacts, this must accept IndexInfo and expose that information, as well as consolidation
// given a set of possible schemas: The session mechanism is present here to make that efficient when added
// (resolving schema subsets for every field lookup is too expensive).
@Beta
public class SchemaInfo {

    private static final SchemaInfo empty = new SchemaInfo(List.of(), Map.of());

    private final Map<String, Schema> schemas;

    /** The schemas contained in each content cluster indexed by cluster name */
    private final Map<String, List<String>> clusters;

    @Inject
    public SchemaInfo(IndexInfoConfig indexInfo, // will be used in the future
                      SchemaInfoConfig schemaInfoConfig,
                      QrSearchersConfig qrSearchersConfig) {
        this(SchemaInfoConfigurer.toSchemas(schemaInfoConfig), SchemaInfoConfigurer.toClusters(qrSearchersConfig));
    }

    public SchemaInfo(List<Schema> schemas, Map<String, List<String>> clusters) {
        Map<String, Schema> schemaMap = new LinkedHashMap<>();
        schemas.forEach(schema -> schemaMap.put(schema.name(), schema));
        this.schemas = Collections.unmodifiableMap(schemaMap);
        this.clusters = Collections.unmodifiableMap(clusters);
    }

    /** Returns all schemas configured in this application, indexed by schema name. */
    public Map<String, Schema> schemas() { return schemas; }

    public Session newSession(Query query) {
        return new Session(query.getModel().getSources(), query.getModel().getRestrict(), clusters, schemas);
    }

    public static SchemaInfo empty() { return empty; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof SchemaInfo)) return false;
        SchemaInfo other = (SchemaInfo)o;
        if ( ! other.schemas.equals(this.schemas)) return false;
        if ( ! other.clusters.equals(this.clusters)) return false;
        return true;
    }

    @Override
    public int hashCode() { return Objects.hash(schemas, clusters); }

    /** The schema information resolved to be relevant to this session. */
    public static class Session {

        private final Collection<Schema> schemas;

        private Session(Set<String> sources,
                        Set<String> restrict,
                        Map<String, List<String>> clusters,
                        Map<String, Schema> candidates) {
            this.schemas = resolveSchemas(sources, restrict, clusters, candidates.values());
        }

        /**
         * Given a search list which is a mixture of schemas and cluster
         * names, and a restrict list which is a list of schemas, return a
         * set of all valid schemas for this combination.
         *
         * @return the possibly empty list of schemas matching the arguments
         */
        private static Collection<Schema> resolveSchemas(Set<String> sources,
                                                         Set<String> restrict,
                                                         Map<String, List<String>> clusters,
                                                         Collection<Schema> candidates) {
            if (sources.isEmpty())
                return restrict.isEmpty() ? candidates : keep(restrict, candidates);

            Set<String> schemaNames = new HashSet<>();
            for (String source : sources) {
                if (clusters.containsKey(source)) // source is a cluster
                    schemaNames.addAll(clusters.get(source));
                else // source is a schema
                    schemaNames.add(source);
            }
            candidates = keep(schemaNames, candidates);
            return restrict.isEmpty() ? candidates : keep(restrict, candidates);
        }

        private static List<Schema> keep(Set<String> names, Collection<Schema> schemas) {
            return schemas.stream().filter(schema -> names.contains(schema.name())).toList();
        }

        private List<RankProfile> profilesNamed(String name) {
            return schemas.stream()
                          .filter(schema -> schema.rankProfiles().containsKey(name))
                          .map(schema -> schema.rankProfiles().get(name))
                          .toList();
        }

        /**
         * Returns the type of the given rank feature name in the given profile,
         * if it can be uniquely determined.
         *
         * @param rankFeature the rank feature name, a string on the form "query(name)"
         * @param rankProfile the name of the rank profile in which to locate the input declaration
         * @return the type of the declared input, or null if it is not declared or the rank profile is not found
         * @throws IllegalArgumentException if the given rank profile does not exist in any schema, or the
         *         feature is declared in this rank profile in multiple schemas
         *         of this session with conflicting types
         */
        public TensorType rankProfileInput(String rankFeature, String rankProfile) {
            if (schemas.isEmpty()) return null; // no matching schemas - validated elsewhere
            List<RankProfile> profiles = profilesNamed(rankProfile);
            if (profiles.isEmpty())
                throw new IllegalArgumentException("No profile named '" + rankProfile + "' exists in schemas [" +
                                                   schemas.stream().map(Schema::name).collect(Collectors.joining(", ")) + "]");
            TensorType foundType = null;
            RankProfile declaringProfile = null;
            for (RankProfile profile : profiles) {
                TensorType newlyFoundType = profile.inputs().get(rankFeature);
                if (newlyFoundType == null) continue;
                if (foundType != null && ! newlyFoundType.equals(foundType))
                    throw new IllegalArgumentException("Conflicting input type declarations for '" + rankFeature + "': " +
                                                       "Declared as " + foundType + " in " + declaringProfile +
                                                       ", and as " + newlyFoundType + " in " + profile);
                foundType = newlyFoundType;
                declaringProfile = profile;
            }
            return foundType;
        }

    }

}
