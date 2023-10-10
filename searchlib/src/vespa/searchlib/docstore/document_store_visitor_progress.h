// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idocumentstore.h"

namespace search {

class DocumentStoreVisitorProgress : public IDocumentStoreVisitorProgress
{
    double _progress;
public:
    DocumentStoreVisitorProgress();
    void updateProgress(double progress) override;
    virtual double getProgress() const;
};

} // namespace proton

