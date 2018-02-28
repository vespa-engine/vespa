package com.yahoo.vespa.hosted.node.admin.containerdata;

import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.Environment;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigServerContainerData {

    public static final Path configServerAppDir = Paths.get("home/y/conf/configserver-app/");

    private final Environment environment;
    private final String configServerNodeHostName;

    public ConfigServerContainerData(Environment environment, String configServerNodeHostName) {
        this.environment = environment;
        this.configServerNodeHostName = configServerNodeHostName;
    }

    public void create() {
        ContainerData containerData = new ContainerData(environment.pathInHostFromPathInNode(
                ContainerName.fromHostname(configServerNodeHostName),
                ContainerData.containerDataPath));
        containerData.addFile(getPath("configserver-config.xml"), createConfigServerConfigXml());
        containerData.addFile(getPath("node-repository-config.xml"), createNodeRepoConfigXml());
    }

    private Path getPath(String fileName) {
        return configServerAppDir.resolve(fileName);
    }

    private String createConfigServerConfigXml() {
        return "<config name=\"cloud.config.configserver\">\n" +
                "  <system>" + environment.getSystem() + "</system>\n" +
                "  <environment>" + environment.getEnvironment() + "</environment>\n" +
                "  <region>" + environment.getRegion() + "</region>\n" +
                "  <hostedVespa>true</hostedVespa>\n" +
                "  <defaultFlavor>t2.xlarge</defaultFlavor>\n" + // TODO: Avoid hardcoding
                createZookeeperServers() +
                "  <serverId>" + configServerNodeHostName + "</serverId>\n" +
                "  <nodeAdminInContainer>false</nodeAdminInContainer>\n" +
                "</config>\n";
    }

    private String createZookeeperServers() {
        StringBuilder zookeeperServers = new StringBuilder("  <zookeeperserver>\n");
        environment.getConfigServerHostNames().forEach(hostname -> zookeeperServers.append("  <item>\n" +
                                                                                                   "    <hostname>" + hostname + "</hostname>\n" +
                                                                                                   "  </item>\n"));
        zookeeperServers.append("  </zookeeperserver>\n");
        return zookeeperServers.toString();
    }

    // TODO: Avoid hardcoded Docker registry
    private String createNodeRepoConfigXml() {
        return "<config name=\"config.provisioning.node-repository\">\n" +
                "    <dockerImage>658543512185.dkr.ecr.us-east-2.amazonaws.com:4443/vespa/aws</dockerImage>\n" +
                "</config>\n";
    }

}
