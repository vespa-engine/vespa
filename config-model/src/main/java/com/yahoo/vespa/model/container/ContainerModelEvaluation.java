// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import ai.vespa.models.evaluation.ModelsEvaluator;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.schema.derived.RankProfileList;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration of components for stateless model evaluation
 *
 * @author bratseth
 */

public class ContainerModelEvaluation implements
        RankProfilesConfig.Producer,
        RankingConstantsConfig.Producer,
        OnnxModelsConfig.Producer,
        RankingExpressionsConfig.Producer {

    public final static String EVALUATION_BUNDLE_NAME = "model-evaluation";
    public final static String INTEGRATION_BUNDLE_NAME = "model-integration";
    public final static String ONNXRUNTIME_BUNDLE_NAME = "container-onnxruntime.jar";
    public final static String ONNX_RUNTIME_CLASS = "ai.vespa.modelintegration.evaluator.OnnxRuntime";

    private final static String EVALUATOR_NAME = ModelsEvaluator.class.getName();
    private final static String REST_HANDLER_NAME = "ai.vespa.models.handler.ModelsEvaluationHandler";
    private final static String REST_BINDING_PATH = "/model-evaluation/v1";

    public static final Path MODEL_EVALUATION_BUNDLE_FILE = PlatformBundles.absoluteBundlePath(EVALUATION_BUNDLE_NAME);
    public static final Path MODEL_INTEGRATION_BUNDLE_FILE = PlatformBundles.absoluteBundlePath(INTEGRATION_BUNDLE_NAME);
    public static final Path ONNXRUNTIME_BUNDLE_FILE = PlatformBundles.absoluteBundlePath(ONNXRUNTIME_BUNDLE_NAME);

    /** Global rank profiles, aka models */
    private final RankProfileList rankProfileList;

    public ContainerModelEvaluation(ApplicationContainerCluster cluster, RankProfileList rankProfileList) {
        this.rankProfileList = Objects.requireNonNull(rankProfileList, "rankProfileList cannot be null");
        cluster.addSimpleComponent(EVALUATOR_NAME, null, EVALUATION_BUNDLE_NAME);
        cluster.addComponent(ContainerModelEvaluation.getHandler());
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        rankProfileList.getConfig(builder);
    }

    @Override
    public void getConfig(RankingConstantsConfig.Builder builder) {
        rankProfileList.getConfig(builder);
    }

    @Override
    public void getConfig(OnnxModelsConfig.Builder builder) {
        rankProfileList.getConfig(builder);
    }

    public void getConfig(RankingExpressionsConfig.Builder builder) {
        rankProfileList.getConfig(builder);
    }

    public static Handler getHandler() {
        Handler handler = new Handler(new ComponentModel(REST_HANDLER_NAME, null, EVALUATION_BUNDLE_NAME));
        handler.addServerBindings(
                SystemBindingPattern.fromHttpPath(REST_BINDING_PATH),
                SystemBindingPattern.fromHttpPath(REST_BINDING_PATH + "/*"));
        return handler;
    }

}
