// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.ModelReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.search.significance.config.SignificanceConfig;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.yahoo.vespa.model.container.xml.ModelIdResolver.SIGNIFICANCE_MODEL;

/**
 * A registry for significance models.
 *
 * @author MariusArhaug
 *
 */
public class SignificanceModelRegistry extends SimpleComponent implements SignificanceConfig.Producer {

    private static final String CLASS = "com.yahoo.search.significance.impl.DefaultSignificanceModelRegistry";
    private static final String BUNDLE = "linguistics";

    private final List<SignificanceModelConfig> configList;

    public SignificanceModelRegistry(DeployState deployState, Element spec) {
        super(new ComponentModel(BundleInstantiationSpecification.fromStrings(CLASS, CLASS, BUNDLE)));
        configList = new ArrayList<>();

        for (Element modelElement : XML.getChildren(spec, "model")) {
            addConfig(
                    modelElement.getAttribute("language"),
                    Model.fromXml(deployState, modelElement, Set.of(SIGNIFICANCE_MODEL)).modelReference());
        }
    }


    public void addConfig(String language, ModelReference path) {
        configList.add(
                new SignificanceModelConfig(language, path)
        );
    }


    @Override
    public void getConfig(SignificanceConfig.Builder builder) {
        builder.model(
                configList.stream()
                .map(config -> new SignificanceConfig.Model.Builder()
                        .language(config.language)
                        .path(config.path)
                ).toList()
        );
    }


    class SignificanceModelConfig {
        private final String language;
        private final ModelReference path;

        public SignificanceModelConfig(String language, ModelReference path) {
            this.language = language;
            this.path = path;
        }

    }
}

