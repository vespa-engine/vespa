package ai.vespa.schemals.lsp.yqlplus.codelens;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.context.EventDocumentContext;
import ai.vespa.schemals.lsp.common.command.CommandRegistry;
import ai.vespa.schemals.lsp.common.command.CommandRegistry.CommandType;
import ai.vespa.schemals.schemadocument.DocumentManager.DocumentType;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.YQLNode;

public class YQLPlusCodeLens {


    static public List<CodeLens> codeLens(EventDocumentContext context) {

        if (context.document.getDocumentType() != DocumentType.YQL) {
            return List.of();
        }

        YQLNode rootNode = context.document.getRootYQLNode();

        List<CodeLens> ret = new ArrayList<>();

        for (Node child : rootNode) {
            if (child.size() == 0) continue;
            String command = child.getText();
            Range range = child.get(0).getRange();
            ret.add(new CodeLens(
                range,
                CommandRegistry.createLSPCommand(CommandType.RUN_VESPA_QUERY, List.of(command, context.document.getFileURI())),
                null
            ));
        }

        return ret;
    }
}
