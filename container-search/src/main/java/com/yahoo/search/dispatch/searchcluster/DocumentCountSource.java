package com.yahoo.search.dispatch.searchcluster;

/**
 * Simple interface for accessing the document count of a search cluster.
 *
 * @author boeker
 */
public interface DocumentCountSource{
    DocumentCount getDocumentCount();
}
