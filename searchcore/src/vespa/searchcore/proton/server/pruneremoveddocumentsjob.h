// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "blockable_maintenance_job.h"
#include "document_db_maintenance_config.h"
#include <persistence/spi/types.h>

namespace proton {

struct IDocumentMetaStore;
class IPruneRemovedDocumentsHandler;
class IFrozenBucketHandler;

/**
 * Job that regularly checks whether old removed documents should be
 * forgotten.
 */
class PruneRemovedDocumentsJob : public BlockableMaintenanceJob
{
private:
    const IDocumentMetaStore      &_metaStore;  // external ownership
    uint32_t                       _subDbId;
    vespalib::duration             _cfgAgeLimit;
    const vespalib::string        &_docTypeName;
    IPruneRemovedDocumentsHandler &_handler;
    IFrozenBucketHandler          &_frozenHandler;

    typedef uint32_t DocId;
    std::vector<DocId>             _pruneLids;
    DocId                          _nextLid;

    void flush(DocId lowLid, DocId nextLowLid, const storage::spi::Timestamp ageLimit);
public:
    using Config = DocumentDBPruneRemovedDocumentsConfig;

    PruneRemovedDocumentsJob(const Config &config,
                             const IDocumentMetaStore &metaStore,
                             uint32_t subDbId,
                             const vespalib::string &docTypeName,
                             IPruneRemovedDocumentsHandler &handler,
                             IFrozenBucketHandler &frozenHandler);

    // Implements IMaintenanceJob
    bool run() override;
};

} // namespace proton

