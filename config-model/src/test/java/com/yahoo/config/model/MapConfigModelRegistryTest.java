// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class MapConfigModelRegistryTest {

    @Test
    void require_that_registry_finds_components() {
        ModelABuilder ba = new ModelABuilder();
        ModelBBuilder bb = new ModelBBuilder();
        ConfigModelRegistry registry = MapConfigModelRegistry.createFromList(ba, bb);
        assertNotNull(registry.resolve(ConfigModelId.fromName("modelA")));
        assertNotNull(registry.resolve(ConfigModelId.fromName("modelB")));
        assertEquals(ba, registry.resolve(ConfigModelId.fromName("modelA")).iterator().next());
        assertEquals(bb, registry.resolve(ConfigModelId.fromName("modelB")).iterator().next());
        assertTrue(registry.resolve(ConfigModelId.fromName("modelC")).isEmpty());
    }

    @Test
    void require_all_builders_for_a_tag() {
        ModelBBuilder b1 = new ModelBBuilder();
        ModelB2Builder b2 = new ModelB2Builder();
        ConfigModelRegistry registry = MapConfigModelRegistry.createFromList(b1, b2);
        Collection<ConfigModelBuilder> builders = registry.resolve(ConfigModelId.fromName("modelB"));
        assertEquals(2, builders.size());
        assertEquals(b1, builders.iterator().next());
        assertEquals(b2, builders.iterator().next());
    }

    private static class ModelB2Builder extends ConfigModelBuilder<ModelB> {
        public ModelB2Builder() {
            super(ModelB.class);
        }

        @Override
        public List<ConfigModelId> handlesElements() { return Collections.singletonList(ConfigModelId.fromName("modelB")); }
        @Override
        public void doBuild(ModelB model, Element spec, ConfigModelContext modelContext) { }
    }

    private static class ModelBBuilder extends ConfigModelBuilder<ModelB> {
        public ModelBBuilder() {
            super(ModelB.class);
        }
        @Override
        public List<ConfigModelId> handlesElements() { return Collections.singletonList(ConfigModelId.fromName("modelB")); }
        @Override
        public void doBuild(ModelB model, Element spec, ConfigModelContext modelContext) { }
    }

    private class ModelB extends ConfigModel {
        protected ModelB(ConfigModelContext modelContext) {
            super(modelContext);
        }
    }

    private static class ModelABuilder extends ConfigModelBuilder<ModelA> {
        public ModelABuilder() {
            super(ModelA.class);
        }
        @Override
        public List<ConfigModelId> handlesElements() { return Collections.singletonList(ConfigModelId.fromName("modelA")); }

        @Override
        public void doBuild(ModelA model, Element spec, ConfigModelContext modelContext) { }
    }

    private class ModelA extends ConfigModel {
        protected ModelA(ConfigModelContext modelContext) {
            super(modelContext);
        }
    }

}
