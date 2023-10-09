// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.vespa.model.builder.xml.dom.LegacyConfigModelBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This is a plugin for testing the plugin API exchange mechanism in
 * the vespamodel. It uses the API of another plugin.
 *
 * @author  gjoranv
 */
public class ApiConfigModel extends ConfigModel {

    private List<ApiService> apiServices = new ArrayList<>();

    public ApiConfigModel(ConfigModelContext modelContext) {
        super(modelContext);
    }

    // Inherit doc from ConfigModel.
    public void prepare(ConfigModelRepo configModelRepo) {
        int numSimpleServices = 0;
        ConfigModel simplePlugin = configModelRepo.get("simple");

        if ((simplePlugin != null) && (simplePlugin instanceof TestApi)) {
            TestApi testApi = (TestApi) simplePlugin;
            numSimpleServices = testApi.getNumSimpleServices();
        }
        for (Object apiService : apiServices) {
            ApiService as = (ApiService) apiService;
            as.setNumSimpleServices(numSimpleServices);
        }
    }

    public static class Builder extends LegacyConfigModelBuilder<ApiConfigModel> {

        public Builder() {
            super(ApiConfigModel.class);
        }

        @Override
        public List<ConfigModelId> handlesElements() {
            return Arrays.asList(ConfigModelId.fromName("api"));
        }

        @Override
        public void doBuild(ApiConfigModel configModel, Element spec, ConfigModelContext context) {
            NodeList pl = spec.getElementsByTagName("apiservice");
            if (pl.getLength() > 0) {
                for (int i=0; i < pl.getLength(); i++) {
                    configModel.apiServices.add(new DomTestServiceBuilder.ApiServiceBuilder(i).build(context.getDeployState(), context.getParentProducer(), (Element) pl.item(i)));
                }
            }
        }

    }

}
