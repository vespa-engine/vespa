// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.file;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
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
public class FileContentUpdater {
    private static final Logger logger = Logger.getLogger(FileContentUpdater.class.getName());

    private final UnixPath path;
    private final ContentMapper contentMapper;
    private final FileContentCache fileContentCache;

    @FunctionalInterface
    public interface ContentMapper {
        /**
         * @param currentContent The current content of the file
         * @return The new content to write to the file, or empty if it should be left as-is
         */
        Optional<String> getContentToWrite(String currentContent);
    }

    public FileContentUpdater(Path path, ContentMapper contentMapper) {
        this.path = new UnixPath(path);
        this.contentMapper = contentMapper;
        this.fileContentCache = new FileContentCache(this.path);
    }

    public boolean converge(TaskContext context) {
        Optional<FileAttributes> fileAttributes = path.getAttributesIfExists();
        if (!fileAttributes.isPresent()) {
            throw new UncheckedIOException(new NoSuchFileException(path.toString()));
        }

        String content = fileContentCache.get(fileAttributes.get().lastModifiedTime());
        Optional<String> newContent = contentMapper.getContentToWrite(content);
        if (!newContent.isPresent()) {
            return false;
        }

        context.recordSystemModification(logger, "Patching file " + path);
        path.writeUtf8File(newContent.get());
        return true;
    }
}
