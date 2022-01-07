// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.template;

import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.nio.file.Path;

/**
 * Parses a template file, see {@link Template} for details.
 *
 * @author hakonhall
 */
public class TemplateFile {
    public static Template read(Path path) { return read(path, new TemplateDescriptor()); }

    public static Template read(Path path, TemplateDescriptor descriptor) {
        String content = new UnixPath(path).readUtf8File();
        return TemplateParser.parse(descriptor, content);
    }
}
