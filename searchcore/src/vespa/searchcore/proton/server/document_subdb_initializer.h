// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "document_subdb_initializer_result.h"
#include <vespa/searchcore/proton/initializer/initializer_task.h>

namespace searchcorespi { namespace index { struct IThreadService; } }

namespace proton {

class IDocumentSubDB;

/**
 * Class used to initialize a set of components that is used by a document sub database.
 *
 * The initialization of components will typically happen in parallel to reduce startup times.
 */
class DocumentSubDbInitializer : public initializer::InitializerTask
{
private:
    DocumentSubDbInitializerResult _result;
    initializer::InitializerTask::SP _documentMetaStoreInitTask;
    IDocumentSubDB                  &_subDB;
    searchcorespi::index::IThreadService &_master;

public:
    using SP = std::shared_ptr<DocumentSubDbInitializer>;
    using UP = std::unique_ptr<DocumentSubDbInitializer>;
    using InitTask = initializer::InitializerTask;

    DocumentSubDbInitializer(IDocumentSubDB &subDB,
                             searchcorespi::index::IThreadService &master);
    const DocumentSubDbInitializerResult &result() const {
        return _result;
    }

    DocumentSubDbInitializerResult &writableResult() {
        return _result;
    }

    void addDocumentMetaStoreInitTask(InitTask::SP documentMetaStoreInitTask);

    InitTask::SP getDocumentMetaStoreInitTask() const {
        return _documentMetaStoreInitTask;
    }

    void run() override;
};

} // namespace proton

