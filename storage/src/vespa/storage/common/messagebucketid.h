// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucketid.h>

namespace storage {

namespace api {
class StorageMessage;
}

/**
 * @return msg's relevant bucket id. May be an internal message.
 * @throws vespalib::IllegalArgumentException if msg does not
 *     have a bucket id.
 */
document::BucketId getStorageMessageBucketId(
        const api::StorageMessage& msg);

}


