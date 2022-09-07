// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.searchlib.rankingexpression.Reference;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Information about a schema.
 *
 * @author bratseth
 */
public final class SchemaInfo extends Derived implements SchemaInfoConfig.Producer {

    private final Schema schema;

    // Info about profiles needed in memory after build.
    // The rank profile registry itself is not kept around due to its size.
    private final Map<String, RankProfileInfo> rankProfiles;

    private final Summaries summaries;

    public SchemaInfo(Schema schema, RankProfileRegistry rankProfileRegistry, Summaries summaries) {
        this.schema = schema;
        this.rankProfiles = Collections.unmodifiableMap(toRankProfiles(rankProfileRegistry.rankProfilesOf(schema)));
        this.summaries = summaries;
    }

    public String name() { return schema.getName(); }

    @Override
    public String getDerivedName() { return "schema-info"; }

    public Schema fullSchema() { return schema; }

    public Map<String, RankProfileInfo> rankProfiles() { return rankProfiles; }

    private Map<String, RankProfileInfo> toRankProfiles(Collection<RankProfile> rankProfiles) {
        Map<String, RankProfileInfo> rankProfileInfos = new LinkedHashMap<>();
        rankProfiles.forEach(profile -> rankProfileInfos.put(profile.name(), new RankProfileInfo(profile)));
        return rankProfileInfos;
    }

    @Override
    public void getConfig(SchemaInfoConfig.Builder builder) {
        var schemaBuilder = new SchemaInfoConfig.Schema.Builder();
        schemaBuilder.name(schema.getName());
        addSummaryConfig(schemaBuilder);
        addRankProfilesConfig(schemaBuilder);
        builder.schema(schemaBuilder);
    }

    private void addSummaryConfig(SchemaInfoConfig.Schema.Builder schemaBuilder) {
        for (var summary : summaries.asList()) {
            var summaryBuilder = new SchemaInfoConfig.Schema.Summaryclass.Builder();
            summaryBuilder.name(summary.getName());
            for (var field : summary.fields().values()) {
                var fieldsBuilder = new SchemaInfoConfig.Schema.Summaryclass.Fields.Builder();
                fieldsBuilder.name(field.getName())
                             .type(field.getType().getName())
                             .dynamic(SummaryClass.commandRequiringQuery(field.getCommand()));
                summaryBuilder.fields(fieldsBuilder);
            }
            schemaBuilder.summaryclass(summaryBuilder);
        }
    }

    private void addRankProfilesConfig(SchemaInfoConfig.Schema.Builder schemaBuilder) {
        for (RankProfileInfo rankProfile : rankProfiles().values()) {
            var rankProfileConfig = new SchemaInfoConfig.Schema.Rankprofile.Builder();
            rankProfileConfig.name(rankProfile.name());
            rankProfileConfig.hasSummaryFeatures(rankProfile.hasSummaryFeatures());
            rankProfileConfig.hasRankFeatures(rankProfile.hasRankFeatures());
            for (var input : rankProfile.inputs().entrySet()) {
                var inputConfig = new SchemaInfoConfig.Schema.Rankprofile.Input.Builder();
                inputConfig.name(input.getKey().toString());
                inputConfig.type(input.getValue().type().toString());
                rankProfileConfig.input(inputConfig);
            }
            schemaBuilder.rankprofile(rankProfileConfig);
        }
    }

    /** A store of a *small* (in memory) amount of rank profile info. */
    public static final class RankProfileInfo {

        private final String name;
        private final boolean hasSummaryFeatures;
        private final boolean hasRankFeatures;
        private final Map<Reference, RankProfile.Input> inputs;

        public RankProfileInfo(RankProfile profile) {
            this.name = profile.name();
            this.hasSummaryFeatures =  ! profile.getSummaryFeatures().isEmpty();
            this.hasRankFeatures =  ! profile.getRankFeatures().isEmpty();
            this.inputs = profile.inputs();
        }

        public String name() { return name; }
        public boolean hasSummaryFeatures() { return hasSummaryFeatures; }
        public boolean hasRankFeatures() { return hasRankFeatures; }
        public Map<Reference, RankProfile.Input> inputs() { return inputs; }

    }

}
