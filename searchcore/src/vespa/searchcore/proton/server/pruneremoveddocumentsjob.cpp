// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "pruneremoveddocumentsjob.h"
#include "ipruneremoveddocumentshandler.h"
#include <vespa/persistence/spi/bucket_tasks.h>
#include <vespa/searchcore/proton/feedoperation/pruneremoveddocumentsoperation.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.pruneremoveddocumentsjob");

using document::BucketId;
using storage::spi::Timestamp;
using storage::spi::Bucket;
using vespalib::IDestructorCallback;
using vespalib::RetainGuard;
using vespalib::makeLambdaTask;

namespace proton {

PruneRemovedDocumentsJob::
PruneRemovedDocumentsJob(const DocumentDBPruneConfig &config, RetainGuard dbRetainer, const IDocumentMetaStore &metaStore,
                         uint32_t subDbId, document::BucketSpace bucketSpace, const vespalib::string &docTypeName,
                         IPruneRemovedDocumentsHandler &handler, IThreadService & master,
                         BucketExecutor & bucketExecutor)
    : BlockableMaintenanceJob("prune_removed_documents." + docTypeName,
                              config.getDelay(), config.getInterval()),
      _metaStore(metaStore),
      _handler(handler),
      _master(master),
      _bucketExecutor(bucketExecutor),
      _docTypeName(docTypeName),
      _dbRetainer(std::move(dbRetainer)),
      _cfgAgeLimit(config.getAge()),
      _subDbId(subDbId),
      _bucketSpace(bucketSpace),
      _nextLid(1u)
{
}

class PruneRemovedDocumentsJob::PruneTask : public storage::spi::BucketTask {
public:
    PruneTask(std::shared_ptr<PruneRemovedDocumentsJob> job, uint32_t lid, const RawDocumentMetaData & meta, IDestructorCallback::SP opsTracker)
        : _job(std::move(job)),
          _lid(lid),
          _meta(meta),
          _opsTracker(std::move(opsTracker))
    { }
    void run(const Bucket & bucket, IDestructorCallback::SP onDone) override;
    void fail(const Bucket & bucket) override {
        assert(bucket.getBucketId() == _meta.getBucketId());
    }
private:
    std::shared_ptr<PruneRemovedDocumentsJob> _job;
    uint32_t                                    _lid;
    const RawDocumentMetaData                   _meta;
    IDestructorCallback::SP                     _opsTracker;
};

void
PruneRemovedDocumentsJob::PruneTask::run(const Bucket & bucket, IDestructorCallback::SP onDone) {
    assert(bucket.getBucketId() == _meta.getBucketId());
    using DoneContext = vespalib::KeepAlive<std::pair<IDestructorCallback::SP, IDestructorCallback::SP>>;
    auto & job = *_job;
    job._master.execute(makeLambdaTask([job = std::move(_job), lid=_lid, meta = _meta,
                                               onDone = std::make_shared<DoneContext>(std::make_pair(std::move(_opsTracker), std::move(onDone)))
                                       ]() {
        (void) onDone;
        job->remove(lid, meta);
    }));
}

void
PruneRemovedDocumentsJob::remove(uint32_t lid, const RawDocumentMetaData & oldMeta) {
    if (stopped()) return;
    if ( ! _metaStore.validLid(lid)) return;
    const RawDocumentMetaData &meta = _metaStore.getRawMetaData(lid);
    if (meta.getBucketId() != oldMeta.getBucketId()) return;
    if (meta.getTimestamp() != oldMeta.getTimestamp()) return;
    if (meta.getGid() != oldMeta.getGid()) return;

    PruneRemovedDocumentsOperation pruneOp(_metaStore.getCommittedDocIdLimit(), _subDbId);
    pruneOp.getLidsToRemove()->addLid(lid);
    _handler.performPruneRemovedDocuments(pruneOp);
}

bool
PruneRemovedDocumentsJob::run()
{
    vespalib::system_time now = vespalib::system_clock::now();
    const Timestamp ageLimit(static_cast<Timestamp::Type>
                             (vespalib::count_us(now.time_since_epoch() - _cfgAgeLimit)));
    const DocId docIdLimit(_metaStore.getCommittedDocIdLimit());
    const DocId lidLimit = std::min(_nextLid + 1000000u, docIdLimit);
    for (; ! isBlocked() && _nextLid < lidLimit; _nextLid++) {
        if ( ! _metaStore.validLid(_nextLid)) continue;
        const RawDocumentMetaData &meta = _metaStore.getRawMetaData(_nextLid);
        if (meta.getTimestamp() >= ageLimit) continue;

        _bucketExecutor.execute(Bucket(document::Bucket(_bucketSpace, meta.getBucketId())),
                                std::make_unique<PruneTask>(shared_from_this(), _nextLid, meta, getLimiter().beginOperation()));
    }
    if (_nextLid >= docIdLimit) {
        _nextLid = 1u;
        return true;
    }
    return false;
}

} // namespace proton
