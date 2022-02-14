// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.model;

import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
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

    /** The functions defined in this, lazily computed */
    private Map<String, List<Function>> functions = null;

    /** The profiles inherited by this - lazily initialized. */
    private Map<String, RankProfile> inherited = null;

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
                              .map(reference -> owner.rankProfile(reference.getCanonicalText()))
                              .flatMap(r -> r.stream())
                              .collect(Collectors.toMap(p -> p.name(), p -> p));
    }

    /** Returns the functions defined in this. */
    public Map<String, List<Function>> definedFunctions() {
        if (functions != null) return functions;
        functions = new HashMap<>();
        for (SdFunctionDefinition function : PsiTreeUtil.findChildrenOfType(definition, SdFunctionDefinition.class))
            functions.computeIfAbsent(function.getName(), k -> new ArrayList<>()).add(Function.from(function, this));
        return functions;
    }

}
