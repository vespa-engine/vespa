package ai.vespa.schemals.schemadocument.parser.yqlplus;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.yqlplus.ast.identifierStr;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.YQLNode;

public class IdentifySymbolReferences extends Identifier<YQLNode> {

    public IdentifySymbolReferences(ParseContext context) {
        super(context);
    }

    public List<Diagnostic> identify(YQLNode node) {

        if (node.getASTClass() == identifierStr.class || node.getASTClass() == ai.vespa.schemals.parser.grouping.ast.identifierStr.class) {
            context.logger().info("TODO");
        }

        return new ArrayList<>();
    }

}