// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.generic;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.generic.service.ServiceCluster;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class GenericServicesModelTest {

    @Test
    public void test_generic_services_builder() {
        GenericServicesBuilder builder = new GenericServicesBuilder();
        assertThat(builder.handlesElements().size(), is(1));
        assertThat(builder.handlesElements().get(0), is(ConfigModelId.fromName("service")));
    }

    @Test
    public void test_generic_services_model() {
        MockRoot root = new MockRoot();
        GenericServicesModel model = new GenericServicesModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "foo"));
        assertThat(model.serviceClusters().size(), is(0));
        model.addCluster(new ServiceCluster(root, "mycluster", "/bin/foo"));
        assertThat(model.serviceClusters().size(), is(1));
        assertThat(model.serviceClusters().get(0).getName(), is("mycluster"));
    }

    @Test
    public void test_generic_services_parsing() throws IOException, SAXException {
        final String hosts =
                "<hosts>" +
                        "<host name=\"localhost\">" +
                        "    <alias>mockhost</alias>" +
                        "  </host> " +
                        "</hosts>";
        String services = "<services version=\"1.0\">"
                   + "<service id=\"me\" name=\"foo\" command=\"/bin/bar\" version=\"1.0\">"
                   + "<node hostalias=\"mockhost\" />"
                   + "</service>"
                   + "</services>";
        VespaModel model = new VespaModel(new MockApplicationPackage.Builder().withHosts(hosts).withServices(services).build());
        GenericServicesModel gsModel = (GenericServicesModel) model.configModelRepo().get("me");
        assertThat(gsModel.serviceClusters().size(), is(1));
        assertThat(gsModel.serviceClusters().get(0).getName(), is("foo"));
        assertThat(gsModel.serviceClusters().get(0).services().size(), is(1));
    }

}
