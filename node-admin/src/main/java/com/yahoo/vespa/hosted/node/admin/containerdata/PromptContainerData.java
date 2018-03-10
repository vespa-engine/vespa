package com.yahoo.vespa.hosted.node.admin.containerdata;

import com.yahoo.vespa.hosted.node.admin.component.Environment;
import com.yahoo.vespa.hosted.node.admin.task.util.file.TemplateFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;

public class PromptContainerData {

    private static final Path promptPath = Paths.get("etc/profile.d/prompt.sh");
    private static final Path templatePath = Paths.get("src/main/application/templates/prompt.sh.vm");

    private final TemplateFile template;

    public PromptContainerData(Environment environment) {
        template = new TemplateFile(templatePath)
                .set("zone", environment);
    }

    public void writeTo(ContainerData containerData) {
        writeTo(containerData::addFile);
    }

    void writeTo(BiConsumer<Path, String> fileWriter) {
        fileWriter.accept(promptPath, template.render());
    }

}
