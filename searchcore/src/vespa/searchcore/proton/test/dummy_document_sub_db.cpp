// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_document_sub_db.h"

#include <vespa/searchcorespi/common/resource_usage.h>

using searchcorespi::common::ResourceUsage;

namespace proton::test {

namespace {

const DocTypeName dummy_doc_type_name("dummy_doc_type_name");

}

DummyDocumentSubDb::DummyDocumentSubDb(std::shared_ptr<bucketdb::BucketDBOwner> bucketDB, uint32_t subDbId)
    : _subDbId(subDbId),
      _metaStoreCtx(std::move(bucketDB)),
      _summaryManager(),
      _indexManager(),
      _summaryAdapter(),
      _indexWriter(),
      _service(1),
      _pendingLidTracker() {
}

DummyDocumentSubDb::~DummyDocumentSubDb() = default;

ResourceUsage DummyDocumentSubDb::get_resource_usage() const {
    return {};
}

const DocTypeName& DummyDocumentSubDb::doc_type_name() const noexcept {
    return dummy_doc_type_name;
}

} // namespace proton::test
