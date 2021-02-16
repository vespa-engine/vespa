// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.utils;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.provision.SingleNodeProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.monitoring.DefaultMonitoring;
import com.yahoo.vespa.model.admin.monitoring.builder.Metrics;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import org.w3c.dom.Document;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * For testing purposes only.
 * 
 * @author geirst
 */
public class ContentClusterUtils {

    public static MockRoot createMockRoot(String[] hosts) {
        return createMockRoot(hosts, SchemaBuilder.createSchemas("test"));
    }

    private static MockRoot createMockRoot(HostProvisioner provisioner, List<String> schemas) {
        return createMockRoot(provisioner, schemas, new DeployState.Builder());
    }

    private static MockRoot createMockRoot(HostProvisioner provisioner, List<String> schemas, DeployState.Builder deployStateBuilder) {
        ApplicationPackage applicationPackage = new MockApplicationPackage.Builder().withSchemas(schemas).build();
        DeployState deployState = deployStateBuilder.applicationPackage(applicationPackage)
                          .modelHostProvisioner(provisioner)
                          .build();
        return new MockRoot("", deployState);
    }

    public static MockRoot createMockRoot(String[] hosts, List<String> schemas) {
        return createMockRoot(new InMemoryProvisioner(true, false, hosts), schemas);
    }

    public static MockRoot createMockRoot(List<String> schemas) {
        return createMockRoot(new SingleNodeProvisioner(), schemas);
    }

    public static MockRoot createMockRoot(List<String> schemas, DeployState.Builder deployStateBuilder) {
        return createMockRoot(new SingleNodeProvisioner(), schemas, deployStateBuilder);
    }

    public static ContentCluster createCluster(String clusterXml, MockRoot root) {
        Document doc = XML.getDocument(clusterXml);
        Admin admin = new Admin(root, new DefaultMonitoring("vespa", 60), new Metrics(), false,
                                new FileDistributionConfigProducer(root, new MockFileRegistry(), List.of(), false),
                                root.getDeployState().isHosted());
        ConfigModelContext context = ConfigModelContext.create(null, root.getDeployState(),
                                                               null,null, root, null);

        return new ContentCluster.Builder(admin).build(Collections.emptyList(), context, doc.getDocumentElement());
    }

    public static ContentCluster createCluster(String clusterXml, List<String> schemas, DeployState.Builder deployStateBuilder) throws Exception {
        MockRoot root = createMockRoot(schemas, deployStateBuilder);
        ContentCluster cluster = createCluster(clusterXml, root);
        root.freezeModelTopology();
        cluster.validate();
        return cluster;
    }

    public static ContentCluster createCluster(String clusterXml, List<String> schemas) throws Exception {
        return createCluster(clusterXml, schemas, new DeployState.Builder());
    }

    public static ContentCluster createCluster(String clusterXml) throws Exception {
        return createCluster(clusterXml, SchemaBuilder.createSchemas("test"), new DeployState.Builder());
    }

    public static ContentCluster createCluster(String clusterXml, DeployState.Builder deployStateBuilder) throws Exception {
        return createCluster(clusterXml, SchemaBuilder.createSchemas("test"), deployStateBuilder);
    }

    public static String createClusterXml(String groupXml, int redundancy, int searchableCopies) {
        return createClusterXml(groupXml, Optional.empty(), redundancy, searchableCopies);
    }

    public static String createClusterXml(String groupXml, Optional<String> dispatchXml, int redundancy, int searchableCopies) {
        return new ContentClusterBuilder().
                groupXml(groupXml).
                dispatchXml(dispatchXml).
                redundancy(redundancy).
                searchableCopies(searchableCopies).getXml();
    }

}
