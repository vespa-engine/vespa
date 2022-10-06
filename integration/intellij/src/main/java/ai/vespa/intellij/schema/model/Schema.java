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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A schema.
 *
 * @author bratseth
 */
public class Schema {

    private final SdFile definition;

    /** The schema this inherits, or empty if none. Resolved lazily. */
    private Optional<Schema> parent = null;

    /** The children of this. Resolved lazily. */
    private Map<String, Schema> children = null;

    /** The profiles of this, either defined inside it or in separate .profile files. Resolved lazily. */
    private Map<String, RankProfile> rankProfiles = null;

    public Schema(SdFile definition) {
        this.definition = definition;
    }

    public String name() { return definition.getName().substring(0, definition.getName().length() - 3); }

    public SdFile definition() { return definition; }

    /** The path of the location of this schema from the project root. */
    public Path path() { return Path.fromString(definition.getContainingDirectory().getVirtualFile().getPath()); }

    public Optional<Schema> parent() {
        if (parent != null) return parent;
        Optional<SdSchemaDefinition> schemaDefinition = PsiTreeUtil.collectElementsOfType(definition, SdSchemaDefinition.class).stream().findFirst();
        if (schemaDefinition.isEmpty()) return Optional.empty(); // No valid schema definition in schema file
        return parent = AST.inherits(schemaDefinition.get())
                           .stream()
                           .findFirst() // Only one schema can be inherited; ignore any following
                           .map(inheritedNode -> fromProjectFile(definition.getProject(), path().append(inheritedNode.getText() + ".sd")));
    }

    public Map<String, Schema> children() {
        if (children != null) return children;
        children = new HashMap<>();
        for (PsiFile file : Files.allFilesIn(path(),"sd", definition().getProject())) {
            Schema schema = new Schema((SdFile)file);
            if ( schema.parent().isPresent() && Objects.equals(schema.parent().get().name(), name()))
                children.put(schema.name(), schema);
        }
        return children;
    }

    /**
     * Returns the rank profiles in this, defined either inside it or in separate .profile files,
     * or inherited from the parent schema.
     */
    public Map<String, RankProfile> rankProfiles() {
        if (rankProfiles != null) return rankProfiles;
        rankProfiles = parent().isPresent() ? parent().get().rankProfiles() : new HashMap<>();

        for (var profileDefinition : PsiTreeUtil.collectElementsOfType(definition, SdRankProfileDefinition.class))
            rankProfiles.put(profileDefinition.getName(), new RankProfile(profileDefinition, this));

        for (var profileFile : Files.allFilesIn(path().append(name()), "profile", definition.getProject())) {
            var profileDefinitions = PsiTreeUtil.collectElementsOfType(profileFile, SdRankProfileDefinition.class);
            if (profileDefinitions.size() != 1) continue; // invalid file
            var profileDefinition = profileDefinitions.stream().findAny().get();
            rankProfiles.put(profileDefinition.getName(), new RankProfile(profileDefinition, this));
        }
        return rankProfiles;
    }

    public Map<String, List<Function>> functions() {
        Map<String, List<Function>> functions = new HashMap<>();
        for (var profile : rankProfiles().values()) {
            for (var entry : profile.definedFunctions().entrySet())
                functions.computeIfAbsent(entry.key(), k -> new ArrayList<>()).addAll(entry.getValue());
        }
        return functions;
    }

    @Override
    public String toString() { return "schema " + name(); }

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
        return new Schema((SdFile)psiFile.get());
    }

}
