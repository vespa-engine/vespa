// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.model;

import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.utils.Files;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.Optional;

/**
 * A schema.
 *
 * @author bratseth
 */
public class Schema {

    private final SdFile definition;

    /** The path to this schema */
    private final Path path;

    /** The project this is part of */
    private final Project project;

    public Schema(SdFile definition, Path path, Project project) {
        this.definition = definition;
        this.path = path;
        this.project = project;
    }

    public String name() { return definition.getName().substring(0, definition.getName().length() - 3); }

    public SdFile definition() { return definition; }

    /** Returns a rank profile belonging to this, defined either inside it or in a separate .profile file */
    public Optional<RankProfile> rankProfile(String name) {
        var definition = findProfileElement(name, this.definition);
        if (definition.isEmpty()) { // Defined in a separate file schema-name/profile-name.profile
            Optional<PsiFile> file = Files.open(path.getParentPath().append(name()).append(name + ".profile"), project);
            if (file.isPresent())
                definition = findProfileElement(name, file.get());
        }
        return definition.map(d -> new RankProfile(d, this));
    }

    private Optional<SdRankProfileDefinition> findProfileElement(String name, PsiFile file) {
        return PsiTreeUtil.collectElementsOfType(file, SdRankProfileDefinition.class)
                          .stream()
                          .filter(p -> name.equals(p.getName()))
                          .findAny();
    }

    /**
     * Returns the profile of the given name from the given file.
     *
     * @throws IllegalArgumentException if not found
     */
    public static Schema fromProjectFile(Project project, Path file) {
        Optional<PsiFile> psiFile = Files.open(file, project);
        if (psiFile.isEmpty())
            throw new IllegalArgumentException("Could not find file '" + file + "'");
        if ( ! (psiFile.get() instanceof SdFile))
            throw new IllegalArgumentException(file + " is not a schema file");
        return new Schema((SdFile)psiFile.get(), file, project);
    }

}
