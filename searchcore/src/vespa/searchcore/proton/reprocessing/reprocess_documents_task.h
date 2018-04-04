// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_reprocessing_task.h"
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include "attribute_reprocessing_initializer.h"
#include "document_reprocessing_handler.h"
#include "i_reprocessing_initializer.h"

namespace proton
{

/**
 * The reprocessing documents task handles reprocessing of documents,
 * e.g. populate attributes from document store when adding attribute
 * aspect on existing field and populating documents in document store
 * when removing attribute aspect on existing field.
 */
class ReprocessDocumentsTask : public IReprocessingTask,
                               public search::IDocumentStoreVisitorProgress
{
    proton::ISummaryManager::SP          _sm;
    std::shared_ptr<const document::DocumentTypeRepo>       _docTypeRepo;
    vespalib::string                     _subDbName;
    double                               _visitorProgress;
    double                               _visitorCost;
    DocumentReprocessingHandler          _handler;
    int64_t                              _startTime;
    double                               _loggedProgress;
    int64_t                              _loggedTime;

public:
    ReprocessDocumentsTask(IReprocessingInitializer &initializer,
                           const proton::ISummaryManager::SP &sm,
                           const std::shared_ptr<const document::DocumentTypeRepo> &docTypeRepo,
                           const vespalib::string &subDbName,
                           uint32_t docIdLimit);

    virtual void
    run() override;

    virtual void
    updateProgress(double progress) override;

    virtual Progress getProgress() const override;
};

} // namespace proton

