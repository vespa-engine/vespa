// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.searchdefinition.derived.RankProfileList;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;

import java.util.List;
import java.util.Objects;

/**
 * Configuration of components for stateless model evaluation
 *
 * @author bratseth
 */
public class ContainerModelEvaluation implements RankProfilesConfig.Producer, RankingConstantsConfig.Producer {

    /** Global rank profiles, aka models */
    private final RankProfileList rankProfileList;

    public ContainerModelEvaluation(ContainerCluster cluster, RankProfileList rankProfileList) {
        this.rankProfileList = Objects.requireNonNull(rankProfileList, "rankProfileList cannot be null");
        cluster.addSimpleComponent(ModelsEvaluator.class.getName(), null, "model-evaluation");
    }

    public void prepare(List<Container> containers) {
        rankProfileList.sendConstantsTo(containers);
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        rankProfileList.getConfig(builder);
    }

    @Override
    public void getConfig(RankingConstantsConfig.Builder builder) {
        rankProfileList.getConfig(builder);
    }

}
