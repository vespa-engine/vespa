// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.cloud.config.ApplicationIdConfig;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.cloud.config.ModelConfig.Hosts;
import com.yahoo.cloud.config.ModelConfig.Hosts.Services;
import com.yahoo.cloud.config.ModelConfig.Hosts.Services.Ports;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.component.Version;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.document.config.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentrouteselectorpolicyConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.messagebus.MessagebusConfig;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.vespa.config.content.DistributionConfig;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;
import com.yahoo.vespa.documentmodel.DocumentModel;
import com.yahoo.vespa.model.ConfigProducer;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.PortsMeta;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.vespa.model.routing.Routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * This is the parent of all ConfigProducers in the system resulting from configuring an application.
 *
 * @author gjoranv
 */
public class ApplicationConfigProducerRoot extends AbstractConfigProducer<AbstractConfigProducer<?>> implements CommonConfigsProducer {

    private final DocumentModel documentModel;
    private Routing routing = null;
    // The ConfigProducers contained in this Vespa. (configId->producer)
    Map<String, ConfigProducer> id2producer = new LinkedHashMap<>();
    private Admin admin = null;
    private HostSystem hostSystem = null;
    private final Version vespaVersion;
    private final ApplicationId applicationId;

    /**
     * Creates and initializes a new Vespa from the service config file
     * in the given application directory.
     *
     * @param parent the parent, usually VespaModel
     * @param name   the name, used as configId
     * @param documentModel DocumentModel to serve global document config from.
     */
    public ApplicationConfigProducerRoot(AbstractConfigProducer parent, String name, DocumentModel documentModel, Version vespaVersion, ApplicationId applicationId) {
        super(parent, name);
        this.documentModel = documentModel;
        this.vespaVersion = vespaVersion;
        this.applicationId = applicationId;
    }

    private boolean useV8GeoPositions = false;

    public void useFeatureFlags(ModelContext.FeatureFlags featureFlags) {
        this.useV8GeoPositions = featureFlags.useV8GeoPositions();
    }

    /**
     * @return an unmodifiable copy of the set of configIds in this VespaModel.
     */
    public Set<String> getConfigIds() {
        return Collections.unmodifiableSet(id2producer.keySet());
    }

    /**
     * Returns the ConfigProducer with the given id, or null if no such
     * configId exists.
     *
     * @param configId The configId, e.g. "search.0/tld.0"
     * @return ConfigProducer with the given configId
     */
    public ConfigProducer getConfigProducer(String configId) {
        return id2producer.get(configId);
    }

    /**
     * Returns the Service with the given id, or null if no such
     * configId exists or if it belongs to a non-Service ConfigProducer.
     *
     * @param configId The configId, e.g. "search.0/tld.0"
     * @return Service with the given configId
     */
    public Service getService(String configId) {
        ConfigProducer cp = getConfigProducer(configId);
        if (cp == null || !(cp instanceof Service)) {
            return null;
        }
        return (Service) cp;
    }

    /**
     * Adds the descendant (at any depth level), so it can be looked up
     * on configId in the Map.
     *
     * @param descendant The configProducer descendant to add
     */
    // TODO: Make protected if this moves to the same package as AbstractConfigProducer
    public void addDescendant(AbstractConfigProducer descendant) {
        id2producer.put(descendant.getConfigId(), descendant);
    }

    /**
     * Prepares the model for start. The {@link VespaModel} calls
     * this methods after it has loaded this and all plugins have been loaded and
     * their initialize() methods have been called.
     *
     * @param plugins All initialized plugins of the vespa model.
     */
    public void prepare(ConfigModelRepo plugins) {
        if (routing != null) {
            routing.deriveCommonSettings(plugins);
        }
    }

    public void setupAdmin(Admin admin) {
        this.admin = admin;
    }

    // TODO: Do this as another config model depending on the other models
    public void setupRouting(DeployState deployState, VespaModel vespaModel, ConfigModelRepo configModels) {
        if (admin != null) {
            Routing routing = configModels.getRouting();
            if (routing == null) {
                routing = new Routing(ConfigModelContext.create(deployState, vespaModel, configModels, this, "routing"));
                configModels.add(routing);
            }
            this.routing = routing;
        }
    }

    @Override
    public void getConfig(DocumentmanagerConfig.Builder builder) {
        new DocumentManager()
            .useV8GeoPositions(this.useV8GeoPositions)
            .produce(documentModel, builder);
    }

