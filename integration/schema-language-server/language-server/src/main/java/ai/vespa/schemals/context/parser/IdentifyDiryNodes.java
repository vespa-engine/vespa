package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.Token.ParseExceptionSource;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyDiryNodes extends Identifier {


    public IdentifyDiryNodes(PrintStream logger) {
        super(logger);
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
            node.isDirty() &&
            node.isLeaf() &&
            parseException == null &&
            illegalArgumentException == null
        ) {
            ret.add(new Diagnostic(node.getRange(), "Dirty Node"));
        }

        return ret;
    }
}
