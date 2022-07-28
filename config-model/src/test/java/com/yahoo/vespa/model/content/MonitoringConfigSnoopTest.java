// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.test.TestDriver;
import com.yahoo.config.model.test.TestRoot;
import com.yahoo.metrics.MetricsmanagerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * @author havardpe
 **/
public class MonitoringConfigSnoopTest {

    private TestRoot root;

    public void initRoot(int interval) {
        TestDriver tester = new TestDriver();
        root = tester.buildModel(getAdminXml(interval) + getContent());
    }

    private String getAdminXml(int interval) {
        return ""
                + "<admin version='2.0'>"
                + "  <adminserver hostalias='mockhost' />"
                + "  <monitoring interval='" + interval + "' systemname='test' />"
                + "</admin>";
    }

    private String getContent() {
        return (
                "<content version='1.0' id='search'>"+
                "    <documents/>"+
                "    <nodes>"+
                "      <node hostalias='mockhost' distribution-key='0' />"+
                "    </nodes>"+
                "</content>");
    }

    private MetricsmanagerConfig getConfig() {
        return root.getConfig(MetricsmanagerConfig.class, "search/storage/0");
    }

    @Test
    void correct_config_is_snooped() {
        initRoot(60);
        assertEquals(2, getConfig().snapshot().periods().size());
        assertEquals(60, getConfig().snapshot().periods(0));
        assertEquals(300, getConfig().snapshot().periods(1));
    }

    @Test
    void correct_config_is_snooped_default_interval() {
        String getAdminXmlIntervalNotSpecified = "<admin version='2.0'>"
                + "  <adminserver hostalias='mockhost' />"
                + "</admin>";

        TestDriver tester = new TestDriver();
        root = tester.buildModel(getAdminXmlIntervalNotSpecified + getContent());
        assertEquals(2, getConfig().snapshot().periods().size());
        assertEquals(60, getConfig().snapshot().periods(0));
        assertEquals(300, getConfig().snapshot().periods(1));
    }

    @Test
    void invalid_model_1() {
        assertThrows(Exception.class, () -> {
            initRoot(120);
        });
    }
}
