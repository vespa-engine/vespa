// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.component.Version;
import com.yahoo.vespa.config.server.http.UnknownVespaVersionException;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ModelFactoryRegistryTest {
    @Test(expected = IllegalArgumentException.class)
    public void testThatOneFactoryIsRequired() {
        new ModelFactoryRegistry(new ComponentRegistry<>());
    }

    @Test
    public void testThatLatestVersionIsSelected() {
        Version versionA = new Version(5, 38, 4);
        Version versionB = new Version(5, 58, 1);
        Version versionC = new Version(5, 48, 44);
        Version versionD = new Version(5, 18, 44);
        TestFactory a = new TestFactory(versionA);
        TestFactory b = new TestFactory(versionB);
        TestFactory c = new TestFactory(versionC);
        TestFactory d = new TestFactory(versionD);

        for (int i = 0; i < 100; i++) {
            List<ModelFactory> randomOrder = Arrays.asList(a, b, c, d);
            Collections.shuffle(randomOrder);
            ModelFactoryRegistry registry = new ModelFactoryRegistry(randomOrder);
            assertEquals(a, registry.getFactory(versionA));
            assertEquals(b, registry.getFactory(versionB));
            assertEquals(c, registry.getFactory(versionC));
            assertEquals(d, registry.getFactory(versionD));
        }
    }

    @Test
    public void testThatAllFactoriesAreReturned() {
        TestFactory a = new TestFactory(new Version(5, 38, 4));
        TestFactory b = new TestFactory(new Version(5, 58, 1));
        TestFactory c = new TestFactory(new Version(5, 48, 44));
        TestFactory d = new TestFactory(new Version(5, 18, 44));
        ModelFactoryRegistry registry = new ModelFactoryRegistry(Arrays.asList(a, b, c, d));
        assertEquals(4, registry.getFactories().size());
        assertTrue(registry.getFactories().contains(a));
        assertTrue(registry.getFactories().contains(b));
        assertTrue(registry.getFactories().contains(c));
        assertTrue(registry.getFactories().contains(d));
    }

    @Test(expected = UnknownVespaVersionException.class)
    public void testThatUnknownVersionGivesError() {
        ModelFactoryRegistry registry = new ModelFactoryRegistry(List.of(new TestFactory(new Version(1, 2, 3))));
        registry.getFactory(new Version(3, 2, 1));
    }

    private static class TestFactory implements ModelFactory {

        private final Version version;

        TestFactory(Version version) {
            this.version = version;
        }

        @Override
        public Version version() {
            return version;
        }

        @Override
        public Model createModel(ModelContext modelContext) {
            return null;
        }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters) {
            return null;
        }
    }

}
