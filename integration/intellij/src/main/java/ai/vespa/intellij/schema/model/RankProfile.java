// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.model;

import ai.vespa.intellij.schema.psi.SdFirstPhaseDefinition;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.psi.SdSecondPhaseDefinition;
import ai.vespa.intellij.schema.utils.AST;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A rank profile
 *
 * @author bratseth
 */
public class RankProfile {

    private final SdRankProfileDefinition definition;

    private final Schema owner;

    /** The functions defined in this. Resolved lazily. */
    private Map<String, List<Function>> functions = null;

    /** The profiles inherited by this. Resolved lazily. */
    private Map<String, RankProfile> parents = null;

    /** The children of this. Resolved lazily. */
    private List<RankProfile> children = null;

    public RankProfile(SdRankProfileDefinition definition, Schema owner) {
        this.definition = Objects.requireNonNull(definition);
        this.owner = owner;
    }

    public String name() { return definition.getName(); }

    public SdRankProfileDefinition definition() { return definition; }

    /**
     * Returns the profiles inherited by this.
     *
     * @return the profiles this inherits from, empty if none
     */
    public Map<String, RankProfile> parents() {
        if (parents != null) return parents;
        if (owner == null) return Map.of(); // Some code paths do not yet instantiate from a schema
        return parents = AST.inherits(definition).stream()
                            .map(parentIdentifierAST -> parentIdentifierAST.getPsi().getReference())
                            .filter(reference -> reference != null)
                            .map(reference -> owner.rankProfiles().get(reference.getCanonicalText()))
                            .filter(profile -> profile != null)
                            .collect(Collectors.toMap(profile -> profile.name(), profile -> profile));
    }

    public List<RankProfile> children() {
        if (children != null) return children;
        if (owner == null) return List.of(); // Some code paths do not yet instantiate from a schema
        return children = children(owner);
    }

    private List<RankProfile> children(Schema schema) {
        children = new ArrayList<>();
        for (var profile : schema.rankProfiles().values()) {
            if (profile.parents().containsKey(this.name()))
                children.add(profile);
        }
        for (var childSchema : schema.children().values())
            children.addAll(children(childSchema));
        return children;
    }

    /** Returns the functions defined in this. */
    public Map<String, List<Function>> definedFunctions() {
        if (functions != null) return functions;
        functions = new HashMap<>();
        for (SdFunctionDefinition function : PsiTreeUtil.findChildrenOfType(definition, SdFunctionDefinition.class))
            functions.computeIfAbsent(function.getName(), k -> new ArrayList<>()).add(Function.from(function, this));
        PsiTreeUtil.findChildrenOfType(definition, SdFirstPhaseDefinition.class).stream().findFirst()
                   .map(firstPhase -> Function.from(firstPhase, this))
                   .ifPresent(firstPhase -> functions.put(firstPhase.name(), List.of(firstPhase)));
        PsiTreeUtil.findChildrenOfType(definition, SdSecondPhaseDefinition.class).stream().findFirst()
                   .map(secondPhase -> Function.from(secondPhase, this))
                   .ifPresent(secondPhase -> functions.put(secondPhase.name(), List.of(secondPhase)));
        return functions;
    }

    @Override
    public String toString() {
        return "rank-profile " + name();
    }

}
