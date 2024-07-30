package ai.vespa.schemals.intellij;

import ai.vespa.schemals.parser.SchemaIntellijLexer;
import ai.vespa.schemals.parser.SchemaTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Entry point for providing syntax highlighting.
 * Used in plugin.xml
 */
public class SchemaSyntaxHighlighter extends SyntaxHighlighterBase {

    private final TextAttributesKey[] KEYWORD = new TextAttributesKey[] {
            TextAttributesKey.createTextAttributesKey("SCHEMA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    };
    private final TextAttributesKey[] TYPE = new TextAttributesKey[] {
            TextAttributesKey.createTextAttributesKey("SCHEMA_TYPE", DefaultLanguageHighlighterColors.CLASS_REFERENCE)
    };
    private final TextAttributesKey[] NUMBER = new TextAttributesKey[] {
            TextAttributesKey.createTextAttributesKey("SCHEMA_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    };

    private final TextAttributesKey[] COMMENT = new TextAttributesKey[] {
            TextAttributesKey.createTextAttributesKey("SCHEMA_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    };

    private final TextAttributesKey[] IDENTIFIER = new TextAttributesKey[] {
            TextAttributesKey.createTextAttributesKey("SCHEMA_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
    };

    private final TextAttributesKey[] STRING = new TextAttributesKey[] {
            TextAttributesKey.createTextAttributesKey("SCHEMA_STRING", DefaultLanguageHighlighterColors.STRING)
    };

    private final TextAttributesKey[] BOOLEAN = new TextAttributesKey[] {
            TextAttributesKey.createTextAttributesKey("SCHEMA_BOOLEAN", DefaultLanguageHighlighterColors.CONSTANT)
    };


    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new SchemaIntellijLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType iElementType) {
        if (iElementType.equals(SchemaTypes.KEYWORD)) {
            return KEYWORD;
        } else if (iElementType.equals(SchemaTypes.TYPE)) {
            return TYPE;
        } else if (iElementType.equals(SchemaTypes.NUMBER)) {
            return NUMBER;
        } else if (iElementType.equals(SchemaTypes.COMMENT)) {
            return COMMENT;
        } else if (iElementType.equals(SchemaTypes.IDENTIFIER)) {
            return IDENTIFIER;
        } else if (iElementType.equals(SchemaTypes.STRING)) {
            return STRING;
        } else if (iElementType.equals(SchemaTypes.BOOLEAN)) {
            return BOOLEAN;
        }
        return new TextAttributesKey[0];
    }
};
