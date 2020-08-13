// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "commit_and_wait_document_retriever.h"

namespace proton {

CommitAndWaitDocumentRetriever::CommitAndWaitDocumentRetriever(IDocumentRetriever::SP retriever, ICommitable &commit)
    : _retriever(std::move(retriever)),
      _commit(commit)
{ }

CommitAndWaitDocumentRetriever::~CommitAndWaitDocumentRetriever() = default;

} // namespace proton
