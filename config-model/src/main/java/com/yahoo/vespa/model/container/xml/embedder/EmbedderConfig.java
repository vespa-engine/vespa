package com.yahoo.vespa.model.container.xml.embedder;

import com.yahoo.config.model.deploy.DeployState;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
 */
public class EmbedderConfig {

    static EmbedderConfigTransformer getEmbedderTransformer(Element spec, boolean hosted) {
        String classId = getEmbedderClass(spec);
        switch (classId) {
            case "ai.vespa.embedding.BertBaseEmbedder": return new EmbedderConfigBertBaseTransformer(spec, hosted);
        }
        return new EmbedderConfigTransformer(spec, hosted);
    }

    static String modelIdToUrl(String id) {
        switch (id) {
            case "test-model-id":
                return "test-model-url";
            case "minilm-l6-v2":
                return "https://data.vespa.oath.cloud/onnx_models/sentence_all_MiniLM_L6_v2.onnx";
            case "bert-base-uncased":
                return "https://data.vespa.oath.cloud/onnx_models/bert-base-uncased-vocab.txt";
        }
        throw new IllegalArgumentException("Unknown model id: '" + id + "'");
    }

    /**
     * Transforms the &lt;embedder ...&gt; element to component configuration.
     *
     * @param deployState the deploy state - as config generation can depend on context
     * @param embedderSpec the XML element containing the &lt;embedder ...&gt;
     * @return a new XML element containting the &lt;component ...&gt; configuration
     */
    public static Element transform(DeployState deployState, Element embedderSpec) {
        EmbedderConfigTransformer transformer = getEmbedderTransformer(embedderSpec, deployState.isHosted());
        NodeList children = embedderSpec.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                transformer.addOption((Element) child);
            }
        }
        return transformer.createComponentConfig(deployState);
    }

    private static String getEmbedderClass(Element spec) {
        if (spec.hasAttribute("class")) {
            return spec.getAttribute("class");
        }
        if (spec.hasAttribute("id")) {
            return spec.getAttribute("id");
        }
        throw new IllegalArgumentException("Embedder specification does not have a required class attribute");
    }


}
