package ai.vespa.schemals.common.editbuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;


/**
 * WorkspaceEditBuilder
 * Helper for creating a series of text edit operations across several files
 * Multiple operations within same file happens in order of adding them to the builder
 * Order of files edited is based on when the first edit to that file was added.
 * Manually control the ordering by registering the versioned document identifier.
 */
public class WorkspaceEditBuilder {

    ArrayList<Either<TextDocumentEditBuilder, ResourceOperation>> edits;
    Map<String, TextDocumentEditBuilder> textDocumentBuilders = new HashMap<>();

    public WorkspaceEditBuilder() {
        edits = new ArrayList<>();
    }

    public WorkspaceEditBuilder addTextEdit(String fileURI, TextEdit edit) {
        return addTextEdit(new VersionedTextDocumentIdentifier(fileURI, null), edit);
    }

    public WorkspaceEditBuilder addTextEdit(VersionedTextDocumentIdentifier identifier, TextEdit edit) {
        if (!textDocumentBuilders.containsKey(identifier.getUri())) {
            registerVersionedDocumentIdentifier(identifier);
        }
        textDocumentBuilders.get(identifier.getUri()).addEdit(edit);
        return this;
    }

    public WorkspaceEditBuilder addTextEdits(VersionedTextDocumentIdentifier identifier, Iterable<TextEdit> edits) {
        if (!textDocumentBuilders.containsKey(identifier.getUri())) {
            registerVersionedDocumentIdentifier(identifier);
        }
        TextDocumentEditBuilder builder = textDocumentBuilders.get(identifier.getUri());
        edits.forEach(edit -> builder.addEdit(edit));
        return this;
    }

    public WorkspaceEditBuilder addResourceOperation(ResourceOperation resourceOperation) {
        edits.add(Either.forRight(resourceOperation));
        return this;
    }

    public WorkspaceEditBuilder registerVersionedDocumentIdentifier(VersionedTextDocumentIdentifier identifier) {
        var builder = new TextDocumentEditBuilder().setVersionedTextDocumentIdentifier(identifier);
        textDocumentBuilders.put(identifier.getUri(), builder);
        edits.add(Either.forLeft(builder));
        return this;
    }

    public WorkspaceEdit build() {
        List<Either<TextDocumentEdit, ResourceOperation>> result = new ArrayList<>();

        for (var editEntry : this.edits) {
            if (editEntry.isLeft())result.add(
                    Either.forLeft(editEntry.getLeft().build()));
            else result.add(Either.forRight(editEntry.getRight()));
        }

        return new WorkspaceEdit(result);
    }
}
