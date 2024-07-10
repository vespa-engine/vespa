package ai.vespa.schemals.workspaceEdit;

import java.util.ArrayList;

import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;

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

    public TextDocumentEdit exportTextDocumentEdit() {
        return new TextDocumentEdit(versionedTextDocumentIdentifier, textEdits);
    }

    public WorkspaceEdit exportWorkspaceEdit() {
        SchemaWorkspaceEdit ret = new SchemaWorkspaceEdit();
        ret.addTextDocumentEdit(this);
        return ret.exportEdits();
    }

    public String toString() {
        return "DocumentEdits(" + versionedTextDocumentIdentifier.getUri() + " : " + versionedTextDocumentIdentifier.getVersion() + " : " + textEdits.size();
    }
}
