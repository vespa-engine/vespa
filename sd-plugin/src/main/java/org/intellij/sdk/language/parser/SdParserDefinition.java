// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.intellij.sdk.language.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.sdk.language.SdLanguage;
import org.intellij.sdk.language.SdLexerAdapter;
import org.intellij.sdk.language.parser.SdParser;
import org.intellij.sdk.language.psi.SdFile;
import org.intellij.sdk.language.psi.SdTypes;
import org.jetbrains.annotations.NotNull;

/**
 * This class is used for the extension (in plugin.xml), to make the parsing process use the plugin code.
 * @author shahariel
 */
public class SdParserDefinition implements ParserDefinition {
    public static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
    public static final TokenSet COMMENTS = TokenSet.create(SdTypes.COMMENT);
    public static final TokenSet STRINGS = TokenSet.create(SdTypes.STRING_REG);
    
    public static final IFileElementType FILE = new IFileElementType(SdLanguage.INSTANCE);
    
    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new SdLexerAdapter();
    }
    
    @NotNull
    @Override
    public PsiParser createParser(final Project project) {
        return new SdParser();
    }
    
    @NotNull
    @Override
    public TokenSet getWhitespaceTokens() {
        return WHITE_SPACES;
    }
    
    @NotNull
    @Override
    public TokenSet getCommentTokens() {
        return COMMENTS;
    }
    
    @NotNull
    @Override
    public TokenSet getStringLiteralElements() {
        return STRINGS;
    }
    
    @NotNull
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }
    
    @NotNull
    @Override
    public PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new SdFile(viewProvider);
    }
    
    @NotNull
    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }
    
    @NotNull
    @Override
    public PsiElement createElement(ASTNode node) {
        return SdTypes.Factory.createElement(node);
    }
    
}



    
    
