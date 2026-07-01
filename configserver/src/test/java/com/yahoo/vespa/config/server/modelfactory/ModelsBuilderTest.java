// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.modelfactory;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.Version;
import com.yahoo.concurrent.UncheckedTimeoutException;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ModelFactory;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModelsBuilderTest {

    @Test
    public void unchecked_timeout_exception_is_propagated_without_wrapping() {
        var version = Version.fromString("8.1.1");
        var exception = new UncheckedTimeoutException("timed out building models");
        var registry = new ModelFactoryRegistry(List.of(new ThrowingModelFactory(version, exception)));
        var builder = new TestModelsBuilder(registry);
        var applicationPackage = new MockApplicationPackage.Builder().withEmptyHosts().withEmptyServices().build();

        var thrown = assertThrows(UncheckedTimeoutException.class,
                () -> builder.buildModels(ApplicationId.defaultId(),
                                          Optional.empty(),
                                          version,
                                          Optional.empty(),
                                          applicationPackage,
                                          new AllocatedHostsFromAllModels(),
                                          Instant.now()));
        assertTrue(thrown.getMessage().contains("timed out"));
    }

    private static class TestModelsBuilder extends ModelsBuilder<ModelResult> {

        TestModelsBuilder(ModelFactoryRegistry registry) {
            super(registry,
                  new ConfigserverConfig(new ConfigserverConfig.Builder()),
                  Zone.defaultZone(),
                  HostProvisionerProvider.empty(),
                  (level, message) -> {});
        }

        @Override
        protected ModelResult buildModelVersion(ModelFactory modelFactory, ApplicationPackage applicationPackage,
                                                ApplicationId applicationId, Optional<DockerImage> dockerImageRepository,
                                                Version wantedNodeVespaVersion) {
            modelFactory.createModel(null);
            throw new IllegalStateException("should not reach here");
        }

    }

    private static class ThrowingModelFactory implements ModelFactory {

        private final Version version;
        private final RuntimeException exception;

        ThrowingModelFactory(Version version, RuntimeException exception) {
            this.version = version;
            this.exception = exception;
        }

        @Override
        public Version version() { return version; }

        @Override
        public Model createModel(ModelContext modelContext) { throw exception; }

        @Override
        public ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters) {
            throw exception;
        }

    }

}
