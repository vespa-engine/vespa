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

    private final String name;

    private final PsiElement definition;

    private final RankProfile owner;

    private Function(String name, PsiElement definition, RankProfile owner) {
        this.name = name;
        this.definition = definition;
        this.owner = owner;
    }

    public PsiElement definition() { return definition; }

    public String name() { return name; }

    public RankProfile owner() { return owner; }

    @Override
    public String toString() {
        return "function " + name();
    }

    public static Function from(SdFirstPhaseDefinition firstPhase, RankProfile owner) {
        return new Function("first-phase", firstPhase, owner);
    }

    public static Function from(SdSecondPhaseDefinition secondPhase, RankProfile owner) {
        return new Function("second-phase", secondPhase, owner);
    }

    public static Function from(SdFunctionDefinition definition, RankProfile owner) {
        return new Function(definition.getName(), definition, owner);
    }

}
