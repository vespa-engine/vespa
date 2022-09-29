// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.LargeRankingExpressions;
import com.yahoo.schema.OnnxModel;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * The derived rank profiles of a schema
 *
 * @author bratseth
 */
public class RankProfileList extends Derived implements RankProfilesConfig.Producer {

    private final Map<String, RawRankProfile> rankProfiles;
    private final FileDistributedConstants constants;
    private final LargeRankingExpressions largeRankingExpressions;
    private final FileDistributedOnnxModels onnxModels;

    public static final RankProfileList empty = new RankProfileList();

    private RankProfileList() {
        constants = new FileDistributedConstants(null, List.of());
        largeRankingExpressions = new LargeRankingExpressions(null);
        onnxModels = new FileDistributedOnnxModels(null, List.of());
        rankProfiles = Map.of();
    }

    /**
     * Creates a rank profile list
     *
     * @param schema the schema this is a rank profile from
     * @param attributeFields the attribute fields to create a ranking for
     */
    public RankProfileList(Schema schema,
                           LargeRankingExpressions largeRankingExpressions,
                           AttributeFields attributeFields,
                           DeployState deployState) {
        setName(schema == null ? "default" : schema.getName());
        this.largeRankingExpressions = largeRankingExpressions;
        this.rankProfiles = deriveRankProfiles(schema, attributeFields, deployState);
        this.constants = deriveFileDistributedConstants(schema, rankProfiles.values(), deployState);
        this.onnxModels = deriveFileDistributedOnnxModels(schema, rankProfiles.values(), deployState);
    }

    private boolean areDependenciesReady(RankProfile rank, RankProfileRegistry registry, Set<String> processedProfiles) {
        return rank.inheritedNames().isEmpty() ||
               processedProfiles.containsAll(rank.inheritedNames()) ||
               (rank.schema() != null && rank.inheritedNames().stream().allMatch(name -> registry.resolve(rank.schema().getDocument(), name) != null));
    }

    private Map<String, RawRankProfile>  deriveRankProfiles(Schema schema,
                                                            AttributeFields attributeFields,
                                                            DeployState deployState) {
        Map<String,  RawRankProfile> rawRankProfiles = new LinkedHashMap<>();
        if (schema != null) { // profiles belonging to a schema have a default profile
            RawRankProfile rawRank = new RawRankProfile(deployState.rankProfileRegistry().get(schema, "default"),
                                                        largeRankingExpressions,
                                                        deployState.getQueryProfiles().getRegistry(),
                                                        deployState.getImportedModels(),
                                                        attributeFields,
                                                        deployState.getProperties());
            rawRankProfiles.put(rawRank.getName(), rawRank);
        }

        Map<String, RankProfile> remaining = new LinkedHashMap<>();
        deployState.rankProfileRegistry().rankProfilesOf(schema).forEach(rank -> remaining.put(rank.name(), rank));
        remaining.remove("default");
        while (!remaining.isEmpty()) {
            List<RankProfile> ready = new ArrayList<>();
            remaining.forEach((name, profile) -> {
                if (areDependenciesReady(profile, deployState.rankProfileRegistry(), rawRankProfiles.keySet()))
                    ready.add(profile);
            });
            rawRankProfiles.putAll(processRankProfiles(ready,
                                                       deployState.getQueryProfiles().getRegistry(),
                                                       deployState.getImportedModels(),
                                                       attributeFields,
                                                       deployState.getProperties(),
                                                       deployState.getExecutor()));
            ready.forEach(rank -> remaining.remove(rank.name()));
        }
        return rawRankProfiles;
    }

