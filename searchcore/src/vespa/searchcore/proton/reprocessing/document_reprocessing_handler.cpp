// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.reprocessing.document_reprocessing_handler");

#include "document_reprocessing_handler.h"

namespace proton {

void
DocumentReprocessingHandler::rewriteVisit(uint32_t lid, document::Document &doc)
{
    if (lid == 0 || lid >= _docIdLimit)
        return;
    for (const auto &reader : _readers) {
        reader->handleExisting(lid, doc);
    }
    for (const auto &rewriter : _rewriters) {
        rewriter->handleExisting(lid, doc);
    }
}

DocumentReprocessingHandler::DocumentReprocessingHandler(uint32_t docIdLimit)
    : _readers(),
      _rewriters(),
      _rewriteVisitor(*this),
      _docIdLimit(docIdLimit)
{
}

void
DocumentReprocessingHandler::visit(uint32_t lid, const document::Document &doc)
{
    if (lid == 0 || lid >= _docIdLimit)
        return;
    for (const auto &reader : _readers) {
        reader->handleExisting(lid, doc);
    }
}

void
DocumentReprocessingHandler::visit(uint32_t lid)
{
    (void) lid;
}

void
DocumentReprocessingHandler::done()
{
    for (const auto &reader : _readers) {
        reader->done();
    }
}

} // namespace proton
