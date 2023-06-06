// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Replaces model id references in configs by their url.
 *
 * @author lesters
 * @author bratseth
 */
public class ModelIdResolver {

    private static Map<String, String> setupProvidedModels() {
        Map<String, String> models = new HashMap<>();
        models.put("minilm-l6-v2",          "https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx");
        models.put("mpnet-base-v2",         "https://data.vespa.oath.cloud/onnx_models/sentence-all-mpnet-base-v2.onnx");
        models.put("bert-base-uncased",     "https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt");
        models.put("flan-t5-vocab",         "https://data.vespa.oath.cloud/onnx_models/flan-t5-spiece.model");
        models.put("flan-t5-small-encoder", "https://data.vespa.oath.cloud/onnx_models/flan-t5-small-encoder-model.onnx");
        models.put("flan-t5-small-decoder", "https://data.vespa.oath.cloud/onnx_models/flan-t5-small-decoder-model.onnx");
        models.put("flan-t5-base-encoder",  "https://data.vespa.oath.cloud/onnx_models/flan-t5-base-encoder-model.onnx");
        models.put("flan-t5-base-decoder",  "https://data.vespa.oath.cloud/onnx_models/flan-t5-base-decoder-model.onnx");
        models.put("flan-t5-large-encoder", "https://data.vespa.oath.cloud/onnx_models/flan-t5-large-encoder-model.onnx");
        models.put("flan-t5-large-decoder", "https://data.vespa.oath.cloud/onnx_models/flan-t5-large-decoder-model.onnx");

        models.put("multilingual-e5-base", "https://data.vespa.oath.cloud/onnx_models/multilingual-e5-base/model.onnx");
        models.put("multilingual-e5-base-vocab", "https://data.vespa.oath.cloud/onnx_models/multilingual-e5-base/tokenizer.json");

        models.put("e5-small-v2", "https://data.vespa.oath.cloud/onnx_models/e5-small-v2/model.onnx");
        models.put("e5-small-v2-vocab", "https://data.vespa.oath.cloud/onnx_models/e5-small-v2/tokenizer.json");

        models.put("e5-base-v2", "https://data.vespa.oath.cloud/onnx_models/e5-base-v2/model.onnx");
        models.put("e5-base-v2-vocab", "https://data.vespa.oath.cloud/onnx_models/e5-base-v2/tokenizer.json");

        models.put("e5-large-v2", "https://data.vespa.oath.cloud/onnx_models/e5-large-v2/model.onnx");
        models.put("e5-large-v2-vocab", "https://data.vespa.oath.cloud/onnx_models/e5-large-v2/tokenizer.json");

        return Collections.unmodifiableMap(models);
    }

    private static final Map<String, String> providedModels = setupProvidedModels();

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
            value.setAttribute("url", modelIdToUrl(value.getTagName(), value.getAttribute("model-id")));
            value.removeAttribute("path");
        }
        else if ( ! value.hasAttribute("url") && ! value.hasAttribute("path")) {
            throw onlyModelIdInHostedException(value.getTagName());
        }
    }


    public static ModelReference resolveToModelReference(Element elem, DeployState state) {
        return resolveToModelReference(
                elem.getTagName(), XmlHelper.getOptionalAttribute(elem, "model-id"),
                XmlHelper.getOptionalAttribute(elem, "url"), XmlHelper.getOptionalAttribute(elem, "path"), state);
    }

    public static ModelReference resolveToModelReference(
            String paramName, Optional<String> id, Optional<String> url, Optional<String> path, DeployState state) {
        if (id.isEmpty()) return createModelReference(Optional.empty(), url, path, state);
        else if (state.isHosted())
            return createModelReference(id, Optional.of(modelIdToUrl(paramName, id.get())), Optional.empty(), state);
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

    private static String modelIdToUrl(String valueName, String modelId) {
        if ( ! providedModels.containsKey(modelId))
            throw new IllegalArgumentException("Unknown model id '" + modelId + "' on '" + valueName + "'. Available models are [" +
                                               providedModels.keySet().stream().sorted().collect(Collectors.joining(", ")) + "]");
        return providedModels.get(modelId);
    }

}
