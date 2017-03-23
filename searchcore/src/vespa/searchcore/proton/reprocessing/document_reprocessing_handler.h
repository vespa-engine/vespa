// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_reprocessing_handler.h"
#include <vespa/searchlib/docstore/idocumentstore.h>

namespace proton {

/**
 * Class that is a visitor over a document store and proxies all documents
 * to the registered readers and rewriters upon visiting.
 */
class DocumentReprocessingHandler : public IReprocessingHandler,
                                    public search::IDocumentStoreReadVisitor
{
private:
    typedef std::vector<IReprocessingReader::SP> ReaderVector;
    typedef std::vector<IReprocessingRewriter::SP> RewriterVector;

    class RewriteVisitor : public search::IDocumentStoreRewriteVisitor
    {
    private:
        DocumentReprocessingHandler &_handler;
    public:
        RewriteVisitor(DocumentReprocessingHandler &handler) : _handler(handler) {}
        // Implements search::IDocumentStoreRewriteVisitor
        virtual void visit(uint32_t lid, document::Document &doc) {
            _handler.rewriteVisit(lid, doc);
        }
    };

    ReaderVector _readers;
    RewriterVector _rewriters;
    RewriteVisitor _rewriteVisitor;
    uint32_t       _docIdLimit;

    void rewriteVisit(uint32_t lid, document::Document &doc);

public:
    DocumentReprocessingHandler(uint32_t docIdLimit);

    bool hasReaders() const {
        return !_readers.empty();
    }

    bool hasRewriters() const {
        return !_rewriters.empty();
    }

    bool hasProcessors() const {
        return hasReaders() || hasRewriters();
    }

    search::IDocumentStoreRewriteVisitor &getRewriteVisitor() {
        return _rewriteVisitor;
    }

    // Implements IReprocessingHandler
    virtual void addReader(const IReprocessingReader::SP &reader) {
        _readers.push_back(reader);
    }

    virtual void addRewriter(const IReprocessingRewriter::SP &rewriter) {
        _rewriters.push_back(rewriter);
    }

    // Implements search::IDocumentStoreReadVisitor
    virtual void visit(uint32_t lid, const document::Document &doc);

    virtual void visit(uint32_t lid);

    void done();
};

} // namespace proton

