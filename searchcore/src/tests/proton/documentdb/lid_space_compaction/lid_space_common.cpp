// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_common.h"

MyScanIterator::MyScanIterator(const LidVector &lids, bool bucketIdEqualLid)
    : _lids(lids),
      _itr(_lids.begin()),
      _validItr(true),
      _bucketIdEqualLid(bucketIdEqualLid)
{}
MyScanIterator::~MyScanIterator() = default;

bool
MyScanIterator::valid() const {
    return _validItr;
}
search::DocumentMetaData MyScanIterator::next(uint32_t compactLidLimit, bool retry) {
    if (!retry && _itr != _lids.begin()) {
        ++_itr;
    }
    for (; _itr != _lids.end() && (*_itr) <= compactLidLimit; ++_itr) {}
    if (_itr != _lids.end()) {
        uint32_t lid = *_itr;
        if (lid > compactLidLimit) {
            return search::DocumentMetaData(lid, TIMESTAMP_1, createBucketId(lid), GID_1);
        }
    } else {
        _validItr = false;
    }
    return search::DocumentMetaData();
}
search::DocumentMetaData MyScanIterator::getMetaData(uint32_t lid) const {
    return search::DocumentMetaData(lid, TIMESTAMP_1, createBucketId(lid), GID_1);
}

document::BucketId
MyScanIterator::createBucketId(uint32_t lid) const {
    return _bucketIdEqualLid ? document::BucketId(lid) : BUCKET_ID_1;
}

void
MyHandler::clearMoveDoneContexts() {
    _moveDoneContexts.clear();
}
void
MyHandler::run_remove_ops(bool remove_batch) {
    // This ensures to max out the threshold time in the operation rate tracker.
    if (remove_batch) {
        _op_listener->notify_remove_batch();
        _op_listener->notify_remove_batch();
        _op_listener->notify_remove_batch();
    } else {
        _op_listener->notify_remove();
        _op_listener->notify_remove();
        _op_listener->notify_remove();
    }
}
void
MyHandler::stop_remove_ops(bool remove_batch) const {
    if (remove_batch) {
        _rm_listener->get_remove_batch_tracker().reset(vespalib::steady_clock::now());
    } else {
        _rm_listener->get_remove_tracker().reset(vespalib::steady_clock::now());
    }
}
vespalib::string
MyHandler::getName() const {
    return "myhandler";
}
void
MyHandler::set_operation_listener(documentmetastore::OperationListener::SP op_listener) {
    auto* rm_listener = dynamic_cast<RemoveOperationsRateTracker*>(op_listener.get());
    assert(rm_listener != nullptr);
    _op_listener = std::move(op_listener);
    _rm_listener = rm_listener;
}
LidUsageStats
MyHandler::getLidStatus() const {
    assert(_handleMoveCnt < _stats.size());
    return _stats[_handleMoveCnt];
}
IDocumentScanIterator::UP
MyHandler::getIterator() const {
    assert(_iteratorCnt < _lids.size());
    return std::make_unique<MyScanIterator>(_lids[_iteratorCnt++], _bucketIdEqualLid);
}
MoveOperation::UP
MyHandler::createMoveOperation(const search::DocumentMetaData &document, uint32_t moveToLid) const {
    assert(document.lid > moveToLid);
    _moveFromLid = document.lid;
    _moveToLid = moveToLid;
    return std::make_unique<MoveOperation>();
}
void
MyHandler::handleMove(const MoveOperation &, IDestructorCallback::SP moveDoneCtx) {
    ++_handleMoveCnt;
    if (_storeMoveDoneContexts) {
        _moveDoneContexts.push_back(std::move(moveDoneCtx));
    }
}
void
MyHandler::handleCompactLidSpace(const CompactLidSpaceOperation &op, std::shared_ptr<IDestructorCallback>) {
    _wantedLidLimit = op.getLidLimit();
}

MyHandler::MyHandler(bool storeMoveDoneContexts, bool bucketIdEqualLid)
    : _stats(),
      _moveFromLid(0),
      _moveToLid(0),
      _handleMoveCnt(0),
      _wantedLidLimit(0),
      _iteratorCnt(0),
      _storeMoveDoneContexts(storeMoveDoneContexts),
      _bucketIdEqualLid(bucketIdEqualLid),
      _moveDoneContexts(),
      _op_listener(),
      _rm_listener()
{}

