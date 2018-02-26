// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.standalone;

import com.yahoo.vdslib.distribution.Distribution;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

public abstract class ClusterControllerTest {

    private final Map<String, String> overriddenProperties = new TreeMap<>();
    private File tempDirectory;

    protected void setProperty(String p, String val) {
        if (!overriddenProperties.containsKey(p)) {
            overriddenProperties.put(p, System.getProperty(p));
        }
        System.setProperty(p, val);
    }

    protected File createTemporaryDirectory() throws IOException {
        File f = File.createTempFile("clustercontroller", "configtest");
        if (f.exists()) f.delete();
        f.mkdirs();
        return f;
    }

    @Before
    public void setUp() throws Exception {
        tempDirectory = createTemporaryDirectory();
    }

    @After
    public void tearDown() {
        for (Map.Entry<String, String> e : overriddenProperties.entrySet()) {
            if (e.getValue() == null) {
                System.clearProperty(e.getKey());
            } else {
                System.setProperty(e.getKey(), e.getValue());
            }
        }
        overriddenProperties.clear();
        if (tempDirectory != null) {
            for (File f : tempDirectory.listFiles()) {
                f.delete();
            }
            tempDirectory.delete();
            tempDirectory = null;
        }
    }

    protected void writeConfig(String config, String value) throws IOException {
        File f = new File(tempDirectory, config + ".cfg");
        FileWriter fw = new FileWriter(f);
        fw.write(value);
        fw.close();
    }

    protected void setFleetControllerConfigProperty() {
        setProperty("config.id", "dir:" + tempDirectory.toString());
    }

    protected void addFleetControllerConfig(int stateGatherCount, int fleetcontrollers) throws IOException {
        writeConfig("fleetcontroller", "cluster_name \"storage\"\n" +
                "index 0\n" +
                "state_gather_count " + stateGatherCount + "\n" +
                "fleet_controller_count " + fleetcontrollers + "\n" +
                "zookeeper_server \"\"");
    }
    protected void setSlobrokConfigProperty() {
        setProperty("slobrok.config.id", "dir:" + tempDirectory.toString());
    }
    protected void addSlobrokConfig() throws IOException {
        writeConfig("slobroks", "cluster_name \"storage\"\n" +
                "index 0\n" +
                "zookeeper_server \"\"");
    }
    protected void addDistributionConfig() throws IOException {
        writeConfig("stor-distribution", Distribution.getDefaultDistributionConfig(2, 10));
    }
    protected void addZookeepersConfig() throws IOException {
        writeConfig("zookeepers", "zookeeperserverlist \"\"");
    }

    protected void setupConfig() throws Exception {
        setFleetControllerConfigProperty();
        setSlobrokConfigProperty();
        addFleetControllerConfig(2, 1);
        addSlobrokConfig();
        addDistributionConfig();
        addZookeepersConfig();
    }

}
