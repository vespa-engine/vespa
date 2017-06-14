// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.provision.Version;
import com.yahoo.vespa.config.server.http.UnknownVespaVersionException;
import com.yahoo.vespa.config.server.modelfactory.ModelFactoryRegistry;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 */
public class ModelFactoryRegistryTest {
    @Test(expected = IllegalArgumentException.class)
    public void testThatOneFactoryIsRequired() {
        new ModelFactoryRegistry(new ComponentRegistry<>());
    }

    @Test
    public void testThatLatestVersionIsSelected() {
        Version versionA = Version.fromIntValues(5, 38, 4);
        Version versionB = Version.fromIntValues(5, 58, 1);
        Version versionC = Version.fromIntValues(5, 48, 44);
        Version versionD = Version.fromIntValues(5, 18, 44);
        TestFactory a = new TestFactory(versionA);
        TestFactory b = new TestFactory(versionB);
        TestFactory c = new TestFactory(versionC);
        TestFactory d = new TestFactory(versionD);

        for (int i = 0; i < 100; i++) {
            List<ModelFactory> randomOrder = Arrays.asList(a, b, c, d);
            Collections.shuffle(randomOrder);
            ModelFactoryRegistry registry = new ModelFactoryRegistry(randomOrder);
            assertThat(registry.getFactory(versionA), is(a));
            assertThat(registry.getFactory(versionB), is(b));
            assertThat(registry.getFactory(versionC), is(c));
            assertThat(registry.getFactory(versionD), is(d));
        }
    }

    @Test
    public void testThatAllFactoriesAreReturned() {
        TestFactory a = new TestFactory(Version.fromIntValues(5, 38, 4));
        TestFactory b = new TestFactory(Version.fromIntValues(5, 58, 1));
        TestFactory c = new TestFactory(Version.fromIntValues(5, 48, 44));
        TestFactory d = new TestFactory(Version.fromIntValues(5, 18, 44));
        ModelFactoryRegistry registry = new ModelFactoryRegistry(Arrays.asList(a, b, c, d));
        assertThat(registry.getFactories().size(), is(4));
        assertTrue(registry.getFactories().contains(a));
        assertTrue(registry.getFactories().contains(b));
        assertTrue(registry.getFactories().contains(c));
        assertTrue(registry.getFactories().contains(d));
    }

    @Test(expected = UnknownVespaVersionException.class)
    public void testThatUnknownVersionGivesError() {
        ModelFactoryRegistry registry = new ModelFactoryRegistry(Arrays.asList(new TestFactory(Version.fromIntValues(1, 2, 3))));
        registry.getFactory(Version.fromIntValues(3, 2, 1));
    }

    private static class TestFactory implements ModelFactory {
        private final Version version;

        public TestFactory(Version version) {
            this.version = version;
        }

        @Override
        public Version getVersion() {
            return version;
        }

        @Override
        public Model createModel(ModelContext modelContext) {
            return null;
        }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, boolean ignoreValidationErrors) {
            return null;
        }
    }
}
