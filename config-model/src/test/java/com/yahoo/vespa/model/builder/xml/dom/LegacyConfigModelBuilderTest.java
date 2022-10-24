// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.text.XML;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class LegacyConfigModelBuilderTest {

    @Test
    void testThatProducerIsInserted() {
        String services = "<foo><config name=\"bar.foo\"><key>value</key></config></foo>";
        ModelBuilder builder = new ModelBuilder();
        Model model = builder.build(DeployState.createTestState(new MockApplicationPackage.Builder().withServices(services).build()),
                null, null, new MockRoot(), XML.getDocument(services).getDocumentElement());
        assertEquals(1, model.getContext().getParentProducer().getUserConfigs().size());
    }

    public static class Model extends ConfigModel {

        private final ConfigModelContext context;

        /**
         * Constructs a new config model given a context.
         *
         * @param modelContext The model context.
         */
        public Model(ConfigModelContext modelContext) {
            super(modelContext);
            this.context = modelContext;
        }

        public ConfigModelContext getContext() {
            return context;
        }
    }
    private static class ModelBuilder extends LegacyConfigModelBuilder<Model> {

        ModelBuilder() {
            super(Model.class);
        }

        @Override
        public void doBuild(Model model, Element element, ConfigModelContext modelContext) {
        }

        @Override
        public List<ConfigModelId> handlesElements() {
            return List.of(ConfigModelId.fromName("foo"));
        }
    }
}
