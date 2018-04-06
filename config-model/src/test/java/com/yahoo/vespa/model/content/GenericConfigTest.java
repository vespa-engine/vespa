// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 * @since 5.1.8
 */
public class GenericConfigTest {

    private VespaModel model;

    private String servicesXml() {
        return "" +
                "<services version='1.0'>" +
                "  <config name='vespa.config.content.stor-filestor'>" +
                "    <num_threads>7</num_threads> " +
                "  </config>" +
                "  <admin  version=\"2.0\">" +
                "    <adminserver hostalias=\"node0\" />" +
                "    <cluster-controllers>" +
                "      <cluster-controller hostalias='node0'/>" +
                "    </cluster-controllers>" +
                "  </admin>" +
                "  <content version='1.0' id='storage'>" +
                "      <documents>" +
                "       <document type=\"type1\" mode=\"store-only\"/>\n" +
                "      </documents>" +
                "      <config name='config.juniperrc'>" +
                "        <length>1024</length>" +
                "      </config>" +
                "      <redundancy>1</redundancy>" +
                "      <group>" +
                "        <node distribution-key='0' hostalias='node0'/>" +
                "      </group>" +
                "  </content>" +
                "</services>";
    }

    @Before
    public void getVespaModel() throws IOException, SAXException, ParseException {
        model = (new VespaModelCreatorWithMockPkg(ContentBaseTest.getHosts(), servicesXml(), ApplicationPackageUtils.generateSearchDefinitions("type1"))).create();
    }

    @Test
    public void config_override_on_root_is_visible_on_storage_cluster() throws Exception {
        StorageCluster cluster = model.getContentClusters().get("storage").getStorageNodes();

        StorFilestorConfig config = model.getConfig(StorFilestorConfig.class, cluster.getConfigId());
        assertThat(config.num_threads(), is(7));
    }

    @Test
    public void config_override_on_root_is_visible_on_content_cluster() throws Exception {
        ContentCluster cluster = model.getContentClusters().get("storage");

        StorFilestorConfig config = model.getConfig(StorFilestorConfig.class, cluster.getConfigId());
        assertThat(config.num_threads(), is(7));
    }

}
