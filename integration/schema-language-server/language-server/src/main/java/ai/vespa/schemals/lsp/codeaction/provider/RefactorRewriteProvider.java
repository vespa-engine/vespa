package ai.vespa.schemals.lsp.codeaction.provider;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.CreateFileOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.common.editbuilder.WorkspaceEditBuilder;
import ai.vespa.schemals.context.EventCodeActionContext;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.rootSchemaItem;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * RefactorRewriteProvider
 */
public class RefactorRewriteProvider implements CodeActionProvider {

    private Optional<CodeAction> getMoveRankProfile(SchemaNode node, EventCodeActionContext context) {
        if (!(context.document instanceof SchemaDocument)) return Optional.empty();
        SchemaNode rankProfileNode = CSTUtils.findASTClassAncestor(node, rankProfile.class);
        if (rankProfileNode == null) return Optional.empty();

        String schemaName = ((SchemaDocument)context.document).getSchemaIdentifier();
        String rankProfileName = rankProfileNode.get(1).getText();

        URI schemaURI = URI.create(context.document.getFileURI());
        Path rankProfilePath = Path.of(schemaURI).resolveSibling(Path.of(schemaName, rankProfileName + ".profile"));

        String rankProfileURI = rankProfilePath.toUri().toString();
        Range rankProfileInsertPosition = new Range(new Position(0, 0), new Position(0, 0));
        String rankProfileText = StringUtils.addIndents(rankProfileNode.getText(), -rankProfileNode.getRange().getStart().getCharacter());

        WorkspaceEdit edit = new WorkspaceEditBuilder()
            .addResourceOperation(new CreateFile(rankProfileURI, new CreateFileOptions(false, true)))
            .addTextEdit(rankProfileURI, new TextEdit(rankProfileInsertPosition, rankProfileText))
            .addTextEdit(context.document.getVersionedTextDocumentIdentifier(), new TextEdit(rankProfileNode.getRange(), ""))
            .build();

        CodeAction action = new CodeAction();
        action.setTitle("Move '" + rankProfileName + "' to separate file");
        action.setKind(CodeActionKind.RefactorRewrite);
        action.setEdit(edit);
        return Optional.of(action);
    }

	@Override
	public List<Either<Command, CodeAction>> getActions(EventCodeActionContext context) {
        SchemaNode atPosition = CSTUtils.getNodeAtPosition(context.document.getRootNode(), context.position);
        List<Either<Command, CodeAction>> result = new ArrayList<>();

        getMoveRankProfile(atPosition, context).ifPresent(action -> result.add(Either.forRight(action)));

        return result;
	}
} 
