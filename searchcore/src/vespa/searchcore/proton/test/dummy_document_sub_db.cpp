#include "dummy_document_sub_db.h"

namespace proton::test {

DummyDocumentSubDb::DummyDocumentSubDb(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB, uint32_t subDbId)
    : _subDbId(subDbId),
      _metaStoreCtx(std::move(bucketDB)),
      _summaryManager(),
      _indexManager(),
      _summaryAdapter(),
      _indexWriter(),
      _service(1),
      _pendingLidTracker()
{ }

DummyDocumentSubDb::~DummyDocumentSubDb() = default;

}
