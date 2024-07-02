package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.*;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/*
 * Class to manage a document as a list of tokens.
 * Contrary to usual standards, this will construct a list of tokens *after* parsing is done.
 * This is because we can exploit CongoCC to avoid dealing with complex lexing logic.
 *
 * @author mangern
 * */
public class SchemaDocumentLexer {
    private SchemaNode CST;
    private ArrayList<LexicalToken> tokens = new ArrayList<>();

    public record LexicalToken(Token.TokenType type, Range range, boolean isDirty) { }

    public void setCST(SchemaNode CST) {
        this.CST = CST;
        tokens.clear();

        collectAllTokens(CST);
    }

    public void dumpTokens(PrintStream logger) {
        for (var token : tokens) {
            Position start = token.range().getStart();
            Position end = token.range().getEnd();
            logger.println(
                token.type().toString() + 
                (token.isDirty() ? " [DIRTY]" : "") +
                ", (" + start.getLine() + ", " + start.getCharacter() + ") - (" + end.getLine() + ", " + end.getCharacter() + ")");
        }
    }


    public LexicalToken tokenAtOrBeforePosition(Position pos, boolean skipNL) {
        int index = indexOfPosition(pos, skipNL);
        if (index == -1)return null;

        //if (pos.equals(tokens.get(index).range().getStart())) index--;

        //if (index == -1)return null;

        return tokens.get(index);
    }

    /*
     * Finds the sequence of token types supplied by 'pattern' by searching backwards
     * up to 'allowSkip' places before the supplied position
     * Returns a reference to the LexicalToken at the position if found
     * null otherwise
     *
     * TODO: The ideal pattern whould be some kind of regular expression
     * */
    public LexicalToken matchBackwards(Position pos, int allowSkip, boolean allowDirty, Token.TokenType... pattern) {
        int index = indexOfPosition(pos, false);
        if (index == -1)return null;
        int skipped = 0;

        for (int patternStart = index - pattern.length + 1; patternStart >= 0 && skipped <= allowSkip; patternStart--, skipped++) {
            boolean matched = true;
            for (int i = 0; i < pattern.length; i++) {
                if (pattern[i] != tokens.get(patternStart + i).type()) {
                    matched = false;
                }

                if (tokens.get(patternStart + i).isDirty() && !allowDirty) {
                    matched = false;
                }
            }

            if (matched) return tokens.get(patternStart);
        }

        return null;
    }

    /*
     * Returns the index of the last token at or before the supplied position
     * -1 if no such token exists
     * */
    private int indexOfPosition(Position pos, boolean skipNL) {
        int lastIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (skipNL && tokens.get(i).type() == Token.TokenType.NL) continue;
            if (CSTUtils.positionLT(pos, tokens.get(i).range().getStart())) break;
            lastIndex = i;
        }
        return lastIndex;
    }

    private void collectAllTokens(SchemaNode node) {
        if (node == null)return;
        if (node.getDirtyType() != null) {
            tokens.add(new LexicalToken(node.getDirtyType(), node.getRange(), node.isDirty()));
        }
        for (int i = 0; i < node.size(); i++) {
            collectAllTokens(node.get(i));
        }
    }
}