MyHandler::~MyHandler() = default;

#if 0
struct MyStorer : public IOperationStorer {
    uint32_t _moveCnt;
    uint32_t _compactCnt;
    MyStorer()
        : _moveCnt(0),
          _compactCnt(0)
    {}
    void appendOperation(const FeedOperation &op, DoneCallback) override {
        if (op.getType() == FeedOperation::MOVE) {
            ++ _moveCnt;
        } else if (op.getType() == FeedOperation::COMPACT_LID_SPACE) {
            ++_compactCnt;
        }
    }
    CommitResult startCommit(DoneCallback) override {
        return CommitResult();
    }
};

struct MyFrozenBucketHandler : public IFrozenBucketHandler {
    BucketId _bucket;
    MyFrozenBucketHandler() : _bucket() {}
    ExclusiveBucketGuard::UP acquireExclusiveBucket(BucketId bucket) override {
        return (_bucket == bucket)
               ? ExclusiveBucketGuard::UP()
               : std::make_unique<ExclusiveBucketGuard>(bucket);
    }
    void addListener(IBucketFreezeListener *) override { }
    void removeListener(IBucketFreezeListener *) override { }
};

struct MyFeedView : public test::DummyFeedView {
    explicit MyFeedView(std::shared_ptr<const DocumentTypeRepo> repo)
        : test::DummyFeedView(std::move(repo))
    {
    }
};

struct MyDocumentStore : public test::DummyDocumentStore {
    Document::SP _readDoc;
    mutable uint32_t _readLid;
    MyDocumentStore() : _readDoc(), _readLid(0) {}
    ~MyDocumentStore() override;
    document::Document::UP read(search::DocumentIdT lid, const document::DocumentTypeRepo &) const override {
        _readLid = lid;
        return Document::UP(_readDoc->clone());
    }
};
#endif

MyDocumentStore::~MyDocumentStore() = default;

#if 0
struct MyDocumentRetriever : public DocumentRetrieverBaseForTest {
    std::shared_ptr<const DocumentTypeRepo> repo;
    const MyDocumentStore& store;
    MyDocumentRetriever(std::shared_ptr<const DocumentTypeRepo> repo_in, const MyDocumentStore& store_in) noexcept
        : repo(std::move(repo_in)),
          store(store_in)
    {}
    const document::DocumentTypeRepo& getDocumentTypeRepo() const override { return *repo; }
    void getBucketMetaData(const storage::spi::Bucket&, DocumentMetaData::Vector&) const override { abort(); }
    DocumentMetaData getDocumentMetaData(const DocumentId&) const override { abort(); }
    Document::UP getFullDocument(DocumentIdT lid) const override {
        return store.read(lid, *repo);
    }
    CachedSelect::SP parseSelect(const vespalib::string&) const override { abort(); }
};

#endif

MySubDb::MySubDb(std::shared_ptr<BucketDBOwner> bucket_db, const MyDocumentStore& store, const std::shared_ptr<const DocumentTypeRepo> & repo)
    : sub_db(std::move(bucket_db), SUBDB_ID),
      maintenance_sub_db(sub_db.getName(), sub_db.getSubDbId(), sub_db.getDocumentMetaStoreContext().getSP(),
                         std::make_shared<MyDocumentRetriever>(repo, store),
                         std::make_shared<MyFeedView>(repo),
                         &_pendingLidsForCommit)
{
}

MySubDb::~MySubDb() = default;

#if 0
struct MyDirectJobRunner : public IMaintenanceJobRunner {
    IMaintenanceJob &_job;
    explicit MyDirectJobRunner(IMaintenanceJob &job)
        : _job(job)
    {
        _job.registerRunner(this);
    }
    void run() override { _job.run(); }
};

struct MyCountJobRunner : public IMaintenanceJobRunner {
    uint32_t runCnt;
    explicit MyCountJobRunner(IMaintenanceJob &job) : runCnt(0) {
        job.registerRunner(this);
    }
    void run() override { ++runCnt; }
};
#endif
