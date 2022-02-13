// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.utils;

import ai.vespa.intellij.schema.psi.SdTypes;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;

/**
 * AST navigation tools.
 *
 * @author bratseth
 */
public class AST {

    /** Returns the nodes following "inherits" in the given element. */
    public static List<ASTNode> inherits(PsiElement element) {
        Tokens tokens = Tokens.of(element);
        tokens.requireElement();
        tokens.requireWhitespace();
        tokens.require(SdTypes.IDENTIFIER_VAL, SdTypes.IDENTIFIER_WITH_DASH_VAL);
        if ( ! tokens.skipWhitespace()) return List.of();
        if ( ! tokens.skip(SdTypes.INHERITS)) return List.of();
        tokens.requireWhitespace();
        List<ASTNode> inherited = new ArrayList<>();
        do {
            inherited.add(tokens.require(SdTypes.IDENTIFIER_VAL, SdTypes.IDENTIFIER_WITH_DASH_VAL));
            tokens.skipWhitespace();
            if ( ! tokens.skip(SdTypes.COMMA)) break;
            tokens.skipWhitespace();
        } while (true);
        return inherited;
    }

}
