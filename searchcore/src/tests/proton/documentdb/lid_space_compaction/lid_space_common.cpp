// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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


void
MyStorer::appendOperation(const FeedOperation &op, DoneCallback) {
    if (op.getType() == FeedOperation::MOVE) {
        ++ _moveCnt;
    } else if (op.getType() == FeedOperation::COMPACT_LID_SPACE) {
        ++_compactCnt;
    }
}

IOperationStorer::CommitResult
MyStorer::startCommit(DoneCallback) {
    return CommitResult();
}

IFrozenBucketHandler::ExclusiveBucketGuard::UP
MyFrozenBucketHandler::acquireExclusiveBucket(BucketId bucket) {
    return (_bucket == bucket)
           ? ExclusiveBucketGuard::UP()
           : std::make_unique<ExclusiveBucketGuard>(bucket);
}

MyDocumentStore::MyDocumentStore()
    : _readDoc(),
      _readLid(0)
{}

MyDocumentStore::~MyDocumentStore() = default;

document::Document::UP
MyDocumentStore::read(search::DocumentIdT lid, const document::DocumentTypeRepo &) const {
    _readLid = lid;
    return Document::UP(_readDoc->clone());
}

MyDocumentRetriever::MyDocumentRetriever(std::shared_ptr<const DocumentTypeRepo> repo_in, const MyDocumentStore& store_in) noexcept
    : repo(std::move(repo_in)),
      store(store_in)
{}

MyDocumentRetriever::~MyDocumentRetriever() = default;

const document::DocumentTypeRepo&
MyDocumentRetriever::getDocumentTypeRepo() const {
    return *repo;
}

void
MyDocumentRetriever::getBucketMetaData(const storage::spi::Bucket&, DocumentMetaData::Vector&) const {
    abort();
}

DocumentMetaData
MyDocumentRetriever::getDocumentMetaData(const DocumentId&) const {
    abort();
}

Document::UP
MyDocumentRetriever::getFullDocument(DocumentIdT lid) const {
    return store.read(lid, *repo);
}

CachedSelect::SP
MyDocumentRetriever::parseSelect(const vespalib::string&) const {
    abort();
}

MySubDb::MySubDb(std::shared_ptr<BucketDBOwner> bucket_db, const MyDocumentStore& store, const std::shared_ptr<const DocumentTypeRepo> & repo)
    : sub_db(std::move(bucket_db), SUBDB_ID),
      maintenance_sub_db(sub_db.getName(), sub_db.getSubDbId(), sub_db.getDocumentMetaStoreContext().getSP(),
                         std::make_shared<MyDocumentRetriever>(repo, store),
                         std::make_shared<MyFeedView>(repo),
                         &_pendingLidsForCommit)
{
}

MySubDb::~MySubDb() = default;
