// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Replaces model id references in configs by their url.
 *
 * @author lesters
 * @author bratseth
 */
public class ModelIdResolver {

    public static final String HF_TOKENIZER = "huggingface-tokenizer";
    public static final String ONNX_MODEL = "onnx-model";
    public static final String BERT_VOCAB = "bert-vocabulary";
    public static final String SIGNIFICANCE_MODEL = "significance-model";

    private static Map<String, ProvidedModel> setupProvidedModels() {
        var m = new HashMap<String, ProvidedModel>();
        register(m, "minilm-l6-v2",          "https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx", Set.of(ONNX_MODEL));
        register(m, "mpnet-base-v2",         "https://data.vespa.oath.cloud/onnx_models/sentence-all-mpnet-base-v2.onnx", Set.of(ONNX_MODEL));
        register(m, "bert-base-uncased",     "https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt", Set.of(BERT_VOCAB));
        register(m, "flan-t5-vocab",         "https://data.vespa.oath.cloud/onnx_models/flan-t5-spiece.model", Set.of());
        register(m, "flan-t5-small-encoder", "https://data.vespa.oath.cloud/onnx_models/flan-t5-small-encoder-model.onnx", Set.of(ONNX_MODEL));
        register(m, "flan-t5-small-decoder", "https://data.vespa.oath.cloud/onnx_models/flan-t5-small-decoder-model.onnx", Set.of(ONNX_MODEL));
        register(m, "flan-t5-base-encoder",  "https://data.vespa.oath.cloud/onnx_models/flan-t5-base-encoder-model.onnx", Set.of(ONNX_MODEL));
        register(m, "flan-t5-base-decoder",  "https://data.vespa.oath.cloud/onnx_models/flan-t5-base-decoder-model.onnx", Set.of(ONNX_MODEL));
        register(m, "flan-t5-large-encoder", "https://data.vespa.oath.cloud/onnx_models/flan-t5-large-encoder-model.onnx", Set.of(ONNX_MODEL));
        register(m, "flan-t5-large-decoder", "https://data.vespa.oath.cloud/onnx_models/flan-t5-large-decoder-model.onnx", Set.of(ONNX_MODEL));

        register(m, "multilingual-e5-base", "https://data.vespa.oath.cloud/onnx_models/multilingual-e5-base/model.onnx", Set.of(ONNX_MODEL));
        register(m, "multilingual-e5-base-vocab", "https://data.vespa.oath.cloud/onnx_models/multilingual-e5-base/tokenizer.json", Set.of(HF_TOKENIZER));

        register(m, "multilingual-e5-small", "https://data.vespa.oath.cloud/onnx_models/multilingual-e5-small/model.onnx", Set.of(ONNX_MODEL));
        register(m, "multilingual-e5-small-vocab", "https://data.vespa.oath.cloud/onnx_models/multilingual-e5-small/tokenizer.json", Set.of(HF_TOKENIZER));

        register(m, "multilingual-e5-small-cpu-friendly", "https://data.vespa.oath.cloud/onnx_models/multilingual-e5-small-cpu-friendly/model.onnx", Set.of(ONNX_MODEL));
        register(m, "multilingual-e5-small-cpu-friendly-vocab", "https://data.vespa.oath.cloud/onnx_models/multilingual-e5-small-cpu-friendly/tokenizer.json", Set.of(HF_TOKENIZER));

        register(m, "e5-small-v2", "https://data.vespa.oath.cloud/onnx_models/e5-small-v2/model.onnx", Set.of(ONNX_MODEL));
        register(m, "e5-small-v2-vocab", "https://data.vespa.oath.cloud/onnx_models/e5-small-v2/tokenizer.json", Set.of(HF_TOKENIZER));

        register(m, "e5-small-v2-cpu-friendly", "https://data.vespa.oath.cloud/onnx_models/e5-small-v2-cpu-friendly/model.onnx", Set.of(ONNX_MODEL));
        register(m, "e5-small-v2-cpu-friendly-vocab", "https://data.vespa.oath.cloud/onnx_models/e5-small-v2-cpu-friendly/tokenizer.json", Set.of(HF_TOKENIZER));

        register(m, "e5-base-v2", "https://data.vespa.oath.cloud/onnx_models/e5-base-v2/model.onnx", Set.of(ONNX_MODEL));
        register(m, "e5-base-v2-vocab", "https://data.vespa.oath.cloud/onnx_models/e5-base-v2/tokenizer.json", Set.of(HF_TOKENIZER));

        register(m, "e5-large-v2", "https://data.vespa.oath.cloud/onnx_models/e5-large-v2/model.onnx", Set.of(ONNX_MODEL));
        register(m, "e5-large-v2-vocab", "https://data.vespa.oath.cloud/onnx_models/e5-large-v2/tokenizer.json", Set.of(HF_TOKENIZER));
        return Map.copyOf(m);
    }

