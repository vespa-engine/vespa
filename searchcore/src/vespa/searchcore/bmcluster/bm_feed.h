// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/document/bucket/bucket.h>

namespace document {

class Document;
class DocumentType;
class DocumentTypeRepo;
class DocumentUpdate;
class Field;

}

namespace vespalib {

class ThreadStackExecutor;
class nbostream;

}

namespace search::bmcluster {

class BmFeedParams;
class BmRange;
class BucketSelector;

/*
 * Class to generate synthetic feed of documents.
 */
class BmFeed {
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    const document::DocumentType*                     _document_type;
    const document::Field&                            _field;
    uint32_t                                          _bucket_bits;
    document::BucketSpace                             _bucket_space;
    vespalib::nbostream make_get_or_remove_feed(BmRange range, BucketSelector bucket_selector, bool make_removes);
public:

    BmFeed(std::shared_ptr<const document::DocumentTypeRepo> document_types);
    ~BmFeed();
    uint32_t num_buckets() const { return (1u << _bucket_bits); }
    document::BucketSpace get_bucket_space() const noexcept { return _bucket_space; }
    document::BucketId make_bucket_id(uint32_t n) const { return document::BucketId(_bucket_bits, n & (num_buckets() - 1)); }
    document::Bucket make_bucket(uint32_t n) const { return document::Bucket(_bucket_space, make_bucket_id(n)); }
    document::DocumentId make_document_id(uint32_t n, uint32_t i) const;
    std::unique_ptr<document::Document> make_document(uint32_t n, uint32_t i) const;
    std::unique_ptr<document::DocumentUpdate> make_document_update(uint32_t n, uint32_t i) const;
    vespalib::nbostream make_put_feed(BmRange range, BucketSelector bucket_selector);
    vespalib::nbostream make_update_feed(BmRange range, BucketSelector bucket_selector);
    vespalib::nbostream make_get_feed(BmRange range, BucketSelector bucket_selector);
    vespalib::nbostream make_remove_feed(BmRange range, BucketSelector bucket_selector);
    std::vector<vespalib::nbostream> make_feed(vespalib::ThreadStackExecutor& executor, const BmFeedParams& bm_params, std::function<vespalib::nbostream(BmRange,BucketSelector)> func, uint32_t num_buckets, const vespalib::string& label);
};

}
