// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.deploy;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.codegen.InnerCNode;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Rotation;
import com.yahoo.vespa.config.ConfigDefinition;
import com.yahoo.vespa.config.ConfigDefinitionKey;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Ulf Lilleengen
 */
public class DeployStateTest {

    @Test
    public void testProvisionerIsSet() {
        DeployState.Builder builder = new DeployState.Builder();
        HostProvisioner provisioner = new InMemoryProvisioner(true, "foo.yahoo.com");
        builder.modelHostProvisioner(provisioner);
        DeployState state = builder.build(true);
        assertThat(state.getProvisioner(), is(provisioner));
    }

    @Test
    public void testBuilder() {
        DeployState.Builder builder = new DeployState.Builder();
        ApplicationPackage app = MockApplicationPackage.createEmpty();
        builder.permanentApplicationPackage(Optional.of(app));
        DeployState state = builder.build(true);
        assertThat(state.getPermanentApplicationPackage().get(), is(app));
    }

    @Test
    public void testPreviousModelIsProvided() throws IOException, SAXException {
        VespaModel prevModel = new VespaModel(MockApplicationPackage.createEmpty());
        DeployState.Builder builder = new DeployState.Builder();
        assertThat(builder.previousModel(prevModel).build(true).getPreviousModel().get(), is(prevModel));
    }

    @Test
    public void testProperties() {
        DeployState.Builder builder = new DeployState.Builder();
        DeployState state = builder.build(true);
        assertThat(state.getProperties().applicationId(), is(ApplicationId.defaultId()));
        ApplicationId customId = new ApplicationId.Builder()
                                 .tenant("bar")
                                 .applicationName("foo").instanceName("quux").build();
        DeployProperties properties = new DeployProperties.Builder().applicationId(customId).build();
        builder.properties(properties);
        state = builder.build(true);
        assertThat(state.getProperties().applicationId(), is(customId));
    }

    @Test
    public void testDefinitionRepoIsUsed() {
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
        assertThat(defaultValue.intValue(), is(0));
    }

    @Test
    public void testGetConfigDefinition() {
        Map<ConfigDefinitionKey, com.yahoo.vespa.config.buildergen.ConfigDefinition> defs = new LinkedHashMap<>();
        defs.put(new ConfigDefinitionKey("test2", "a.b"), new com.yahoo.vespa.config.buildergen.ConfigDefinition("test2", new String[]{"namespace=a.b", "doubleVal double default=1.0"}));
        //defs.put(new ConfigDefinitionKey("test2", "c.d"), new com.yahoo.vespa.config.buildergen.ConfigDefinition("test2", new String[]{"namespace=c.d", "doubleVal double default=1.0"}));
        defs.put(new ConfigDefinitionKey("test3", "xyzzy"), new com.yahoo.vespa.config.buildergen.ConfigDefinition("test3", new String[]{"namespace=xyzzy", "message string"}));
        ApplicationPackage app = FilesApplicationPackage.fromFile(new File("src/test/cfg//application/app1"));
        DeployState state = createDeployState(app, defs);

        assertNotNull(state.getConfigDefinition(new ConfigDefinitionKey("test2", "a.b")));

        ConfigDefinition test1 = state.getConfigDefinition(new ConfigDefinitionKey("test2", "a.b")).get();
        assertNotNull(test1);
        assertThat(test1.getName(), is("test2"));
        assertThat(test1.getNamespace(), is("a.b"));
    }

    @Test
    public void testRotations() {
        Set<Rotation> rotations = new HashSet<>();
        assertThat(new DeployState.Builder().rotations(rotations).build(true).getRotations().size(), is(0));
        for (String name : new String[]{"rotation-001.vespa.a02.yahoodns.net", "rotation-002.vespa.a02.yahoodns.net"}) {
            rotations.add(new Rotation(name));
        }
        assertThat(new DeployState.Builder().rotations(rotations).build(true).getRotations(), equalTo(rotations));
    }

    private DeployState createDeployState(ApplicationPackage app, Map<ConfigDefinitionKey, com.yahoo.vespa.config.buildergen.ConfigDefinition> defs) {
        DeployState.Builder builder = new DeployState.Builder().applicationPackage(app);
        builder.configDefinitionRepo(new ConfigDefinitionRepo() {
            @Override
            public Map<ConfigDefinitionKey, com.yahoo.vespa.config.buildergen.ConfigDefinition> getConfigDefinitions() {
                return defs;
            }
        });
        return builder.build(true);
    }

}

