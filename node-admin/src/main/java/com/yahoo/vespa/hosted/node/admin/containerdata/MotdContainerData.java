package com.yahoo.vespa.hosted.node.admin.containerdata;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.node.admin.ContainerNodeSpec;
import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.task.util.file.TemplateFile;
import com.yahoo.vespa.hosted.provision.Node.State;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.BiConsumer;

public class MotdContainerData {

    private static final Path motdPath = Paths.get("etc/profile.d/motd.sh");
    private static final Path templatePath = Paths.get("src/main/application/templates/motd.sh.vm");

    private final TemplateFile template;

    public MotdContainerData(ContainerNodeSpec nodeSpec, Environment environment) {
        template = new TemplateFile(templatePath)
                .set("zone", environment)
                .set("node", new Node(nodeSpec.nodeType,
                                      nodeSpec.nodeState,
                                      nodeSpec.vespaVersion,
                                      nodeSpec.wantedVespaVersion,
                                      nodeSpec.owner));
    }

    public void writeTo(ContainerData containerData) {
        writeTo(containerData::addFile);
    }

    void writeTo(BiConsumer<Path, String> fileWriter) {
        System.out.println(template.render());
        fileWriter.accept(motdPath, template.render());
    }

    private static class Node {

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
