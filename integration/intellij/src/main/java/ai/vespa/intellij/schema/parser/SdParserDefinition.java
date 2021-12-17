// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.parser;

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
import ai.vespa.intellij.schema.SdLanguage;
import ai.vespa.intellij.schema.lexer.SdLexerAdapter;
import ai.vespa.intellij.schema.psi.SdFile;
import ai.vespa.intellij.schema.psi.SdTypes;

/**
 * This class is used for the extension (in plugin.xml), to make the parsing process use the plugin code.
 *
 * @author Shahar Ariel
 */
public class SdParserDefinition implements ParserDefinition {

    public static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
    public static final TokenSet COMMENTS = TokenSet.create(SdTypes.COMMENT);
    public static final TokenSet STRINGS = TokenSet.create(SdTypes.STRING_REG);
    
    public static final IFileElementType FILE = new IFileElementType(SdLanguage.INSTANCE);
    
    @Override
    public Lexer createLexer(Project project) {
        return new SdLexerAdapter();
    }
    
    @Override
    public PsiParser createParser(final Project project) {
        return new SdParser();
    }
    
    @Override
    public TokenSet getWhitespaceTokens() {
        return WHITE_SPACES;
    }
    
    @Override
    public TokenSet getCommentTokens() {
        return COMMENTS;
    }
    
    @Override
    public TokenSet getStringLiteralElements() {
        return STRINGS;
    }
    
    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }
    
    @Override
    public PsiFile createFile(FileViewProvider viewProvider) {
        return new SdFile(viewProvider);
    }
    
    @Override
    public SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }
    
    @Override
    public PsiElement createElement(ASTNode node) {
        return SdTypes.Factory.createElement(node);
    }
    
}



    
    
