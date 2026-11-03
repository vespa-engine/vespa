// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.tensor.Tensor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        if ( ! schemaInfoConfig.explanation().isEmpty())
            schemaBuilder.setExplanation(schemaInfoConfig.explanation());

        for (var fieldConfig : schemaInfoConfig.field()) {
            Field.Builder fieldBuilder = new Field.Builder(fieldConfig.name(), fieldConfig.type());
            fieldBuilder.setAttribute(fieldConfig.attribute());
            fieldBuilder.setIndex(fieldConfig.index());
            fieldBuilder.setMatchType(Field.MatchType.valueOf(fieldConfig.matchType().name()));
            if ( ! fieldConfig.inputExpression().isEmpty())
                fieldBuilder.setInputExpression(fieldConfig.inputExpression());
            if ( ! fieldConfig.explanation().isEmpty())
                fieldBuilder.setExplanation(fieldConfig.explanation());
            for (var fastMapConfig : fieldConfig.fastMapSearchFields())
                fieldBuilder.addFastMapSearch(new Field.FastMapSearchFields(fastMapConfig.lookupName(),
                                                                            fastMapConfig.keyField(),
                                                                            Field.Type.from(fastMapConfig.keyType()),
                                                                            fastMapConfig.valueField(),
                                                                            Field.Type.from(fastMapConfig.valueType())));
            for (var alias : fieldConfig.alias())
                fieldBuilder.addAlias(alias);
            schemaBuilder.add(fieldBuilder.build());
        }

        for (var fieldSetConfig : schemaInfoConfig.fieldset()) {
            FieldSet.Builder fieldSetBuilder = new FieldSet.Builder(fieldSetConfig.name());
            for (String fieldName : fieldSetConfig.field())
                fieldSetBuilder.addField(fieldName);
            schemaBuilder.add(fieldSetBuilder.build());
        }

        for (var profileConfig : schemaInfoConfig.rankprofile()) {
            var profileBuilder = new RankProfile.Builder(profileConfig.name())
                    .setHasSummaryFeatures(profileConfig.hasSummaryFeatures())
                    .setHasRankFeatures(profileConfig.hasRankFeatures())
                    .setUseSignificanceModel(profileConfig.significance().useModel());
            if ( ! profileConfig.explanation().isEmpty())
                profileBuilder.setExplanation(profileConfig.explanation());
            if (profileConfig.keepRankCount() >= 0)
                profileBuilder.setKeepRankCount(profileConfig.keepRankCount());
            if (profileConfig.totalKeepRankCount() >= 0)
                profileBuilder.setTotalKeepRankCount(profileConfig.totalKeepRankCount());
            for (var inputConfig : profileConfig.input())
                profileBuilder.addInput(new RankProfile.Input(inputConfig.name(),
                                                              RankProfile.InputType.fromSpec(inputConfig.type()),
                                                              inputConfig.defaultValue().isEmpty() ? Optional.empty()
                                                                                                   : Optional.of(Tensor.from(inputConfig.defaultValue())), inputConfig.explanation().isEmpty() ? Optional.empty()
                                                                                                                                                                                               : Optional.of(inputConfig.explanation())
                ));
            for (String sortFeature : profileConfig.sortFeature())
                profileBuilder.addSortFeature(sortFeature);

            var matchPhaseBuilder = new MatchPhase.Builder();
            if (profileConfig.matchPhaseMaxHits() >= 0)
                matchPhaseBuilder.setMaxHits(profileConfig.matchPhaseMaxHits());
            if (profileConfig.totalMatchPhaseMaxHits() >= 0)
                matchPhaseBuilder.setTotalMaxHits(profileConfig.totalMatchPhaseMaxHits());
            profileBuilder.setMatchPhase(matchPhaseBuilder.build());

            var secondPhaseBuilder = new SecondPhase.Builder();
            if (profileConfig.rerankCount() >= 0)
                secondPhaseBuilder.setRerankCount(profileConfig.rerankCount());
            if (profileConfig.totalRerankCount() >= 0)
                secondPhaseBuilder.setTotalRerankCount(profileConfig.totalRerankCount());
            profileBuilder.setSecondPhase(secondPhaseBuilder.build());

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
