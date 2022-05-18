// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "blockable_maintenance_job.h"
#include "document_db_maintenance_config.h"
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/vespalib/util/retain_guard.h>
#include <atomic>

namespace storage::spi { struct BucketExecutor; }
namespace searchcorespi::index { struct IThreadService; }

namespace proton {

struct IDocumentMetaStore;
class IPruneRemovedDocumentsHandler;
struct RawDocumentMetaData;

/**
 * Job that regularly checks whether old removed documents should be
 * forgotten.
 */
class PruneRemovedDocumentsJob : public BlockableMaintenanceJob,
                                 public std::enable_shared_from_this<PruneRemovedDocumentsJob>
{
private:
    class PruneTask;
    using Config = DocumentDBPruneRemovedDocumentsConfig;
    using BucketExecutor = storage::spi::BucketExecutor;
    using IThreadService = searchcorespi::index::IThreadService;
    using DocId = uint32_t;

    const IDocumentMetaStore      &_metaStore;  // external ownership
    IPruneRemovedDocumentsHandler &_handler;
    IThreadService                &_master;
    BucketExecutor                &_bucketExecutor;
    const vespalib::string         _docTypeName;
    vespalib::RetainGuard          _dbRetainer;
    const vespalib::duration       _cfgAgeLimit;
    const uint32_t                 _subDbId;
    const document::BucketSpace    _bucketSpace;

    DocId                          _nextLid;

    void remove(uint32_t lid, const RawDocumentMetaData & meta);

    PruneRemovedDocumentsJob(const DocumentDBPruneConfig &config, vespalib::RetainGuard dbRetainer, const IDocumentMetaStore &metaStore,
                             uint32_t subDbId, document::BucketSpace bucketSpace, const vespalib::string &docTypeName,
                             IPruneRemovedDocumentsHandler &handler, IThreadService & master,
                             BucketExecutor & bucketExecutor);
    bool run() override;
public:
    static std::shared_ptr<PruneRemovedDocumentsJob>
    create(const Config &config, vespalib::RetainGuard dbRetainer, const IDocumentMetaStore &metaStore, uint32_t subDbId,
           document::BucketSpace bucketSpace, const vespalib::string &docTypeName,
           IPruneRemovedDocumentsHandler &handler, IThreadService & master, BucketExecutor & bucketExecutor)
   {
        return std::shared_ptr<PruneRemovedDocumentsJob>(
                new PruneRemovedDocumentsJob(config, std::move(dbRetainer), metaStore, subDbId, bucketSpace,
                                             docTypeName, handler, master, bucketExecutor));
    }
};

} // namespace proton

