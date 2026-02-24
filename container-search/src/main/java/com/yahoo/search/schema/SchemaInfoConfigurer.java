// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.config.SchemaInfoConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Translation between schema info configuration and schema objects.
 *
 * @author bratseth
 */
class SchemaInfoConfigurer {

    static List<Schema> toSchemas(SchemaInfoConfig schemaInfoConfig) {
        return schemaInfoConfig.schema().stream().map(SchemaInfoConfigurer::toSchema).toList();
    }

    static Schema toSchema(SchemaInfoConfig.Schema schemaInfoConfig) {
        Schema.Builder schemaBuilder = new Schema.Builder(schemaInfoConfig.name());

        for (var fieldConfig : schemaInfoConfig.field()) {
            Field.Builder fieldBuilder = new Field.Builder(fieldConfig.name(), fieldConfig.type());
            fieldBuilder.setAttribute(fieldConfig.attribute());
            fieldBuilder.setIndex(fieldConfig.index());
            fieldBuilder.setBitPacked(fieldConfig.index());
            for (var alias : fieldConfig.alias())
                fieldBuilder.addAlias(alias);
            schemaBuilder.add(fieldBuilder.build());
        }

        for (var profileConfig : schemaInfoConfig.rankprofile()) {
            var profileBuilder = new RankProfile.Builder(profileConfig.name())
                    .setHasSummaryFeatures(profileConfig.hasSummaryFeatures())
                    .setHasRankFeatures(profileConfig.hasRankFeatures())
                    .setUseSignificanceModel(profileConfig.significance().useModel());
            var secondPhaseBuilder = new SecondPhase.Builder();
            if (profileConfig.rerankCount() >= 0)
                secondPhaseBuilder.setRerankCount(profileConfig.rerankCount());
            if (profileConfig.totalRerankCount() >= 0)
                secondPhaseBuilder.setTotalRerankCount(profileConfig.totalRerankCount());
            profileBuilder.setSecondPhase(secondPhaseBuilder.build());
            for (var inputConfig : profileConfig.input())
                profileBuilder.addInput(inputConfig.name(), RankProfile.InputType.fromSpec(inputConfig.type()));
            schemaBuilder.add(profileBuilder.build());
        }

        for (var summaryConfig : schemaInfoConfig.summaryclass()) {
            DocumentSummary.Builder summaryBuilder = new DocumentSummary.Builder(summaryConfig.name());
            for (var field : summaryConfig.fields()) {
                if (field.dynamic())
                    summaryBuilder.setDynamic(true);
                summaryBuilder.add(new DocumentSummary.Field(field.name(), field.type()));
            }
            schemaBuilder.add(summaryBuilder.build());
        }

        return schemaBuilder.build();
    }

    static List<Cluster> toClusters(QrSearchersConfig config) {
        List<Cluster> clusters = new ArrayList<>();
        for (var searchCluster : config.searchcluster()) {
            String clusterName = searchCluster.name();
            var clusterInfo = new Cluster.Builder(clusterName);
            clusterInfo.setStreaming(searchCluster.indexingmode() == QrSearchersConfig.Searchcluster.Indexingmode.Enum.STREAMING);
            for (var schemaDef : searchCluster.searchdef())
                clusterInfo.addSchema(schemaDef);
            clusters.add(clusterInfo.build());
        }
        return clusters;
    }

}
