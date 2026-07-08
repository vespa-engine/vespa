// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.search.query.parser.ParserEnvironment.ParserSettings;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
// It replicates the schema resolution mechanism in IndexFacts, but does not yet contain complete field information.
public class SchemaInfo {

    private static final SchemaInfo empty = new SchemaInfo(List.of(), List.of());

    private final Map<String, Schema> schemas;

    private final Map<String, Cluster> clusters;

    private final ParserSettings parserSettings;

    @Inject
    public SchemaInfo(SchemaInfoConfig schemaInfoConfig,
                      QrSearchersConfig qrSearchersConfig) {
        this(SchemaInfoConfigurer.toSchemas(schemaInfoConfig),
             SchemaInfoConfigurer.toClusters(qrSearchersConfig),
             extractLQP(qrSearchersConfig));
    }

    public SchemaInfo(List<Schema> schemas, List<Cluster> clusters) {
        this(schemas, clusters, new ParserSettings());
    }

    private SchemaInfo(List<Schema> schemas, List<Cluster> clusters, ParserSettings lqp) {
        Map<String, Schema> schemaMap = new LinkedHashMap<>();
        schemas.forEach(schema -> schemaMap.put(schema.name(), schema));
        this.schemas = Collections.unmodifiableMap(schemaMap);

        Map<String, Cluster> clusterMap = new LinkedHashMap<>();
        clusters.forEach(cluster -> clusterMap.put(cluster.name(), cluster));
        this.clusters = Collections.unmodifiableMap(clusterMap);

        this.parserSettings = lqp;
    }

    /** Returns all schemas configured in this application, indexed by schema name. */
    public Map<String, Schema> schemas() { return schemas; }

    /** Returns information about all clusters available for searching in this application, indexed by cluster name. */
    public Map<String, Cluster> clusters() { return clusters; }

    public ParserSettings parserSettings() { return parserSettings; }

    public Session newSession(Query query) {
        return new Session(query.getModel().getSources(), query.getModel().getRestrict(), clusters, schemas);
    }

    public static SchemaInfo empty() { return empty; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof SchemaInfo other)) return false;
        if ( ! other.schemas.equals(this.schemas)) return false;
        if ( ! other.clusters.equals(this.clusters)) return false;
        return true;
    }

    @Override
    public int hashCode() { return Objects.hash(schemas, clusters); }

    private static ParserSettings extractLQP(QrSearchersConfig qrSearchersConfig) {
        var cfg = qrSearchersConfig.parserSettings();
        return new ParserSettings(cfg.keepImplicitAnds(),
                                  cfg.markSegmentAnds(),
                                  cfg.keepSegmentAnds(),
                                  cfg.keepIdeographicPunctuation());
    }

    /** only for unit tests */
    public static SchemaInfo createStub(ParserSettings lqp) {
        return new SchemaInfo(List.of(), List.of(), lqp);
    }

    /** The schema information resolved to be relevant to this session. */
    public static class Session {

        private final boolean isStreaming;
        private final Collection<Schema> schemas;

        private Session(Set<String> sources,
                        Set<String> restrict,
                        Map<String, Cluster> clusters,
                        Map<String, Schema> candidates) {
            this.isStreaming = resolveStreaming(sources, clusters);
            this.schemas = resolveSchemas(sources, restrict, clusters, candidates.values());
        }

        /** Returns true if this only searches streaming clusters. */
        public boolean isStreaming() { return isStreaming; }

        public Collection<Schema> schemas() { return schemas; }

        public Optional<Schema> schema(String name) {
            return schemas.stream().filter(schema -> schema.name().equals(name)).findAny();
        }

        /**
         * Looks up a field or field set by the given name or alias
         * in the schemas resolved for this query.
         *
         * If there are several fields or field sets by this name or alias across the schemas of this session,
         * one is chosen by random.
         *
         * @param fieldName the name or alias of the field or field set. If this is empty, the name "default" is looked up.
         * @return the appropriate field or empty if no field or field set has this name or alias
         */
        public Optional<FieldInfo> fieldInfo(String fieldName) {
            for (var schema : schemas) {
                Optional<FieldInfo> field = schema.fieldInfo(fieldName);
                if (field.isPresent())
                    return field;
            }
            return Optional.empty();
        }

        private static boolean resolveStreaming(Set<String> sources, Map<String, Cluster> clusters) {
            if (sources.isEmpty()) return clusters.values().stream().allMatch(Cluster::isStreaming);

            var matchedClusters = sources.stream().map(source -> clusterOfSource(source, clusters)).filter(Objects::nonNull).toList();
            if (matchedClusters.isEmpty()) return false;
            return matchedClusters.stream().allMatch(Cluster::isStreaming);
        }

        /**
         * A source name is either a cluster or a schema.
         * Returns the cluster which either is or contains this name, if any.
         */
        private static Cluster clusterOfSource(String source, Map<String, Cluster> clusters) {
            var cluster = clusters.get(source);
            if (cluster != null) return cluster;
            for (var c : clusters.values()) {
                if (c.schemas().contains(source))
                    return c;
            }
            return null;
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
                                                         Map<String, Cluster> clusters,
                                                         Collection<Schema> candidates) {
            if (sources.isEmpty())
                return restrict.isEmpty() ? candidates : keep(restrict, candidates);

            Set<String> schemaNames = new HashSet<>();
            for (String source : sources) {
                if (clusters.containsKey(source)) // source is a cluster
                    schemaNames.addAll(clusters.get(source).schemas());
                else // source is a schema
                    schemaNames.add(source);
            }
            candidates = keep(schemaNames, candidates);
            return restrict.isEmpty() ? candidates : keep(restrict, candidates);
        }

        private static List<Schema> keep(Set<String> names, Collection<Schema> schemas) {
            return schemas.stream().filter(schema -> names.contains(schema.name())).toList();
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
        public RankProfile.InputType rankProfileInput(String rankFeature, String rankProfile) {
            if (schemas.isEmpty()) return null; // no matching schemas - validated elsewhere
            List<RankProfile> profiles = profilesNamed(rankProfile);
            if (profiles.isEmpty())
                throw new IllegalArgumentException("No profile named '" + rankProfile + "' exists in schemas [" +
                                                   schemas.stream().map(Schema::name).collect(Collectors.joining(", ")) + "]");
            RankProfile.InputType foundType = null;
            RankProfile declaringProfile = null;
            for (RankProfile profile : profiles) {
                RankProfile.InputType newlyFoundType = profile.inputs().get(rankFeature);
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

        private List<RankProfile> profilesNamed(String name) {
            return schemas.stream()
                          .filter(schema -> schema.rankProfiles().containsKey(name))
                          .map(schema -> schema.rankProfiles().get(name))
                          .toList();
        }

    }

}
