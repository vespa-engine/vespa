package ai.vespa.schemals.common.editbuilder;

import java.util.ArrayList;

import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.common.FileUtils;

public class TextDocumentEditBuilder {
    private ArrayList<TextEdit> textEdits;
    private VersionedTextDocumentIdentifier versionedTextDocumentIdentifier;

    public TextDocumentEditBuilder() {
        this.versionedTextDocumentIdentifier = new VersionedTextDocumentIdentifier();
        textEdits = new ArrayList<>();
    }

    public TextDocumentEditBuilder setVersionedTextDocumentIdentifier(VersionedTextDocumentIdentifier identifier) {
        this.versionedTextDocumentIdentifier = identifier;
        return this;
    }

    public TextDocumentEditBuilder setFileURI(String fileURI) {
        this.versionedTextDocumentIdentifier.setUri(fileURI);
        return this;
    }

    public TextDocumentEditBuilder addEdit(TextEdit textEdit) {
        textEdits.add(textEdit);
        return this;
    }

    public String getFileURI() {
        return FileUtils.decodeURL(versionedTextDocumentIdentifier.getUri());
    }

    public TextDocumentEdit build() {
        return new TextDocumentEdit(versionedTextDocumentIdentifier, textEdits);
    }

    public String toString() {
        return "DocumentEdits(" + versionedTextDocumentIdentifier.getUri() + " : " + 
            (versionedTextDocumentIdentifier.getVersion() == null ? "" : versionedTextDocumentIdentifier.getVersion())
            + " : " + textEdits.size();
    }
}