    private record ProvidedModel(String modelId, URI uri, Set<String> tags) {
        ProvidedModel { tags = Set.copyOf(tags); }
    }

    private static void register(Map<String, ProvidedModel> models, String modelId, String uri, Set<String> tags) {
        models.put(modelId, new ProvidedModel(modelId, URI.create(uri), tags));
    }

    private static final Map<String, ProvidedModel> providedModels = setupProvidedModels();

    /**
     * Finds any config values of type 'model' below the given config element and
     * supplies the url attribute of them if a model id is specified and hosted is true
     * (regardless of whether an url is already specified).
     *
     * @param component the XML element of any component
     */
    public static void resolveModelIds(Element component, boolean hosted) {
        for (Element config : XML.getChildren(component, "config")) {
            for (Element value : XML.getChildren(config))
                transformModelValue(value, hosted);
        }
    }

    /** Expands a model config value into regular config values. */
    private static void transformModelValue(Element value, boolean hosted) {
        if ( ! value.hasAttribute("model-id")) return;

        if (hosted) {
            value.setAttribute("url", modelIdToUrl(value.getTagName(), value.getAttribute("model-id"), Set.of()));
            value.removeAttribute("path");
        }
        else if ( ! value.hasAttribute("url") && ! value.hasAttribute("path")) {
            throw onlyModelIdInHostedException(value.getTagName());
        }
    }

    public static ModelReference resolveToModelReference(
            String paramName, Optional<String> id, Optional<String> url, Optional<String> path, Set<String> requiredTags, DeployState state) {
        if (id.isEmpty()) return createModelReference(Optional.empty(), url, path, state);
        else if (state.isHosted())
            return createModelReference(id, Optional.of(modelIdToUrl(paramName, id.get(), requiredTags)), Optional.empty(), state);
        else if (url.isEmpty() && path.isEmpty()) throw onlyModelIdInHostedException(paramName);
        else return createModelReference(id, url, path, state);
    }

    private static ModelReference createModelReference(Optional<String> id, Optional<String> url, Optional<String> path, DeployState state) {
        var fileRef = path.map(p -> state.getFileRegistry().addFile(p));
        return ModelReference.unresolved(id, url.map(UrlReference::valueOf), fileRef);
    }

    private static IllegalArgumentException onlyModelIdInHostedException(String paramName) {
        return new IllegalArgumentException(paramName + " is configured with only a 'model-id'. " +
                                             "Add a 'path' or 'url' to deploy this outside Vespa Cloud");
    }

    private static String modelIdToUrl(String valueName, String modelId, Set<String> requiredTags) {
        if ( ! providedModels.containsKey(modelId))
            throw new IllegalArgumentException("Unknown model id '" + modelId + "' on '" + valueName + "'. Available models are [" +
                                               providedModels.keySet().stream().sorted().collect(Collectors.joining(", ")) + "]");
        var providedModel = providedModels.get(modelId);
        if (!providedModel.tags().containsAll(requiredTags)) {
            throw new IllegalArgumentException(
                    "Model '%s' on '%s' has tags %s but are missing required tags %s"
                            .formatted(modelId, valueName, providedModel.tags(), requiredTags));
        }
        return providedModel.uri().toString();
    }

}
