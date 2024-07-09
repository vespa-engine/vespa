package ai.vespa.schemals.workspaceEdit;

import java.util.ArrayList;

import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

public class SchemaTextDocumentEdit {
    private ArrayList<TextEdit> textEdits = new ArrayList<>();
    private VersionedTextDocumentIdentifier versionedTextDocumentIdentifier;

    public SchemaTextDocumentEdit(VersionedTextDocumentIdentifier versionedTextDocumentIdentifier) {
        this.versionedTextDocumentIdentifier = versionedTextDocumentIdentifier;
    }

    public void add(TextEdit textEdit) {
        textEdits.add(textEdit);
    }

    public TextDocumentEdit export() {
        return new TextDocumentEdit(versionedTextDocumentIdentifier, textEdits);
    }
}
