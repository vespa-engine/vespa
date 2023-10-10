// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton
{

class PruneRemovedDocumentsOperation;

class IPruneRemovedDocumentsHandler
{
public:
    virtual void
    performPruneRemovedDocuments(PruneRemovedDocumentsOperation &pruneOp) = 0;

    virtual
    ~IPruneRemovedDocumentsHandler()
    {
    }
};

} // namespace proton

