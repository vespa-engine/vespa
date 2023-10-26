// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "document_subdb_initializer.h"

namespace proton {

/**
 * Class used to initialize a collection of document sub databases.
 */
class DocumentSubDbCollectionInitializer : public initializer::InitializerTask
{
private:
    std::vector<DocumentSubDbInitializer::SP> _subDbInitializers;

public:
    using SP = std::shared_ptr<DocumentSubDbCollectionInitializer>;

    DocumentSubDbCollectionInitializer();
    void add(const DocumentSubDbInitializer::SP subDbInitializer) {
        _subDbInitializers.push_back(subDbInitializer);
        addDependency(subDbInitializer);
    }
    virtual void run() override;
};

} // namespace proton

