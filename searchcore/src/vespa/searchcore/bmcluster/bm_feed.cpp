// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_feed.h"
#include "bm_feed_operation.h"
#include "bm_feed_params.h"
#include "bm_range.h"
#include "bucket_selector.h"
#include "i_bm_feed_handler.h"
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <cassert>
#include <chrono>

#include <vespa/log/log.h>
LOG_SETUP(".bmcluster.bm_feed");

using document::AssignValueUpdate;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::DocumentUpdate;
using document::IntFieldValue;
using document::FieldUpdate;
using vespalib::makeLambdaTask;

namespace search::bmcluster {

BmFeed::BmFeed(std::shared_ptr<const DocumentTypeRepo> repo)
    : _repo(std::move(repo)),
      _document_type(_repo->getDocumentType("test")),
      _field(_document_type->getField("int")),
      _bucket_bits(16),
      _bucket_space(document::test::makeBucketSpace("test"))
{
}

BmFeed::~BmFeed()
{
}

DocumentId
BmFeed::make_document_id(uint32_t n, uint32_t i) const
{
    DocumentId id(vespalib::make_string("id::test:n=%u:%u", n & (num_buckets() - 1), i));
    return id;
}

std::unique_ptr<Document>
BmFeed::make_document(uint32_t n, uint32_t i) const
{
    auto id = make_document_id(n, i);
    auto document = std::make_unique<Document>(*_repo, *_document_type, id);
    document->setFieldValue(_field, IntFieldValue::make(i));
    return document;
}

std::unique_ptr<DocumentUpdate>
BmFeed::make_document_update(uint32_t n, uint32_t i) const
{
    auto id = make_document_id(n, i);
    auto document_update = std::make_unique<DocumentUpdate>(*_repo, *_document_type, id);
    document_update->addUpdate(FieldUpdate(_field).addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<IntFieldValue>(15))));
    return document_update;
}

vespalib::nbostream
BmFeed::make_put_feed(BmRange range, BucketSelector bucket_selector)
{
    vespalib::nbostream serialized_feed;
    LOG(debug, "make_put_feed([%u..%u))", range.get_start(), range.get_end());
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto n = bucket_selector(i);
        serialized_feed << static_cast<uint8_t>(BmFeedOperation::PUT_OPERATION);
        serialized_feed << make_bucket_id(n);
        auto document = make_document(n, i);
        document->serialize(serialized_feed);
    }
    return serialized_feed;
}

vespalib::nbostream
BmFeed::make_update_feed(BmRange range, BucketSelector bucket_selector)
{
    vespalib::nbostream serialized_feed;
    LOG(debug, "make_update_feed([%u..%u))", range.get_start(), range.get_end());
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto n = bucket_selector(i);
        serialized_feed << static_cast<uint8_t>(BmFeedOperation::UPDATE_OPERATION);
        serialized_feed << make_bucket_id(n);
        auto document_update = make_document_update(n, i);
        document_update->serializeHEAD(serialized_feed);
    }
    return serialized_feed;
}

vespalib::nbostream
BmFeed::make_get_or_remove_feed(BmRange range, BucketSelector bucket_selector, bool make_removes)
{
    vespalib::nbostream serialized_feed;
    BmFeedOperation operation(make_removes ? BmFeedOperation::REMOVE_OPERATION : BmFeedOperation::GET_OPERATION);
    if (make_removes) {
        LOG(debug, "make_remove_feed([%u..%u))", range.get_start(), range.get_end());
    } else {
        LOG(debug, "make_get_feed([%u..%u))", range.get_start(), range.get_end());
    }
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto n = bucket_selector(i);
        serialized_feed << static_cast<uint8_t>(operation);
        serialized_feed << make_bucket_id(n);
        auto document_id = make_document_id(n, i);
        vespalib::string raw_id = document_id.toString();
        serialized_feed.write(raw_id.c_str(), raw_id.size() + 1);
    }
    return serialized_feed;
}

vespalib::nbostream
BmFeed::make_get_feed(BmRange range, BucketSelector bucket_selector)
{
    return make_get_or_remove_feed(range, bucket_selector, false);
}

vespalib::nbostream
BmFeed::make_remove_feed(BmRange range, BucketSelector bucket_selector)
{
    return make_get_or_remove_feed(range, bucket_selector, true);
}

std::vector<vespalib::nbostream>
BmFeed::make_feed(vespalib::ThreadStackExecutor& executor, const BmFeedParams& params, std::function<vespalib::nbostream(BmRange,BucketSelector)> func, uint32_t num_buckets, const vespalib::string &label)
{
    LOG(info, "make_feed %s %u small documents", label.c_str(), params.get_documents());
    std::vector<vespalib::nbostream> serialized_feed_v;
    auto start_time = std::chrono::steady_clock::now();
    serialized_feed_v.resize(params.get_client_threads());
    for (uint32_t i = 0; i < params.get_client_threads(); ++i) {
        auto range = params.get_range(i);
        BucketSelector bucket_selector(i, params.get_client_threads(), num_buckets);
        executor.execute(makeLambdaTask([&serialized_feed_v, i, range, &func, bucket_selector]()
                                        { serialized_feed_v[i] = func(range, bucket_selector); }));
    }
    executor.sync();
    auto end_time = std::chrono::steady_clock::now();
    std::chrono::duration<double> elapsed = end_time - start_time;
    LOG(info, "%8.2f %s data elements/s", params.get_documents() / elapsed.count(), label.c_str());
    return serialized_feed_v;
}

}
