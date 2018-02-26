// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.google.common.annotations.Beta;
import com.yahoo.component.Version;
import com.yahoo.config.model.MapConfigModelRegistry;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.SchemaValidators;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.vespa.model.VespaModel;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test driver for testing config models. Add custom builders for plugins to be tested. Builds a model from the
 * xml string and returns a config producer that can be use to test getConfig.
 *
 * @author lulf
 * @since 5.1.20
 */
@Beta
public class TestDriver {

    private final List<ConfigModelBuilder> builders = new ArrayList<>();
    private final boolean validate;

    public TestDriver(boolean validate) {
        this.validate = validate;
    }

    public TestDriver() {
        this(false);
    }

    /**
     * Add a new builder to the tester.
     *
     * @param builder builder to add.
     * @return this for chaining
     */
    public TestDriver addBuilder(ConfigModelBuilder builder) {
        builders.add(builder);
        return this;
    }

    /**
     * Build a model from an XML string. The hosts referenced in services must be set to 'mockhost' when using
     * this method, as it automatically generates a hosts file for you.
     *
     * @param servicesXml The xml for services.xml
     * @return a producer root capable of answering getConfig requests.
     */
    public TestRoot buildModel(String servicesXml) {
        return buildModel(servicesXml, "<hosts><host name='localhost'><alias>mockhost</alias></host></hosts>");
    }

    /**
     * Build a model from an XML string of services and one of hosts.
     *
     * @param servicesXml The xml for services.xml
     * @param hostsXml The xml for hosts.xml
     * @return a producer root capable of answering getConfig requests.
     */
    public TestRoot buildModel(String servicesXml, String hostsXml) {
        if (!servicesXml.contains("<services")) {
            servicesXml = "<services version='1.0'>" + servicesXml + "</services>";
        }
        return buildModel(new MockApplicationPackage.Builder().withHosts(hostsXml).withServices(servicesXml).build());
    }

    /**
     * Build a model from an application package.
     *
     * @param applicationPackage Any type of application package.
     * @return a producer root capable of answering getConfig requests.
     */
    public TestRoot buildModel(ApplicationPackage applicationPackage) {
        return buildModel(new DeployState.Builder().applicationPackage(applicationPackage).build(true));
    }

    /**
     * Build a model given a deploy state.
     *
     * @param deployState An instance of {@link com.yahoo.config.model.deploy.DeployState}
     * @return a producer root capable of answering getConfig requests.
     */
    public TestRoot buildModel(DeployState deployState) {
        MapConfigModelRegistry registry = new MapConfigModelRegistry(builders);
        try {
            validate(deployState.getApplicationPackage());
            return new TestRoot(new VespaModel(registry, deployState));
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private void validate(ApplicationPackage appPkg) throws IOException {
        if (!validate) {
            return;
        }
        SchemaValidators schemaValidators = new SchemaValidators(new Version(6), new BaseDeployLogger());
        if (appPkg.getHosts() != null) {
            schemaValidators.hostsXmlValidator().validate(appPkg.getHosts());
        }
        schemaValidators.servicesXmlValidator().validate(appPkg.getServices());
    }
}
