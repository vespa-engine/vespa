// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Translates config in services.xml of the form
 *
 *     &lt;embedder id="..." class="..." bundle="..." def="..."&gt;
 *         &lt;!-- options --&gt;
 *     &lt;/embedder&gt;
 *
 * to component configuration of the form
 *
 *     &lt;component id="..." class="..." bundle="..."&gt;
 *         &lt;config name=def&gt;
 *             &lt;!-- options --&gt;
 *         &lt;/config&gt;
 *     &lt;/component&gt;
 *
 * with some added interpretations based on recognizing the class.
 *
 * @author lesters
 * @author bratseth
 */
public class EmbedderConfigTransformer {

    private static final Map<String, String> providedModels =
            Map.of("minilm-l6-v2", "https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx",
                   "bert-base-uncased", "https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt");

    // Until we have optional path parameters, use services.xml as it is guaranteed to exist
    private final static String dummyPath = "services.xml";

    /**
     * Transforms the &lt;embedder ...&gt; element to component configuration.
     *
     * @param deployState the deploy state - as config generation can depend on context
     * @param embedder the XML element containing the &lt;embedder ...&gt;
     * @return a new XML element containting the &lt;component ...&gt; configuration
     */
    public static Element transform(DeployState deployState, Element embedder) {
        String embedderId = XML.attribute("id", embedder).orElseThrow(); // Required by schema
        String embedderClass = XML.attribute("class", embedder).orElse(embedderId);

        Element component = XML.getDocumentBuilder().newDocument().createElement("component");
        component.setAttribute("id", embedderId);
        component.setAttribute("class", embedderClass);
        component.setAttribute("bundle", XML.attribute("bundle", embedder).orElse("model-integration"));

        String configDef = embedderConfigFrom(embedder, embedderClass);
        if ( ! configDef.isEmpty()) {
            Element config = component.getOwnerDocument().createElement("config");
            config.setAttribute("name", configDef);
            for (Element child : XML.getChildren(embedder))
                addConfigValue(child, config, deployState.isHosted());
            component.appendChild(config);
        }
        else if ( ! XML.getChildren(embedder).isEmpty()) {
            throw new IllegalArgumentException("Embedder '" + embedder.getAttribute("id") + "' does not specify " +
                                               "a 'def' parameter so it cannot contain config values");
        }

        return component;
    }

    /** Adds a config value from an embedder element into a regular config. */
    private static void addConfigValue(Element value, Element config, boolean hosted) {
        if (value.hasAttribute("path")) {
            addChild(value.getTagName() + "Url", "", config);
            addChild(value.getTagName() + "Path", value.getAttribute("path"), config);
        }
        else if (value.hasAttribute("id") && hosted) {
            addChild(value.getTagName() + "Url", modelIdToUrl(value.getAttribute("id")), config);
            addChild(value.getTagName() + "Path", dummyPath, config);
        }
        else if (value.hasAttribute("url")) {
            addChild(value.getTagName() + "Url", value.getAttribute("url"), config);
            addChild(value.getTagName() + "Path", dummyPath, config);
        }
        else {
            addChild(value.getTagName(), XML.getValue(value), config);
        }
    }

    private static void addChild(String name, String value, Element parent) {
        Element element = parent.getOwnerDocument().createElement(name);
        element.setTextContent(value);
        parent.appendChild(element);
    }

    private static String embedderConfigFrom(Element embedder, String embedderClass) {
        String explicitDefinition = embedder.getAttribute("def");
        if ( ! explicitDefinition.isEmpty()) return explicitDefinition;

        // Implicit from class name
        return switch (embedderClass) {
            case "ai.vespa.embedding.BertBaseEmbedder" -> "embedding.bert-base-embedder";
            default -> "";
        };
    }

    private static String modelIdToUrl(String id) {
        if ( ! providedModels.containsKey(id))
            throw new IllegalArgumentException("Unknown embedder model '" + id + "'. Available models are [" +
                                               providedModels.keySet().stream().sorted().collect(Collectors.joining(", ")) + "]");
        return providedModels.get(id);
    }

}
