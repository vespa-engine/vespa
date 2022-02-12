// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.model;

import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.psi.SdSchemaDefinition;
import ai.vespa.intellij.schema.utils.AST;
import ai.vespa.intellij.schema.utils.Files;
import ai.vespa.intellij.schema.utils.Path;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
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

    /** The schema this inherits, or empty if none. Resolved lazily. */
    private Optional<Schema> inherited = null;

    public Schema(SdFile definition, Path path, Project project) {
        this.definition = definition;
        this.path = path;
        this.project = project;
    }

    public String name() { return definition.getName().substring(0, definition.getName().length() - 3); }

    public SdFile definition() { return definition; }

    public Optional<Schema> inherited() {
        if (inherited != null) return inherited;
        Optional<SdSchemaDefinition> schemaDefinition = PsiTreeUtil.collectElementsOfType(definition, SdSchemaDefinition.class).stream().findFirst();
        if (schemaDefinition.isEmpty()) return Optional.empty(); // No valid schema definition in schema file
        return inherited = AST.inherits(schemaDefinition.get())
                              .stream()
                              .findFirst() // Only one schema can be inherited; ignore any following
                              .map(inheritedNode -> fromProjectFile(project, path.getParentPath().append(inheritedNode.getText() + ".sd")));
    }

    /** Returns a rank profile belonging to this, defined either inside it or in a separate .profile file */
    public Optional<RankProfile> rankProfile(String name) {
        var definition = findProfileElement(name, this.definition); // Look up in this
        if (definition.isEmpty()) { // Look up in a separate file schema-name/profile-name.profile
            Optional<PsiFile> file = Files.open(path.getParentPath().append(name()).append(name + ".profile"), project);
            if (file.isPresent())
                definition = findProfileElement(name, file.get());
        }
        if (definition.isEmpty() && inherited().isPresent()) { // Look up in parent
            return inherited().get().rankProfile(name);
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
