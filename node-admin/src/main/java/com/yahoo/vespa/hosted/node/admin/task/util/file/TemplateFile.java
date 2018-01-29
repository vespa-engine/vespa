// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.nio.file.Path;

/**
 * Make a file based on a Velocity template file.
 *
 * @author hakonhall
 */
public class TemplateFile {
    private final Path templatePath;
    private final VelocityEngine velocityEngine;
    private final VelocityContext velocityContext = new VelocityContext();

    public TemplateFile(Path templatePath) {
        this.templatePath = templatePath;
        velocityEngine = new VelocityEngine();
        velocityEngine.addProperty(
                Velocity.RUNTIME_LOG_LOGSYSTEM_CLASS,
                "org.apache.velocity.runtime.log.NullLogSystem");
        velocityEngine.addProperty(Velocity.FILE_RESOURCE_LOADER_PATH, templatePath.getParent().toString());
        velocityEngine.init();
    }

    public TemplateFile set(String name, String value) {
        velocityContext.put(name, value);
        return this;
    }

    public FileWriter getFileWriterTo(Path destinationPath) {
        return new FileWriter(destinationPath, this::render);
    }

    private String render() {
        Template template = velocityEngine.getTemplate(templatePath.getFileName().toString(), "UTF-8");
        StringWriter writer = new StringWriter();
        template.merge(velocityContext, writer);
        return writer.toString();
    }
}
