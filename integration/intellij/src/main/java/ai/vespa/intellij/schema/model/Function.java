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

    public Function(PsiElement definition) {
        this.definition = definition;
    }

    public PsiElement definition() { return definition; }

    public static Function from(SdFirstPhaseDefinition firstPhase) {
        return new Function(firstPhase);
    }

    public static Function from(SdSecondPhaseDefinition secondPhase) {
        return new Function(secondPhase);
    }

    public static Function from(SdFunctionDefinition definition) {
        return new Function(definition);
    }

    public static Function from(PsiElement definition) {
        return new Function(definition);
    }

}
