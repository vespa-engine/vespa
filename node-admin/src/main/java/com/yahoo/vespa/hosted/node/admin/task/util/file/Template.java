// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Uses the Velocity engine to render a template, to and from both String and Path objects.
 *
 * @author hakonhall
 * @author jonmv
 */
public class Template {

    private final VelocityEngine velocityEngine = new VelocityEngine();
    private final VelocityContext velocityContext = new VelocityContext();
    private final String template;

    private Template(String template) {
        this.template = template;

        velocityEngine.addProperty(Velocity.RUNTIME_LOG_LOGSYSTEM_CLASS,
                                   "org.apache.velocity.runtime.log.NullLogSystem");
        velocityEngine.init();
    }

    public static Template at(Path templatePath) {
        return of(IOExceptionUtil.uncheck(() -> new String(Files.readAllBytes(templatePath))));
    }

    public static Template of(String template) {
        return new Template(template);
    }

    public Template set(String name, Object value) {
        velocityContext.put(name, value);
        return this;
    }

    public FileWriter getFileWriterTo(Path destinationPath) {
        return new FileWriter(destinationPath, this::render);
    }

    public String render() {
        StringWriter writer = new StringWriter();
        velocityEngine.evaluate(velocityContext, writer, "Template", template);
        return writer.toString();
    }

}
