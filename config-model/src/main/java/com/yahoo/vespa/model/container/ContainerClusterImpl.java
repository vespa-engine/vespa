// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.component.ComponentId;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.BundlesConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ConfigProducerGroup;
import com.yahoo.vespa.model.container.component.Servlet;
import com.yahoo.vespa.model.container.jersey.Jersey2Servlet;
import com.yahoo.vespa.model.container.jersey.RestApi;
import com.yahoo.vespa.model.utils.FileSender;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The default container cluster implementation.
 *
 * @author gjoranv
 */
public final class ContainerClusterImpl extends ContainerCluster<ContainerImpl> implements
        BundlesConfig.Producer,
        RankProfilesConfig.Producer,
        RankingConstantsConfig.Producer,
        ServletPathsConfig.Producer
{

    private final Set<FileReference> applicationBundles = new LinkedHashSet<>();

    private final ConfigProducerGroup<Servlet> servletGroup;
    private final ConfigProducerGroup<RestApi> restApiGroup;

    private ContainerModelEvaluation modelEvaluation;

    public ContainerClusterImpl(AbstractConfigProducer<?> parent, String subId, String name, DeployState deployState) {
        super(parent, subId, name, deployState);
        restApiGroup = new ConfigProducerGroup<>(this, "rest-api");
        servletGroup = new ConfigProducerGroup<>(this, "servlet");
    }

    public ContainerClusterImpl(AbstractConfigProducer<?> parent, String subId, String name, ContainerClusterVerifier verifier, DeployState deployState) {
        super(parent, subId, name, verifier, deployState);
        restApiGroup = new ConfigProducerGroup<>(this, "rest-api");
        servletGroup = new ConfigProducerGroup<>(this, "servlet");
    }


    protected void myPrepare(DeployState deployState) {
        addAndSendApplicationBundles(deployState);
        if (modelEvaluation != null)
            modelEvaluation.prepare(containers);
        sendUserConfiguredFiles(deployState);
        for (RestApi restApi : restApiGroup.getComponents())
            restApi.prepare();
    }

    private void addAndSendApplicationBundles(DeployState deployState) {
        for (ComponentInfo component : deployState.getApplicationPackage().getComponentsInfo(deployState.getVespaVersion())) {
            FileReference reference = FileSender.sendFileToServices(component.getPathRelativeToAppDir(), containers);
            applicationBundles.add(reference);
        }
    }

    private void sendUserConfiguredFiles(DeployState deployState) {
        // Files referenced from user configs to all components.
        for (Component<?, ?> component : getAllComponents()) {
            FileSender.sendUserConfiguredFiles(component, containers, deployState.getDeployLogger());
        }
    }

    public void setModelEvaluation(ContainerModelEvaluation modelEvaluation) {
        this.modelEvaluation = modelEvaluation;
    }

    public final void addRestApi(@NonNull RestApi restApi) {
        restApiGroup.addComponent(ComponentId.fromString(restApi.getBindingPath()), restApi);
    }

    public Map<ComponentId, RestApi> getRestApiMap() {
        return restApiGroup.getComponentMap();
    }


    public Map<ComponentId, Servlet> getServletMap() {
        return servletGroup.getComponentMap();
    }

    public final void addServlet(@NonNull Servlet servlet) {
        servletGroup.addComponent(servlet.getGlobalComponentId(), servlet);
    }

    // Returns all servlets, including rest-api/jersey servlets.
    public Collection<Servlet> getAllServlets() {
        return allServlets().collect(Collectors.toCollection(ArrayList::new));
    }

    private Stream<Servlet> allServlets() {
        return Stream.concat(allJersey2Servlets(),
                             servletGroup.getComponents().stream());
    }

    private Stream<Jersey2Servlet> allJersey2Servlets() {
        return restApiGroup.getComponents().stream().map(RestApi::getJersey2Servlet);
    }

    @Override
    public void getConfig(BundlesConfig.Builder builder) {
        applicationBundles.stream().map(FileReference::value)
                .forEach(builder::bundle);
        super.getConfig(builder);
    }

    @Override
    public void getConfig(ServletPathsConfig.Builder builder) {
        allServlets().forEach(servlet ->
                                      builder.servlets(servlet.getComponentId().stringValue(),
                                                       servlet.toConfigBuilder())
        );
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        if (modelEvaluation != null) modelEvaluation.getConfig(builder);
    }

    @Override
    public void getConfig(RankingConstantsConfig.Builder builder) {
        if (modelEvaluation != null) modelEvaluation.getConfig(builder);
    }

}
