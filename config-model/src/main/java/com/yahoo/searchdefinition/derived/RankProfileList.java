// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.OnnxModels;
import com.yahoo.searchdefinition.LargeRankExpressions;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.RankingConstants;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.Schema;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Logger;

/**
 * The derived rank profiles of a schema
 *
 * @author bratseth
 */
public class RankProfileList extends Derived implements RankProfilesConfig.Producer {

    private static final Logger log = Logger.getLogger(RankProfileList.class.getName());

    private final Map<String, RawRankProfile> rankProfiles = new java.util.LinkedHashMap<>();
    private final RankingConstants rankingConstants;
    private final LargeRankExpressions largeRankExpressions;
    private final OnnxModels onnxModels;

    public static final RankProfileList empty = new RankProfileList();

    private RankProfileList() {
        rankingConstants = new RankingConstants(null, Optional.empty());
        largeRankExpressions = new LargeRankExpressions(null);
        onnxModels = new OnnxModels(null, Optional.empty());
    }

    /**
     * Creates a rank profile list
     *
     * @param schema the schema this is a rank profile from
     * @param attributeFields the attribute fields to create a ranking for
     */
    public RankProfileList(Schema schema,
                           RankingConstants rankingConstants,
                           LargeRankExpressions largeRankExpressions,
                           OnnxModels onnxModels,
                           AttributeFields attributeFields,
                           RankProfileRegistry rankProfileRegistry,
                           QueryProfileRegistry queryProfiles,
                           ImportedMlModels importedModels,
                           ModelContext.Properties deployProperties,
                           ExecutorService executor) {
        setName(schema == null ? "default" : schema.getName());
        this.rankingConstants = rankingConstants;
        this.largeRankExpressions = largeRankExpressions;
        this.onnxModels = onnxModels;  // as ONNX models come from parsing rank expressions
        deriveRankProfiles(rankProfileRegistry, queryProfiles, importedModels, schema, attributeFields, deployProperties, executor);
    }

    private boolean areDependenciesReady(RankProfile rank, RankProfileRegistry registry) {
        return rank.inheritedNames().isEmpty() ||
               rankProfiles.keySet().containsAll(rank.inheritedNames()) ||
               (rank.schema() != null && rank.inheritedNames().stream().allMatch(name -> registry.resolve(rank.schema().getDocument(), name) != null));
    }

    private void deriveRankProfiles(RankProfileRegistry rankProfileRegistry,
                                    QueryProfileRegistry queryProfiles,
                                    ImportedMlModels importedModels,
                                    Schema schema,
                                    AttributeFields attributeFields,
                                    ModelContext.Properties deployProperties,
                                    ExecutorService executor) {
        if (schema != null) { // profiles belonging to a search have a default profile
            RawRankProfile rawRank = new RawRankProfile(rankProfileRegistry.get(schema, "default"),
                    largeRankExpressions, queryProfiles, importedModels, attributeFields, deployProperties);
            rankProfiles.put(rawRank.getName(), rawRank);
        }

        Map<String, RankProfile> remaining = new LinkedHashMap<>();
        rankProfileRegistry.rankProfilesOf(schema).forEach(rank -> remaining.put(rank.name(), rank));
        remaining.remove("default");
        while (!remaining.isEmpty()) {
            List<RankProfile> ready = new ArrayList<>();
            remaining.forEach((name, rank) -> {
                if (areDependenciesReady(rank, rankProfileRegistry)) ready.add(rank);
            });
            processRankProfiles(ready, queryProfiles, importedModels, schema, attributeFields, deployProperties, executor);
            ready.forEach(rank -> remaining.remove(rank.name()));
        }
    }
    private void processRankProfiles(List<RankProfile> ready,
                                     QueryProfileRegistry queryProfiles,
                                     ImportedMlModels importedModels,
                                     Schema schema,
                                     AttributeFields attributeFields,
                                     ModelContext.Properties deployProperties,
                                     ExecutorService executor) {
        Map<String, Future<RawRankProfile>> futureRawRankProfiles = new LinkedHashMap<>();
        for (RankProfile rank : ready) {
            if (schema == null) {
                onnxModels.add(rank.onnxModels());
            }

            futureRawRankProfiles.put(rank.name(), executor.submit(() -> new RawRankProfile(rank, largeRankExpressions, queryProfiles, importedModels,
                                                                                            attributeFields, deployProperties)));
        }
        try {
            for (Future<RawRankProfile> rawFuture : futureRawRankProfiles.values()) {
                RawRankProfile rawRank = rawFuture.get();
                rankProfiles.put(rawRank.getName(), rawRank);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    public OnnxModels getOnnxModels() {
        return onnxModels;
    }

    public Map<String, RawRankProfile> getRankProfiles() {
        return rankProfiles;
    }

    /** Returns the raw rank profile with the given name, or null if it is not present */
    public RawRankProfile getRankProfile(String name) {
        return rankProfiles.get(name);
    }

    @Override
    public String getDerivedName() { return "rank-profiles"; }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        for (RawRankProfile rank : rankProfiles.values() ) {
            rank.getConfig(builder);
        }
    }

    public void getConfig(RankingExpressionsConfig.Builder builder) {
        largeRankExpressions.expressions().forEach((expr) -> builder.expression.add(new RankingExpressionsConfig.Expression.Builder().name(expr.getName()).fileref(expr.getFileReference())));
    }

    public void getConfig(RankingConstantsConfig.Builder builder) {
        for (RankingConstant constant : rankingConstants.asMap().values()) {
            if ("".equals(constant.getFileReference()))
                log.warning("Illegal file reference " + constant); // Let tests pass ... we should find a better way
            else
                builder.constant(new RankingConstantsConfig.Constant.Builder()
                                         .name(constant.getName())
                                         .fileref(constant.getFileReference())
                                         .type(constant.getType()));
        }
    }

    public void getConfig(OnnxModelsConfig.Builder builder) {
        for (OnnxModel model : onnxModels.asMap().values()) {
            if ("".equals(model.getFileReference()))
                log.warning("Illegal file reference " + model); // Let tests pass ... we should find a better way
            else {
                OnnxModelsConfig.Model.Builder modelBuilder = new OnnxModelsConfig.Model.Builder();
                modelBuilder.dry_run_on_setup(true);
                modelBuilder.name(model.getName());
                modelBuilder.fileref(model.getFileReference());
                model.getInputMap().forEach((name, source) -> modelBuilder.input(new OnnxModelsConfig.Model.Input.Builder().name(name).source(source)));
                model.getOutputMap().forEach((name, as) -> modelBuilder.output(new OnnxModelsConfig.Model.Output.Builder().name(name).as(as)));
                if (model.getStatelessExecutionMode().isPresent())
                    modelBuilder.stateless_execution_mode(model.getStatelessExecutionMode().get());
                if (model.getStatelessInterOpThreads().isPresent())
                    modelBuilder.stateless_interop_threads(model.getStatelessInterOpThreads().get());
                if (model.getStatelessIntraOpThreads().isPresent())
                    modelBuilder.stateless_intraop_threads(model.getStatelessIntraOpThreads().get());

                builder.model(modelBuilder);
            }
        }
    }
}
