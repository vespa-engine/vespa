package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.Token.ParseExceptionSource;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyDirtyNodes extends Identifier {

    public IdentifyDirtyNodes(ParseContext context) {
		super(context);
	}

	private String getParseExceptionMessage(ParseException exception) {

        Throwable cause = exception.getCause();

        if (cause == null) {
            return exception.getMessage();
        }

        return cause.getMessage();
    }

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        ParseExceptionSource parseException = node.getParseExceptionSource();
        
        if (parseException != null) {
            TokenSource tokenSource = node.getTokenSource();
            Range range = CSTUtils.getRangeFromOffsets(tokenSource, parseException.beginOffset, parseException.endOffset);
            String message = getParseExceptionMessage(parseException.parseException);
            ret.add(new Diagnostic(range, message));
        }

        IllegalArgumentException illegalArgumentException = node.getIllegalArgumentException();

        if (illegalArgumentException != null) {
            ret.add(new Diagnostic(node.getRange(), illegalArgumentException.getMessage()));
        }


        if (
            node.getIsDirty() &&
            node.isLeaf() &&
            parseException == null &&
            illegalArgumentException == null
        ) {
            ret.add(new Diagnostic(node.getRange(), "Invalid syntax."));
        }

        return ret;
    }
}