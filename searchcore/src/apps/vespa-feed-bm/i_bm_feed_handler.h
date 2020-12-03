// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>

namespace document {
class Bucket;
class Document;
class DocumentUpdate;
class DocumentId;
}

namespace feedbm {

class BucketInfoQueue;
class PendingTracker;

/*
 * Interface class for benchmark feed handler.
 */
class IBmFeedHandler
{
public:
    virtual ~IBmFeedHandler() = default;
    virtual void put(const document::Bucket& bucket, std::unique_ptr<document::Document> document, uint64_t timestamp, PendingTracker& tracker) = 0;
    virtual void update(const document::Bucket& bucket, std::unique_ptr<document::DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker) = 0;
    virtual void remove(const document::Bucket& bucket, const document::DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker) = 0;
    virtual void get(const document::Bucket& bucket, vespalib::stringref field_set_string, const document::DocumentId& document_id, PendingTracker& tracker) = 0;
    virtual void attach_bucket_info_queue(PendingTracker& tracker) = 0;
    virtual uint32_t get_error_count() const = 0;
    virtual const vespalib::string &get_name() const = 0;
    virtual bool manages_timestamp() const = 0;
};

}
