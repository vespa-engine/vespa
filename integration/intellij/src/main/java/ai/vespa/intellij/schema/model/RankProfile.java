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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A rank profile
 *
 * @author bratseth
 */
public class RankProfile {

    private final SdRankProfileDefinition definition;

    private final Schema owner;

    /** The functions defined in this, lazily computed */
    private Map<String, List<Function>> functions = null;

    /** The profiles inherited by this - lazily initialized. */
    private Map<String, RankProfile> inherited = null;

    /** The children of this - lazily inherited */
    private Map<String, RankProfile> children = null;

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
    public Map<String, RankProfile> inherited() {
        if (inherited != null) return inherited;
        return inherited = AST.inherits(definition).stream()
                              .map(parentIdentifierAST -> parentIdentifierAST.getPsi().getReference())
                              .filter(reference -> reference != null)
                              .map(reference -> owner.rankProfiles().get(reference.getCanonicalText()))
                              .filter(r -> r != null)
                              .collect(Collectors.toMap(p -> p.name(), p -> p));
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

    public Map<String, RankProfile> children() {
        if (children != null) return children;
        children = new HashMap<>();
        for (var profile : owner.rankProfiles().values()) {
            if (profile.inherited().containsKey(this.name()))
                children.put(profile.name(), profile);
        }
        return children;
    }

    @Override
    public String toString() {
        return "rank-profile " + name();
    }

}
