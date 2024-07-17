package ai.vespa.schemals.workspaceEdit;

import java.util.ArrayList;

import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

public class SchemaTextDocumentEdit {
    private ArrayList<TextEdit> textEdits;
    private VersionedTextDocumentIdentifier versionedTextDocumentIdentifier;

    public SchemaTextDocumentEdit(VersionedTextDocumentIdentifier versionedTextDocumentIdentifier) {
        this.versionedTextDocumentIdentifier = versionedTextDocumentIdentifier;
        textEdits = new ArrayList<>();
    }

    public void add(TextEdit textEdit) {
        textEdits.add(textEdit);
    }

    public String getFileURI() {
        return versionedTextDocumentIdentifier.getUri();
    }

    public TextDocumentEdit exportTextDocumentEdit() {
        return new TextDocumentEdit(versionedTextDocumentIdentifier, textEdits);
    }

    public String toString() {
        return "DocumentEdits(" + versionedTextDocumentIdentifier.getUri() + " : " + versionedTextDocumentIdentifier.getVersion() + " : " + textEdits.size();
    }
}