    @Override
    public void getConfig(DocumenttypesConfig.Builder builder) {
        new DocumentTypes()
            .useV8GeoPositions(this.useV8GeoPositions)
            .produce(documentModel, builder);
    }

    @Override
    public void getConfig(DocumentrouteselectorpolicyConfig.Builder builder) {
        if (routing != null) {
            routing.getConfig(builder);
        }
    }

    @Override
    public void getConfig(DocumentProtocolPoliciesConfig.Builder builder) {
        if (routing != null) {
            routing.getConfig(builder);
        }
    }

    @Override
    public void getConfig(MessagebusConfig.Builder builder) {
        if (routing != null) {
            routing.getConfig(builder);
        }
    }

    @Override
    public void getConfig(LogdConfig.Builder builder) {
        if (admin != null) {
            admin.getConfig(builder);
        }
    }

    @Override
    public void getConfig(SlobroksConfig.Builder builder) {
        if (admin != null) {
            admin.getConfig(builder);
        }
    }

    @Override
    public void getConfig(ZookeepersConfig.Builder builder) {
        if (admin != null) {
            admin.getConfig(builder);
        }
    }

    @Override
    public void getConfig(ClusterListConfig.Builder builder) {
        VespaModel model = (VespaModel) getRoot();
        for (ContentCluster cluster : model.getContentClusters().values()) {
            ClusterListConfig.Storage.Builder storage = new ClusterListConfig.Storage.Builder();
            storage.name(cluster.getName());
            storage.configid(cluster.getConfigId());
            builder.storage(storage);
        }
    }

    @Override
    public void getConfig(DistributionConfig.Builder builder) {
        for (ContentCluster cluster : ((VespaModel) getRoot()).getContentClusters().values()) {
            cluster.getConfig(builder);
        }
    }

    @Override
    public void getConfig(AllClustersBucketSpacesConfig.Builder builder) {
        VespaModel model = (VespaModel) getRoot();
        for (ContentCluster cluster : model.getContentClusters().values()) {
            builder.cluster(cluster.getName(), cluster.clusterBucketSpaceConfigBuilder());
        }
    }

    @Override
    public void getConfig(ModelConfig.Builder builder) {
        builder.vespaVersion(vespaVersion.toFullString());
        for (HostResource modelHost : hostSystem().getHosts()) {
            builder.hosts(new Hosts.Builder()
                    .name(modelHost.getHostname())
                    .services(getServices(modelHost))
            );
        }
    }

    private List<Services.Builder> getServices(HostResource modelHost) {
        List<Services.Builder> ret = new ArrayList<>();
        for (Service modelService : modelHost.getServices()) {
            ret.add(new Services.Builder()
                    .name(modelService.getServiceName())
                    .type(modelService.getServiceType())
                    .configid(modelService.getConfigId())
                    .clustertype(modelService.getServicePropertyString("clustertype", ""))
                    .clustername(modelService.getServicePropertyString("clustername", ""))
                    .index(Integer.parseInt(modelService.getServicePropertyString("index", "999999")))
                    .ports(getPorts(modelService))
            );
        }
        return ret;
    }

    private List<Ports.Builder> getPorts(Service modelService) {
        List<Ports.Builder> ret = new ArrayList<>();
        PortsMeta portsMeta = modelService.getPortsMeta();
        for (int i = 0; i < portsMeta.getNumPorts(); i++) {
            ret.add(new Ports.Builder()
                    .number(modelService.getRelativePort(i))
                    .tags(getPortTags(portsMeta, i))
            );
        }
        return ret;
    }

    public static String getPortTags(PortsMeta portsMeta, int portNumber) {
        StringBuilder sb = new StringBuilder();
        boolean firstTag = true;
        for (String s : portsMeta.getTagsAt(portNumber)) {
            if (!firstTag) {
                sb.append(" ");
            } else {
                firstTag = false;
            }
            sb.append(s);
        }
        return sb.toString();
    }

    public void setHostSystem(HostSystem hostSystem) {
        this.hostSystem = hostSystem;
    }

    @Override
    public HostSystem hostSystem() {
        return hostSystem;
    }

    public FileDistributionConfigProducer getFileDistributionConfigProducer() {
        if (admin == null) return null; // no admin if standalone
        return admin.getFileDistributionConfigProducer();
    }

    public Admin getAdmin() {
        return admin;
    }

    @Override
    public void getConfig(ApplicationIdConfig.Builder builder) {
        builder.tenant(applicationId.tenant().value());
        builder.application(applicationId.application().value());
        builder.instance(applicationId.instance().value());
    }

}
