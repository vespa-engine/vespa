// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::BucketInfoReply
 * @ingroup messageapi
 *
 * @brief Superclass for storage replies which returns bucket info in reply.
 *
 * A bucket info reply contains information about the state of a bucket. This
 * can be altered from before the operation if this was a write operation or if
 * the bucket was repaired in the process.
 */

#pragma once

#include "bucketreply.h"
#include "bucketinfocommand.h"
#include <vespa/storageapi/buckets/bucketinfo.h>

namespace storage::api {

class BucketInfoReply : public BucketReply {
    BucketInfo _result;

protected:
    BucketInfoReply(const BucketInfoCommand& cmd, const ReturnCode& code = ReturnCode(ReturnCode::OK));

public:
    DECLARE_POINTER_TYPEDEFS(BucketInfoReply);

    const BucketInfo& getBucketInfo() const { return _result; };
    void setBucketInfo(const BucketInfo& info) { _result = info; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

}
