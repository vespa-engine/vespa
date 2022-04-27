// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.config;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.search.SchemaInfoConfig;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translation between schema info configuration and schema objects.
 *
 * @author bratseth
 */
class SchemaInfoConfigurer {

    static List<Schema> toSchemas(SchemaInfoConfig documentdbInfoConfig) {
        return documentdbInfoConfig.schema().stream().map(config -> toSchema(config)).collect(Collectors.toList());
    }

    static Schema toSchema(SchemaInfoConfig.Schema schemaInfoConfig) {
        Schema.Builder builder = new Schema.Builder(schemaInfoConfig.name());
        for (var profileConfig : schemaInfoConfig.rankprofile()) {
            RankProfile.Builder profileBuilder = new RankProfile.Builder(profileConfig.name());
            profileBuilder.setHasSummaryFeatures(profileConfig.hasSummaryFeatures());
            profileBuilder.setHasRankFeatures(profileConfig.hasRankFeatures());
            for (var inputConfig : profileConfig.input())
                profileBuilder.addInput(inputConfig.name(), TensorType.fromSpec(inputConfig.type()));
            builder.add(profileBuilder.build());
        }
        return builder.build();
    }

    static Map<String, List<String>> toClusters(QrSearchersConfig config) {
        Map<String, List<String>> clusters = new HashMap<>();
        for (int i = 0; i < config.searchcluster().size(); ++i) {
            List<String> schemas = new ArrayList<>();
            String clusterName = config.searchcluster(i).name();
            for (int j = 0; j < config.searchcluster(i).searchdef().size(); ++j)
                schemas.add(config.searchcluster(i).searchdef(j));
            clusters.put(clusterName, schemas);
        }
        return clusters;
    }

}
