// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates model references in component configs.
 *
 * @author lesters
 * @author bratseth
 */
public class ModelConfigTransformer {

    private static final Map<String, String> providedModels =
            Map.of("minilm-l6-v2", "https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx",
                   "bert-base-uncased", "https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt");

    // Until we have optional path parameters, use services.xml as it is guaranteed to exist
    private final static String dummyPath = "services.xml";

    /**
     * Transforms the &lt;embedder ...&gt; element to component configuration.
     *
     * @param deployState the deploy state - as config generation can depend on context
     * @param component the XML element containing the &lt;embedder ...&gt;
     * @return a new XML element containting the &lt;component ...&gt; configuration
     */
    public static Element transform(DeployState deployState, Element component) {
        for (Element config : XML.getChildren(component, "config")) {
            for (Element value : XML.getChildren(config))
                transformModelValue(value, config, deployState.isHosted());
        }
        return component;
    }

    /** Expans a model config value into regular config values. */
    private static void transformModelValue(Element value, Element config, boolean hosted) {
        if (value.hasAttribute("path")) {
            addChild(value.getTagName() + "Url", "", config);
            addChild(value.getTagName() + "Path", value.getAttribute("path"), config);
            config.removeChild(value);
        }
        else if (value.hasAttribute("id") && hosted) {
            addChild(value.getTagName() + "Url", modelIdToUrl(value.getAttribute("id")), config);
            addChild(value.getTagName() + "Path", dummyPath, config);
            config.removeChild(value);
        }
        else if (value.hasAttribute("url")) {
            addChild(value.getTagName() + "Url", value.getAttribute("url"), config);
            addChild(value.getTagName() + "Path", dummyPath, config);
            config.removeChild(value);
        }
    }

    private static void addChild(String name, String value, Element parent) {
        Element element = parent.getOwnerDocument().createElement(name);
        element.setTextContent(value);
        parent.appendChild(element);
    }

    private static String modelIdToUrl(String id) {
        if ( ! providedModels.containsKey(id))
            throw new IllegalArgumentException("Unknown embedder model '" + id + "'. Available models are [" +
                                               providedModels.keySet().stream().sorted().collect(Collectors.joining(", ")) + "]");
        return providedModels.get(id);
    }

}
