// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ulf Lilleengen
 */
public class DeployStateTest {

    @Test
    void testProvisionerIsSet() {
        DeployState.Builder builder = new DeployState.Builder();
        HostProvisioner provisioner = new InMemoryProvisioner(true, false, "foo.yahoo.com");
        builder.modelHostProvisioner(provisioner);
        DeployState state = builder.build();
        assertEquals(provisioner, state.getProvisioner());
    }

    @Test
    void testBuilder() {
        DeployState.Builder builder = new DeployState.Builder();
        ApplicationPackage app = MockApplicationPackage.createEmpty();
        builder.permanentApplicationPackage(Optional.of(app));
        DeployState state = builder.build();
        assertEquals(app, state.getPermanentApplicationPackage().get());
    }

    @Test
    void testPreviousModelIsProvided() throws IOException, SAXException {
        VespaModel prevModel = new VespaModel(MockApplicationPackage.createEmpty());
        DeployState.Builder builder = new DeployState.Builder();
        assertEquals(prevModel, builder.previousModel(prevModel).build().getPreviousModel().get());
    }

    @Test
    void testProperties() {
        DeployState.Builder builder = new DeployState.Builder();
        DeployState state = builder.build();
        assertEquals(ApplicationId.defaultId(), state.getProperties().applicationId());
        ApplicationId customId = new ApplicationId.Builder()
                .tenant("bar")
                .applicationName("foo").instanceName("quux").build();
        ModelContext.Properties properties = new TestProperties().setApplicationId(customId);
        builder.properties(properties);
        state = builder.build();
        assertEquals(customId, state.getProperties().applicationId());
    }

    @Test
    void testDefinitionRepoIsUsed() {
        Map<ConfigDefinitionKey, com.yahoo.vespa.config.buildergen.ConfigDefinition> defs = new LinkedHashMap<>();
        defs.put(new ConfigDefinitionKey("foo", "bar"), new com.yahoo.vespa.config.buildergen.ConfigDefinition("foo", new String[]{"namespace=bar", "foo int default=0"}));
        defs.put(new ConfigDefinitionKey("test2", "a.b"),
                new com.yahoo.vespa.config.buildergen.ConfigDefinition("namespace-in-filename", new String[]{"namespace=a.b", "doubleVal double default=1.0"}));
        ApplicationPackage app = FilesApplicationPackage.fromFile(new File("src/test/cfg//application/app1"));
        DeployState state = createDeployState(app, defs);

        assertNotNull(state.getConfigDefinition(new ConfigDefinitionKey("foo", "bar")));
        ConfigDefinition overridden = state.getConfigDefinition(new ConfigDefinitionKey("namespace-in-filename", "a.b")).get();
        assertNotNull(overridden);
        Double defaultValue = overridden.getDoubleDefs().get("doubleVal").getDefVal();
        assertNotNull(defaultValue);
        assertEquals(0, defaultValue.intValue());
    }

    @Test
    void testGetConfigDefinition() {
        Map<ConfigDefinitionKey, com.yahoo.vespa.config.buildergen.ConfigDefinition> defs = new LinkedHashMap<>();
        defs.put(new ConfigDefinitionKey("test2", "a.b"), new com.yahoo.vespa.config.buildergen.ConfigDefinition("test2", new String[]{"namespace=a.b", "doubleVal double default=1.0"}));
        //defs.put(new ConfigDefinitionKey("test2", "c.d"), new com.yahoo.vespa.config.buildergen.ConfigDefinition("test2", new String[]{"namespace=c.d", "doubleVal double default=1.0"}));
        defs.put(new ConfigDefinitionKey("test3", "xyzzy"), new com.yahoo.vespa.config.buildergen.ConfigDefinition("test3", new String[]{"namespace=xyzzy", "message string"}));
        ApplicationPackage app = FilesApplicationPackage.fromFile(new File("src/test/cfg//application/app1"));
        DeployState state = createDeployState(app, defs);

        assertNotNull(state.getConfigDefinition(new ConfigDefinitionKey("test2", "a.b")));

        ConfigDefinition test1 = state.getConfigDefinition(new ConfigDefinitionKey("test2", "a.b")).get();
        assertNotNull(test1);
        assertEquals("test2", test1.getName());
        assertEquals("a.b", test1.getNamespace());
    }

    @Test
    void testContainerEndpoints() {
        assertTrue(new DeployState.Builder().endpoints(Set.of()).build().getEndpoints().isEmpty());
        var endpoints = Set.of(new ContainerEndpoint("c1", ApplicationClusterEndpoint.Scope.global, List.of("c1.example.com", "c1-alias.example.com")));
        assertEquals(endpoints, new DeployState.Builder().endpoints(endpoints).build().getEndpoints());
    }

    private DeployState createDeployState(ApplicationPackage app, Map<ConfigDefinitionKey, com.yahoo.vespa.config.buildergen.ConfigDefinition> defs) {
        DeployState.Builder builder = new DeployState.Builder().applicationPackage(app);
        builder.configDefinitionRepo(new ConfigDefinitionRepo() {
            @Override
            public Map<ConfigDefinitionKey, com.yahoo.vespa.config.buildergen.ConfigDefinition> getConfigDefinitions() {
                return defs;
            }
            @Override
            public com.yahoo.vespa.config.buildergen.ConfigDefinition get(ConfigDefinitionKey key) {
                return null;
            }
        });
        return builder.build();
    }

}

