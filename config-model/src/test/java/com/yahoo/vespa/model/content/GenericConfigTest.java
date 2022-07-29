// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @BeforeEach
    public void getVespaModel() {
        model = (new VespaModelCreatorWithMockPkg(ContentBaseTest.getHosts(), servicesXml(), ApplicationPackageUtils.generateSchemas("type1"))).create();
    }

    @Test
    void config_override_on_root_is_visible_on_storage_cluster() {
        StorageCluster cluster = model.getContentClusters().get("storage").getStorageCluster();

        StorFilestorConfig config = model.getConfig(StorFilestorConfig.class, cluster.getConfigId());
        assertEquals(7, config.num_threads());
    }

    @Test
    void config_override_on_root_is_visible_on_content_cluster() {
        ContentCluster cluster = model.getContentClusters().get("storage");

        StorFilestorConfig config = model.getConfig(StorFilestorConfig.class, cluster.getConfigId());
        assertEquals(7, config.num_threads());
    }

}
