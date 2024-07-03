package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyDiryNodes extends Identifier {


    public IdentifyDiryNodes(PrintStream logger) {
        super(logger);
    }

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (node.isDirty() && node.isLeaf()) {
            ret.add(new Diagnostic(node.getRange(), "Dirty Node"));
        }

        return ret;
    }
}
