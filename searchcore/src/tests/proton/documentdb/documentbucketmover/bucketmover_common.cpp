// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmover_common.h"
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/vespalib/testkit/test_macros.h>

using vespalib::IDestructorCallback;

namespace proton::move::test {

MyBucketModifiedHandler::~MyBucketModifiedHandler() = default;

void
MyBucketModifiedHandler::notifyBucketModified(const BucketId &bucket) {
    auto itr = std::find(_modified.begin(), _modified.end(), bucket);
    ASSERT_TRUE(itr == _modified.end());
    _modified.push_back(bucket);
}

MyMoveHandler::MyMoveHandler(bucketdb::BucketDBOwner &bucketDb, bool storeMoveDoneContext)
    : _bucketDb(bucketDb),
      _moves(),
      _numCachedBuckets(),
      _storeMoveDoneContexts(storeMoveDoneContext),
      _moveDoneContexts()
{}

MyMoveHandler::~MyMoveHandler() = default;

void
MyMoveHandler::handleMove(MoveOperation &op, IDestructorCallback::SP moveDoneCtx) {
    _moves.push_back(op);
    if (_bucketDb.takeGuard()->isCachedBucket(op.getBucketId())) {
        ++_numCachedBuckets;
    }
    if (_storeMoveDoneContexts) {
        _moveDoneContexts.push_back(std::move(moveDoneCtx));
    }
}

MySubDb::MySubDb(const std::shared_ptr<const DocumentTypeRepo> &repo, std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                 uint32_t subDbId, SubDbType subDbType)
    : _metaStoreSP(std::make_shared<DocumentMetaStore>(bucketDB, DocumentMetaStore::getFixedName(),
                                                       search::GrowStrategy(), subDbType)),
      _metaStore(*_metaStoreSP),
      _realRetriever(std::make_shared<MyDocumentRetriever>(repo)),
      _retriever(_realRetriever),
      _subDb("my_sub_db", subDbId, _metaStoreSP, _retriever, IFeedView::SP(), nullptr),
      _docs(),
      _bucketDBHandler(*bucketDB)
{
    _bucketDBHandler.addDocumentMetaStore(_metaStoreSP.get(), 0);
}

MySubDb::~MySubDb() = default;

void
MySubDb::insertDocs(const UserDocuments &docs_) {
    for (const auto & entry : docs_) {
        const auto & bucketDocs = entry.second;
        for (size_t i = 0; i < bucketDocs.getDocs().size(); ++i) {
            const auto & testDoc = bucketDocs.getDocs()[i];
            _metaStore.put(testDoc.getGid(), testDoc.getBucket(),
                           testDoc.getTimestamp(), testDoc.getDocSize(), testDoc.getLid(), 0u);
            _realRetriever->_docs.push_back(testDoc.getDoc());
            ASSERT_EQUAL(testDoc.getLid() + 1, _realRetriever->_docs.size());
        }
    }
    _docs.merge(docs_);
}

bool
assertEqual(const document::BucketId &bucket, const proton::test::Document &doc,
            uint32_t sourceSubDbId, uint32_t targetSubDbId, const MoveOperation &op) {
    if (!EXPECT_EQUAL(bucket, op.getBucketId())) return false;
    if (!EXPECT_EQUAL(doc.getTimestamp(), op.getTimestamp())) return false;
    if (!EXPECT_EQUAL(doc.getDocId(), op.getDocument()->getId())) return false;
    if (!EXPECT_EQUAL(doc.getLid(), op.getSourceDbdId().getLid())) return false;
    if (!EXPECT_EQUAL(sourceSubDbId, op.getSourceDbdId().getSubDbId())) return false;
    if (!EXPECT_EQUAL(0u, op.getTargetDbdId().getLid())) return false;
    if (!EXPECT_EQUAL(targetSubDbId, op.getTargetDbdId().getSubDbId())) return false;
    return true;
}

void
MySubDb::setBucketState(const BucketId &bucketId, bool active) {
    _metaStore.setBucketState(bucketId, active);
}

}
