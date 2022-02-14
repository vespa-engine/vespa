// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.model;

import ai.vespa.intellij.schema.psi.SdFirstPhaseDefinition;
import ai.vespa.intellij.schema.psi.SdFunctionDefinition;
import ai.vespa.intellij.schema.psi.SdSecondPhaseDefinition;
import com.intellij.psi.PsiElement;

/**
 * @author bratseth
 */
public class Function {

    private final PsiElement definition;

    private final RankProfile owner;

    public Function(PsiElement definition, RankProfile owner) {
        this.definition = definition;
        this.owner = owner;
    }

    public PsiElement definition() { return definition; }
    public RankProfile owner() { return owner; }

    public static Function from(SdFirstPhaseDefinition firstPhase, RankProfile owner) {
        return new Function(firstPhase, owner);
    }

    public static Function from(SdSecondPhaseDefinition secondPhase, RankProfile owner) {
        return new Function(secondPhase, owner);
    }

    public static Function from(SdFunctionDefinition definition, RankProfile owner) {
        return new Function(definition, owner);
    }

    public static Function from(PsiElement definition, RankProfile owner) {
        return new Function(definition, owner);
    }

}
