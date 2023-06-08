// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.language.huggingface.config.HuggingFaceTokenizerConfig;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.xml.ModelIdResolver;
import org.w3c.dom.Element;

import java.util.Map;
import java.util.TreeMap;

import static com.yahoo.config.model.builder.xml.XmlHelper.getOptionalChildValue;
import static com.yahoo.vespa.model.container.ContainerModelEvaluation.LINGUISTICS_BUNDLE_NAME;

/**
 * @author bjorncs
 */
public class HuggingFaceTokenizer extends TypedComponent implements HuggingFaceTokenizerConfig.Producer {

    private final Map<String, ModelReference> langToModel = new TreeMap<>();
    private final Boolean specialTokens;
    private final Integer maxLength;
    private final Boolean truncation;

    public HuggingFaceTokenizer(Element xml, DeployState state) {
        super("com.yahoo.language.huggingface.HuggingFaceTokenizer", LINGUISTICS_BUNDLE_NAME, xml);
        for (Element element : XML.getChildren(xml, "model")) {
            var lang = element.hasAttribute("language") ? element.getAttribute("language") : "unknown";
            langToModel.put(lang, ModelIdResolver.resolveToModelReference(element, state));
        }
        specialTokens = getOptionalChildValue(xml, "special-tokens").map(Boolean::parseBoolean).orElse(null);
        maxLength = getOptionalChildValue(xml, "max-length").map(Integer::parseInt).orElse(null);
        truncation = getOptionalChildValue(xml, "truncation").map(Boolean::parseBoolean).orElse(null);
    }

    @Override
    public void getConfig(HuggingFaceTokenizerConfig.Builder builder) {
        langToModel.forEach((lang, vocab) -> {
            builder.model.add(new HuggingFaceTokenizerConfig.Model.Builder().language(lang).path(vocab));
        });
        if (specialTokens != null) builder.addSpecialTokens(specialTokens);
        if (maxLength != null) builder.maxLength(maxLength);
        if (truncation != null) builder.truncation(truncation);
    }
}
