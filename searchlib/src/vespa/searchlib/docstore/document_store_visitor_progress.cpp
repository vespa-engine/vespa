// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_store_visitor_progress.h"

namespace search {

DocumentStoreVisitorProgress::DocumentStoreVisitorProgress()
    : search::IDocumentStoreVisitorProgress(),
      _progress(0.0)
{
}

void
DocumentStoreVisitorProgress::updateProgress(double progress)
{
    _progress = progress;
}

double
DocumentStoreVisitorProgress::getProgress() const
{
    return _progress;
}

}
