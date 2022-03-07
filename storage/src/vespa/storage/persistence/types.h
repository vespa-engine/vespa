// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <iosfwd>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/base/documentid.h>
#include <vespa/storage/bucketdb/storbucketdb.h>
#include <vespa/storageapi/buckets/bucketinfo.h>
#include <vespa/storageapi/defs.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/storageframework/generic/clock/time.h>

namespace storage {

class MessageTracker;

struct Types {
    typedef document::BucketId BucketId;
    typedef document::DocumentId DocumentId;
    typedef document::GlobalId GlobalId;
    typedef framework::MicroSecTime Timestamp;
    typedef vespalib::string String;
    typedef api::BucketInfo BucketInfo;
    using MessageTrackerUP = std::unique_ptr<MessageTracker>;
protected:
    ~Types() {} // Noone should refer to objects as Types objects
};

} // storage

