package com.yahoo.vespa.hosted.node.admin.containerdata;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.node.admin.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.task.util.file.Template;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;

/**
 * @author jvenstad
 */
public class MotdContainerData {

    private static final Path motdPath = Paths.get("etc/profile.d/motd.sh");
    private static final String templateString = "#!/usr/bin/env bash\n" +
                                                 "\n" +
                                                 "function motd {\n" +
                                                 "\n" +
                                                 "    local -r uptime=$(uptime | cut -f 3- -d ' ')\n" +
                                                 "\n" +
                                                 "    local -r no_color='\\e[0m'\n" +
                                                 "    local -r green='\\e[0;32m'\n" +
                                                 "## Use red zone name for main prod zones, yellow for other main zones and no colour for cd and dev zones.\n" +
                                                 "    local -r alert=#if($zone.getSystem() == \"main\")#if($zone.getEnvironment() == \"prod\")'\\e[0;91m'#else'\\e[0;33m'#end#else$green#end\n" +
                                                 "\n" +
                                                 "\n" +
                                                 "    echo -e \"\n" +
                                                 "${green}Zone          : ${alert}$zone.getSystem().toUpperCase() $zone.getEnvironment().toUpperCase() $zone.getRegion().toUpperCase()\n" +
                                                 "${green}Node type     : ${no_color}$type\n" +
                                                 "${green}Node flavor   : ${no_color}$flavor\n" +
                                                 "${green}Host name     : ${no_color}$(hostname)\n" +
                                                 "${green}Uptime        : ${no_color}$uptime\n" +
                                                 "${green}Version       : ${no_color}wanted = $wanted.orElse(\"unknown\"); installed = $installed.orElse(\"unknown\")\n" +
                                                 "#if($owner.isPresent())\n" +
                                                 "${green}Node state    : ${no_color}$state\n" +
                                                 "${green}Owner         : ${no_color}$owner.get().serializedForm()\n" +
                                                 "#end\n" +
                                                 "\"\n" +
                                                 "}\n" +
                                                 "\n" +
                                                 "# Display motd (gently)\n" +
                                                 "[ ! -f ~/.hushlogin ] && motd\n";

    private final String renderedString;

    public MotdContainerData(NodeSpec node, Environment environment) {
        renderedString = Template.of(templateString)
                .set("zone", environment)
                .set("type", node.nodeType)
                .set("state", node.nodeState)
                .set("installed", node.vespaVersion)
                .set("wanted", node.wantedVespaVersion)
                .set("owner", node.owner.map(id -> ApplicationId.from(id.tenant, id.application, id.instance)))
                .set("flavor", node.nodeFlavor)
                .render();
    }

    public void writeTo(ContainerData containerData) {
        writeTo(containerData::addFile);
    }

    void writeTo(BiConsumer<Path, String> fileWriter) {
        fileWriter.accept(motdPath, renderedString);
    }

}
