// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_document_sub_db.h"

#include <vespa/searchcorespi/common/resource_usage.h>

using searchcorespi::common::ResourceUsage;

namespace proton::test {

namespace {

auto dummy_doc_type_and_subdb_name(DocTypeAndSubDbName::create(DocTypeName("dummy_doc_type_name"), "dummysubdb"));

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

const std::shared_ptr<const DocTypeAndSubDbName>& DummyDocumentSubDb::doc_type_and_subdb_name() const noexcept {
    return dummy_doc_type_and_subdb_name;
}

} // namespace proton::test
