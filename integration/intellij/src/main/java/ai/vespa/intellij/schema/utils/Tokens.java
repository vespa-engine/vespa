// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.utils;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;

import java.util.Arrays;
import java.util.stream.Collectors;

/** A collection of tokens with a current index. */
public class Tokens {

    private final ASTNode[] nodes;
    private int i = 0;

    private Tokens(PsiElement element) {
        nodes = element.getNode().getChildren(null);
    }

    /**
     * Advances to the next token, if it is of the given type.
     *
     * @return true if the current token was of the given type and we advanced it, false
     *         if it was not and nothing was changed
     */
    public boolean skip(IElementType... tokenTypes) {
        if (current() == null) return false;
        boolean is = is(tokenTypes);
        if (is)
            i++;
        return is;
    }

    /**
     * Advances beyond the next token, if it is whitespace.
     *
     * @return true if the current token was of the given type and we advanced it, false
     * if it was not and nothing was changed
     */
    public boolean skipWhitespace() {
        if (current() == null) return false;
        boolean is = isWhitespace();
        if (is)
            i++;
        return is;
    }

    /** Returns whether the current token is of the given type */
    public boolean is(IElementType... tokenTypes) {
        if (current() == null) return false;
        for (IElementType type : tokenTypes)
            if (current().getElementType() == type) return true;
        return false;
    }

    /** Returns whether the current token is whitespace */
    public boolean isWhitespace() {
        if (current() == null) return false;
        return current().getPsi() instanceof PsiWhiteSpace;
    }

    /** Returns whether the current token is an element */
    public boolean isElement() {
        if (current() == null) return false;
        return current() instanceof LeafPsiElement;
    }

    /** Returns the current token if it is of the required type and throws otherwise. */
    public ASTNode require(IElementType... tokenTypes) {
        if (!is(tokenTypes))
            throw new IllegalArgumentException("Expected " + toString(tokenTypes) + " but got " + current());
        ASTNode current = current();
        i++;
        return current;
    }

    /** Returns the current token if it is any element and throws otherwise. */
    public ASTNode requireElement() {
        if (!isElement())
            throw new IllegalArgumentException("Expected an element but got " + current().getClass());
        ASTNode current = current();
        i++;
        return current;
    }

    public void requireWhitespace() {
        if (!isWhitespace())
            throw new IllegalArgumentException("Expected whitespace, but got " + current());
        i++;
    }

    /** Returns the current token (AST node), or null if we have reached the end. */
    public ASTNode current() {
        if (i >= nodes.length) return null;
        return nodes[i];
    }

    private String toString(IElementType[] tokens) {
        return Arrays.stream(tokens).map(token -> token.getDebugName()).collect(Collectors.joining(", "));
    }

    public static Tokens of(PsiElement element) {
        return new Tokens(element);
    }

    /** For debugging: Prints the remaining to standard out. */
    public void dump() {
        for (int j = i; j < nodes.length; j++)
            System.out.println(nodes[j]);
    }

}
