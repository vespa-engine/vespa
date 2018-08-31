// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Will set the content of a file based on a user-defined function that gets the current
 * content of the file.
 *
 * <p>For instance this class can be used to write {@code enabled} into
 * {@code /sys/kernel/mm/transparent_hugepage/enabled} only if the current content is different
 * than {@code [always] madvise never}.
 *
 * @author hakon
 */
public class FileUpdater {
    private static final Logger logger = Logger.getLogger(FileUpdater.class.getName());

    private final UnixPath path;
    private final ContentMapper contentMapper;
    private final FileContentCache fileContentCache;

    @FunctionalInterface
    public interface ContentMapper {
        /**
         * @param currentContent The current content of the file, or empty if file does not exist
         * @return The content to write to the file (creating the file if file does not exist),
         *         or empty if it should be left as-is
         */
        Optional<String> getContentToWrite(Optional<String> currentContent);
    }

    public FileUpdater(Path path, ContentMapper contentMapper) {
        this.path = new UnixPath(path);
        this.contentMapper = contentMapper;
        this.fileContentCache = new FileContentCache(this.path);
    }

    public boolean converge(TaskContext context) {
        Optional<String> content = path.getAttributesIfExists()
                .map(attributes -> fileContentCache.get(attributes.lastModifiedTime()));
        Optional<String> newContent = contentMapper.getContentToWrite(content);
        if (!newContent.isPresent()) {
            return false;
        }

        if (content.isPresent()) {
            context.recordSystemModification(logger, "Patching file " + path);
        } else {
            Path parents = path.toPath().getParent();
            if (!Files.isDirectory(parents)) {
                context.recordSystemModification(logger, "Creating parent directories for " + path);
                path.createParents();
            }
            context.recordSystemModification(logger, "Creating file " + path);
        }

        path.writeUtf8File(newContent.get());
        return true;
    }
}
