package com.yahoo.search.dispatch.searchcluster;

public class MockDocumentCountSource implements DocumentCountSource {
    private final DocumentCount documentCount;

    public MockDocumentCountSource() {
        this.documentCount = new DocumentCount();
    }
    public MockDocumentCountSource(DocumentCount documentCount) {
        this.documentCount = documentCount;
    }

    public DocumentCount getDocumentCount() {
        return documentCount;
    }
}
