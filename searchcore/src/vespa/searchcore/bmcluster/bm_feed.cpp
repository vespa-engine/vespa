// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_feed.h"
#include "bm_range.h"
#include "bucket_selector.h"
#include "pending_tracker.h"
#include "i_bm_feed_handler.h"
#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

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
    auto document = std::make_unique<Document>(*_document_type, id);
    document->setRepo(*_repo);
    document->setFieldValue(_field, std::make_unique<IntFieldValue>(i));
    return document;
}

std::unique_ptr<DocumentUpdate>
BmFeed::make_document_update(uint32_t n, uint32_t i) const
{
    auto id = make_document_id(n, i);
    auto document_update = std::make_unique<DocumentUpdate>(*_repo, *_document_type, id);
    document_update->addUpdate(FieldUpdate(_field).addUpdate(AssignValueUpdate(IntFieldValue(15))));
    return document_update;
}

vespalib::nbostream
BmFeed::make_put_feed(BmRange range, BucketSelector bucket_selector)
{
    vespalib::nbostream serialized_feed;
    LOG(debug, "make_put_feed([%u..%u))", range.get_start(), range.get_end());
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto n = bucket_selector(i);
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
        serialized_feed << make_bucket_id(n);
        auto document_update = make_document_update(n, i);
        document_update->serializeHEAD(serialized_feed);
    }
    return serialized_feed;
}

vespalib::nbostream
BmFeed::make_remove_feed(BmRange range, BucketSelector bucket_selector)
{
    vespalib::nbostream serialized_feed;
    LOG(debug, "make_remove_feed([%u..%u))", range.get_start(), range.get_end());
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        auto n = bucket_selector(i);
        serialized_feed << make_bucket_id(n);
        auto document_id = make_document_id(n, i);
        vespalib::string raw_id = document_id.toString();
        serialized_feed.write(raw_id.c_str(), raw_id.size() + 1);
    }
    return serialized_feed;
}


void
BmFeed::put_async_task(IBmFeedHandler& feed_handler, uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{
    LOG(debug, "put_async_task([%u..%u))", range.get_start(), range.get_end());
    PendingTracker pending_tracker(max_pending);
    feed_handler.attach_bucket_info_queue(pending_tracker);
    auto &repo = *_repo;
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    document::BucketId bucket_id;
    bool use_timestamp = !feed_handler.manages_timestamp();
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        auto document = std::make_unique<Document>(repo, is);
        feed_handler.put(bucket, std::move(document), (use_timestamp ? (time_bias + i) : 0), pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
BmFeed::update_async_task(IBmFeedHandler& feed_handler, uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{
    LOG(debug, "update_async_task([%u..%u))", range.get_start(), range.get_end());
    PendingTracker pending_tracker(max_pending);
    feed_handler.attach_bucket_info_queue(pending_tracker);
    auto &repo = *_repo;
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    document::BucketId bucket_id;
    bool use_timestamp = !feed_handler.manages_timestamp();
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        auto document_update = DocumentUpdate::createHEAD(repo, is);
        feed_handler.update(bucket, std::move(document_update), (use_timestamp ? (time_bias + i) : 0), pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
BmFeed::get_async_task(IBmFeedHandler& feed_handler, uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed)
{
    LOG(debug, "get_async_task([%u..%u))", range.get_start(), range.get_end());
    search::bmcluster::PendingTracker pending_tracker(max_pending);
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    document::BucketId bucket_id;
    vespalib::string all_fields(document::AllFields::NAME);
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        DocumentId document_id(is);
        feed_handler.get(bucket, all_fields, document_id, pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

void
BmFeed::remove_async_task(IBmFeedHandler& feed_handler, uint32_t max_pending, BmRange range, const vespalib::nbostream &serialized_feed, int64_t time_bias)
{
    LOG(debug, "remove_async_task([%u..%u))", range.get_start(), range.get_end());
    search::bmcluster::PendingTracker pending_tracker(max_pending);
    feed_handler.attach_bucket_info_queue(pending_tracker);
    vespalib::nbostream is(serialized_feed.data(), serialized_feed.size());
    document::BucketId bucket_id;
    bool use_timestamp = !feed_handler.manages_timestamp();
    for (unsigned int i = range.get_start(); i < range.get_end(); ++i) {
        is >> bucket_id;
        document::Bucket bucket(_bucket_space, bucket_id);
        DocumentId document_id(is);
        feed_handler.remove(bucket, document_id, (use_timestamp ? (time_bias + i) : 0), pending_tracker);
    }
    assert(is.empty());
    pending_tracker.drain();
}

}
