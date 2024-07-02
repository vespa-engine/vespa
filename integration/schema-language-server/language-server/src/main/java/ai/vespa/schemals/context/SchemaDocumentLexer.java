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

    public LexicalToken tokenBeforePosition(Position pos, boolean skipNL) {
        LexicalToken lastToken = null;
        for (LexicalToken token : tokens) {
            if (skipNL && token.type() == Token.TokenType.NL) continue;
            Position tokenStart = token.range().getStart();
            if (CSTUtils.positionLT(pos, tokenStart) || pos.equals(tokenStart)) return lastToken;

            lastToken = token;
        }
        return lastToken;
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
