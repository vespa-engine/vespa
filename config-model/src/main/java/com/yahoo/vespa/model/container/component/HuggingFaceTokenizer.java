// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.language.huggingface.config.HuggingFaceTokenizerConfig;
import com.yahoo.language.huggingface.config.HuggingFaceTokenizerConfig.Padding;
import com.yahoo.language.huggingface.config.HuggingFaceTokenizerConfig.Truncation;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.xml.ModelIdResolver;
import org.w3c.dom.Element;

import java.util.Map;
import java.util.TreeMap;

import static com.yahoo.vespa.model.container.ContainerModelEvaluation.LINGUISTICS_BUNDLE_NAME;

/**
 * @author bjorncs
 */
public class HuggingFaceTokenizer extends TypedComponent implements HuggingFaceTokenizerConfig.Producer {

    private final Map<String, ModelReference> langToModel = new TreeMap<>();

    public HuggingFaceTokenizer(Element xml, DeployState state) {
        super("com.yahoo.language.huggingface.HuggingFaceTokenizer", LINGUISTICS_BUNDLE_NAME, xml);
        for (Element element : XML.getChildren(xml, "model")) {
            var lang = element.hasAttribute("language") ? element.getAttribute("language") : "unknown";
            langToModel.put(lang, ModelIdResolver.resolveToModelReference(element, state));
        }
    }

    @Override
    public void getConfig(HuggingFaceTokenizerConfig.Builder builder) {
        langToModel.forEach((lang, vocab) -> {
            builder.model.add(new HuggingFaceTokenizerConfig.Model.Builder().language(lang).path(vocab));
        });
        builder.truncation(Truncation.Enum.OFF).padding(Padding.Enum.OFF).addSpecialTokens(false);
    }
}
