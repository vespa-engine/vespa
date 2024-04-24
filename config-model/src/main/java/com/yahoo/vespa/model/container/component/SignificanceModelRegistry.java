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

    private static final String CLASS = "com.yahoo.language.significance.impl.DefaultSignificanceModelRegistry";
    private static final String BUNDLE = null;

    private final List<SignificanceModelConfig> configList = new ArrayList<>();

    public SignificanceModelRegistry(DeployState deployState, Element spec) {
        super(new ComponentModel(BundleInstantiationSpecification.fromStrings(CLASS, CLASS, BUNDLE)));
        if (spec != null) {

            for (Element modelElement : XML.getChildren(spec, "model")) {
                addConfig(Model.fromXml(deployState, modelElement, Set.of(SIGNIFICANCE_MODEL)).modelReference());
            }
        }
    }


    public void addConfig(ModelReference path) {
        configList.add(
                new SignificanceModelConfig(path)
        );
    }


    @Override
    public void getConfig(SignificanceConfig.Builder builder) {
        builder.model(
                configList.stream()
                .map(config -> new SignificanceConfig.Model.Builder()
                        .path(config.path)
                ).toList()
        );
    }


    static class SignificanceModelConfig {
        private final ModelReference path;

        public SignificanceModelConfig(ModelReference path) {
            this.path = path;
        }

    }
}

