package ai.vespa.schemals.lsp.codeaction.utils;

import java.util.List;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

import ai.vespa.schemals.common.editbuilder.WorkspaceEditBuilder;
import ai.vespa.schemals.context.EventCodeActionContext;
import ai.vespa.schemals.schemadocument.DocumentManager;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * CodeActionUtils
 */
public class CodeActionUtils {
    public static WorkspaceEdit simpleEditList(EventCodeActionContext context, List<TextEdit> edits, DocumentManager document) {
        return new WorkspaceEditBuilder()
            .addTextEdits(document.getVersionedTextDocumentIdentifier(), edits)
            .build();
    }

    public static WorkspaceEdit simpleEditList(EventCodeActionContext context, List<TextEdit> edits) {
        return simpleEditList(context, edits, context.document);
    }

    public static WorkspaceEdit simpleEdit(EventCodeActionContext context, Position position, String newText) {
        return simpleEdit(context, new Range(position, position), newText);
    }

    public static WorkspaceEdit simpleEdit(EventCodeActionContext context, Range range, String newText) {
        return simpleEditList(context, List.of(new TextEdit(range, newText)));
    }

    public static WorkspaceEdit simpleEdit(EventCodeActionContext context, Range range, String newText, DocumentManager document) {
        return simpleEditList(context, List.of(new TextEdit(range, newText)), document);
    }

    public static WorkspaceEdit createInheritsEdit(EventCodeActionContext context, SchemaNode identifierNode, Class<?> inheritsASTClass, String toInherit) {
        if (identifierNode.getNextSibling() != null && identifierNode.getNextSibling().isASTInstance(inheritsASTClass)) {
            Position insertPosition = identifierNode.getNextSibling().getRange().getEnd();
            return simpleEdit(context, new Range(insertPosition, insertPosition), ", " + toInherit);
        } else {
            Position insertPosition = identifierNode.getRange().getEnd();
            return simpleEdit(context, new Range(insertPosition, insertPosition), " inherits " + toInherit);
        }
    }

}
