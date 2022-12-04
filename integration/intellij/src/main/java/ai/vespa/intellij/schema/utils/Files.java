// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.utils;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Provide workable file operations on top of IntelliJ's API.
 */
public class Files {

    /**
     * Opens a file.
     *
     * @return the file or empty if not found
     */
    public static Optional<PsiFile> open(Path file, Project project) {
        // Apparently there's no API for getting a file by path.
        // This method returns all files having a particular name.
        for (VirtualFile candidate : FilenameIndex.getAllFilesByExt(project, file.extension())) {
            // Not safe, but (at least in tests) there doesn't appear to be a way to get the workspace root (/src)
            if (candidate.getPath().endsWith(file.getRelative()))
                return Optional.of(PsiManager.getInstance(project).findFile(candidate));
        }
        return Optional.empty();
    }

    /** Returns all the files in the given directory with the given ending. */
    public static List<PsiFile> allFilesIn(Path dir, String extension, Project project) {
        if (extension.startsWith("."))
            extension = extension.substring(1);
        return FilenameIndex.getAllFilesByExt(project, extension)
                            .stream()
                            .filter(file -> file.getParent().getPath().endsWith(dir.getRelative()))
                            .map(file -> PsiManager.getInstance(project).findFile(file))
                            .collect(Collectors.toList());
    }

}
