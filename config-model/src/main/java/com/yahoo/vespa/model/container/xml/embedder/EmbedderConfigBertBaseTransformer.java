package com.yahoo.vespa.model.container.xml.embedder;

import org.w3c.dom.Element;

import java.util.Map;

/**
 * Transforms embedding configuration to component configuration for the
 * BertBaseEmbedder using embedder.bert-base-embedder.def
 *
 * @author lesters
 */
public class EmbedderConfigBertBaseTransformer extends EmbedderConfigTransformer {

    private static final String BUNDLE = "model-integration";
    private static final String DEF = "embedding.bert-base-embedder";

    public EmbedderConfigBertBaseTransformer(Element spec, boolean hosted) {
        super(spec, hosted, BUNDLE, DEF);

        EmbedderOption.Builder modelOption = new EmbedderOption.Builder()
                .name("model")
                .required(true)
                .optionTransformer(new EmbedderOption.ModelOptionTransformer("transformerModelPath", "transformerModelUrl"));
        EmbedderOption.Builder vocabOption = new EmbedderOption.Builder()
                .name("vocab")
                .required(true)
                .optionTransformer(new EmbedderOption.ModelOptionTransformer("tokenizerVocabPath", "tokenizerVocabUrl"));

        // Defaults
        if (hosted) {
            modelOption.attributes(Map.of("id", "minilm-l6-v2")).value("");
            vocabOption.attributes(Map.of("id", "bert-base-uncased")).value("");
        }

        addOption(modelOption.build());
        addOption(vocabOption.build());
    }

}