    private Map<String, RawRankProfile> processRankProfiles(List<RankProfile> profiles,
                                                            QueryProfileRegistry queryProfiles,
                                                            ImportedMlModels importedModels,
                                                            AttributeFields attributeFields,
                                                            ModelContext.Properties deployProperties,
                                                            ExecutorService executor) {
        Map<String, Future<RawRankProfile>> futureRawRankProfiles = new LinkedHashMap<>();
        for (RankProfile profile : profiles) {
            futureRawRankProfiles.put(profile.name(), executor.submit(() -> new RawRankProfile(profile, largeRankingExpressions, queryProfiles, importedModels,
                                                                                               attributeFields, deployProperties)));
        }
        try {
            Map<String,  RawRankProfile> rawRankProfiles = new LinkedHashMap<>();
            for (Future<RawRankProfile> rawFuture : futureRawRankProfiles.values()) {
                RawRankProfile rawRank = rawFuture.get();
                rawRankProfiles.put(rawRank.getName(), rawRank);
            }
            return rawRankProfiles;
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FileDistributedConstants deriveFileDistributedConstants(Schema schema,
                                                                           Collection<RawRankProfile> rankProfiles,
                                                                           DeployState deployState) {
        Map<Reference, RankProfile.Constant> allFileConstants = new HashMap<>();
        addFileConstants(schema != null ? schema.constants().values() : List.of(),
                         allFileConstants,
                         schema != null ? schema.toString() : "[global]");
        for (var profile : rankProfiles)
            addFileConstants(profile.constants(), allFileConstants, profile.toString());
        return new FileDistributedConstants(deployState.getFileRegistry(), allFileConstants.values());
    }

    private static void addFileConstants(Collection<RankProfile.Constant> source,
                                         Map<Reference, RankProfile.Constant> destination,
                                         String sourceName) {
        for (var constant : source) {
            if (constant.valuePath().isEmpty()) continue;
            var existing = destination.get(constant.name());
            if ( existing != null && ! constant.equals(existing)) {
                throw new IllegalArgumentException("Duplicate constants: " + sourceName + " have " + constant +
                                                   ", but we already have " + existing +
                                                   ": Value reference constants must be unique across all rank profiles/models");
            }
            destination.put(constant.name(), constant);
        }
    }

    private static FileDistributedOnnxModels deriveFileDistributedOnnxModels(Schema schema,
                                                                             Collection<RawRankProfile> rankProfiles,
                                                                             DeployState deployState) {
        Map<String, OnnxModel> allModels = new LinkedHashMap<>();
        addOnnxModels(schema != null ? schema.onnxModels().values() : List.of(),
                      allModels,
                      schema != null ? schema.toString() : "[global]");
        for (var profile : rankProfiles)
            addOnnxModels(profile.onnxModels(), allModels, profile.toString());
        return new FileDistributedOnnxModels(deployState.getFileRegistry(), allModels.values());
    }

    private static void addOnnxModels(Collection<OnnxModel> source,
                                      Map<String, OnnxModel> destination,
                                      String sourceName) {
        for (var model : source) {
            var existing = destination.get(model.getName());
            if ( existing != null && ! model.equals(existing)) {
                throw new IllegalArgumentException("Duplicate onnx model: " + sourceName + " have " + model +
                                                   ", but we already have " + existing +
                                                   ": Onnx models must be unique across all rank profiles/models");
            }
            destination.put(model.getName(), model);
        }
    }

    public Map<String, RawRankProfile> getRankProfiles() { return rankProfiles; }
    public FileDistributedConstants constants() { return constants; }
    public FileDistributedOnnxModels getOnnxModels() { return onnxModels; }

    @Override
    public String getDerivedName() { return "rank-profiles"; }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        for (RawRankProfile rank : rankProfiles.values() ) {
            rank.getConfig(builder);
        }
    }

    public void getConfig(RankingExpressionsConfig.Builder builder) {
        largeRankingExpressions.expressions().forEach((expr) -> builder.expression.add(new RankingExpressionsConfig.Expression.Builder().name(expr.getName()).fileref(expr.getFileReference())));
    }

    public void getConfig(RankingConstantsConfig.Builder builder) {
        constants.getConfig(builder);
    }

    public void getConfig(OnnxModelsConfig.Builder builder) {
        onnxModels.getConfig(builder);
    }

}
