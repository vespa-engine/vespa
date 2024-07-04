package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.Token.ParseExceptionSource;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyDiryNodes extends Identifier {


    public IdentifyDiryNodes(PrintStream logger) {
        super(logger);
    }

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        ParseExceptionSource exception = node.getParseExceptionSource();
        if (exception != null) {
            TokenSource tokenSource = node.getTokenSource();
            Range range = CSTUtils.getRangeFromOffsets(tokenSource, exception.beginOffset, exception.endOffset);
            ret.add(new Diagnostic(range, exception.parseException.getMessage()));

        } else if (node.isDirty() && node.isLeaf()) {

            ret.add(new Diagnostic(node.getRange(), "Dirty Node"));
        }

        return ret;
    }
}
