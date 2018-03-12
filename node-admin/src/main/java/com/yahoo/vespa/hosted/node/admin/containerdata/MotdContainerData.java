package com.yahoo.vespa.hosted.node.admin.containerdata;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.task.util.file.Template;
import com.yahoo.vespa.hosted.provision.Node.State;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
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
                                                 "${green}Node type     : ${no_color}$node.type()\n" +
                                                 "${green}Host name     : ${no_color}$(hostname)\n" +
                                                 "${green}Uptime        : ${no_color}$uptime\n" +
                                                 "${green}Version       : ${no_color}wanted = $node.wanted().orElse(\"unknown\"); installed = $node.installed().orElse(\"unknown\")\n" +
                                                 "#if($node.owner().isPresent())\n" +
                                                 "${green}Node state    : ${no_color}$node.state()\n" +
                                                 "${green}Owner         : ${no_color}$node.owner().get().serializedForm()\n" +
                                                 "#end\n" +
                                                 "\"\n" +
                                                 "}\n" +
                                                 "\n" +
                                                 "# Display motd (gently)\n" +
                                                 "[ ! -f ~/.hushlogin ] && motd\n";

    private final String renderedString;

    public MotdContainerData(ContainerNodeSpec nodeSpec, Environment environment) {
        renderedString = Template.of(templateString)
                .set("zone", environment)
                .set("node", new Node(nodeSpec.nodeType,
                                      nodeSpec.nodeState,
                                      nodeSpec.vespaVersion,
                                      nodeSpec.wantedVespaVersion,
                                      nodeSpec.owner))
                .render();
    }

    public void writeTo(ContainerData containerData) {
        writeTo(containerData::addFile);
    }

    void writeTo(BiConsumer<Path, String> fileWriter) {
        fileWriter.accept(motdPath, renderedString);
    }


    // Needs to be public for Velocity to use it.
    public static class Node {

        private final String type;
        private final State state;
        private final Optional<String> installed;
        private final Optional<String> wanted;
        private final Optional<ApplicationId> owner;

        public Node(String type, State state, Optional<String> installed, Optional<String> wanted, Optional<ContainerNodeSpec.Owner> owner) {
            this.type = type;
            this.state = state;
            this.installed = installed;
            this.wanted = wanted;
            this.owner = owner.map(id -> ApplicationId.from(id.tenant, id.application, id.instance));
        }

        public String type() { return type; }
        public State state() { return state; }
        public Optional<String> installed() { return installed; }
        public Optional<String> wanted() { return wanted; }
        public Optional<ApplicationId> owner() { return owner; }

    }

}
