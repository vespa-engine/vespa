// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.model;

import ai.vespa.intellij.schema.SdUtil;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.psi.SdTypes;
import ai.vespa.intellij.schema.utils.AST;
import com.intellij.lang.ASTNode;

import java.util.ArrayList;
import java.util.List;
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
    public List<RankProfile> findInherited() {
        return AST.inherits(definition).stream()
                  .map(parentIdentifierAST -> parentIdentifierAST.getPsi().getReference())
                  .filter(reference -> reference != null)
                  .map(reference -> owner.rankProfile(reference.getCanonicalText()))
                  .flatMap(r -> r.stream())
                  .collect(Collectors.toList());
    }

}
