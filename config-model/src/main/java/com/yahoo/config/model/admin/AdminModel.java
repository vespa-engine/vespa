// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.admin;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.builder.xml.dom.DomAdminV2Builder;
import com.yahoo.vespa.model.builder.xml.dom.DomAdminV4Builder;
import com.yahoo.vespa.model.container.ContainerModel;
import org.w3c.dom.Element;

import java.util.*;

/**
 * Config model adaptor of the Admin class.
 *
 * @author Ulf Lilleengen
 */
public class AdminModel extends ConfigModel {

    private Admin admin = null;
    private final Collection<ContainerModel> containerModels;

    /**
     * Constructs a new config model given a context.
     *
     * @param modelContext the model context.
     */
    public AdminModel(ConfigModelContext modelContext, Collection<ContainerModel> containerModels) {
        super(modelContext);
        this.containerModels = containerModels;
    }

    public Admin getAdmin() { return admin; }

    private Collection<ContainerModel> getContainerModels() { return containerModels; }

    @Override
    public void prepare(ConfigModelRepo configModelRepo) {
        verifyClusterControllersOnlyDefinedForContent(configModelRepo);
        if (admin == null) return;
        if (admin.getClusterControllers() != null)
            admin.getClusterControllers().prepare();
    }

    private void verifyClusterControllersOnlyDefinedForContent(ConfigModelRepo configModelRepo) {
        Admin admin = getAdmin();
        if (admin == null || admin.getClusterControllers() == null) return;
        if (configModelRepo.getContent() == null) {
            throw new IllegalArgumentException("Declaring <clustercontrollers> in <admin> in services.xml will not work when <content> is not defined");
        }
    }

    public static class BuilderV2 extends ConfigModelBuilder<AdminModel> {

        public static final List<ConfigModelId> configModelIds =
                ImmutableList.of(ConfigModelId.fromNameAndVersion("admin", "2.0"),
                                 ConfigModelId.fromNameAndVersion("admin", "1.0"));

        public BuilderV2() {
            super(AdminModel.class);
        }

        @Override
        public List<ConfigModelId> handlesElements() { return configModelIds; }

        @Override
        public void doBuild(AdminModel model, Element adminElement, ConfigModelContext modelContext) {
            AbstractConfigProducer parent = modelContext.getParentProducer();
            DeployProperties properties = modelContext.getDeployState().getProperties();
            DomAdminV2Builder domBuilder = new DomAdminV2Builder(modelContext.getApplicationType(),
                                                                 modelContext.getDeployState().getFileRegistry(),
                                                                 properties.multitenant(),
                                                                 properties.configServerSpecs(),
                                                                 modelContext.getDeployState().disableFiledistributor());
            model.admin = domBuilder.build(parent, adminElement);
            // TODO: Is required since other models depend on admin.
            if (parent instanceof ApplicationConfigProducerRoot) {
                ((ApplicationConfigProducerRoot)parent).setupAdmin(model.admin);
            }
        }
    }

    public static class BuilderV4 extends ConfigModelBuilder<AdminModel> {

        public static final List<ConfigModelId> configModelIds =
                ImmutableList.of(ConfigModelId.fromNameAndVersion("admin", "3.0"),
                                 ConfigModelId.fromNameAndVersion("admin", "4.0"));

        public BuilderV4() {
            super(AdminModel.class);
        }

        @Override
        public List<ConfigModelId> handlesElements() { return configModelIds; }

        @Override
        public void doBuild(AdminModel model, Element adminElement, ConfigModelContext modelContext) {
            AbstractConfigProducer parent = modelContext.getParentProducer();
            DeployProperties properties = modelContext.getDeployState().getProperties();
            DomAdminV4Builder domBuilder = new DomAdminV4Builder(modelContext,
                                                                 properties.multitenant(),
                                                                 properties.configServerSpecs(),
                                                                 model.getContainerModels(),
                                                                 modelContext.getDeployState().disableFiledistributor());
            model.admin = domBuilder.build(parent, adminElement);
            // TODO: Is required since other models depend on admin.
            if (parent instanceof ApplicationConfigProducerRoot) {
                ((ApplicationConfigProducerRoot)parent).setupAdmin(model.admin);
            }
        }
    }

}
