// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/bucket/bucket.h>

namespace storage {

namespace api {
class StorageMessage;
}

/**
 * @return msg's relevant bucket id. May be an internal message.
 * @throws vespalib::IllegalArgumentException if msg does not
 *     have a bucket id.
 */
document::Bucket getStorageMessageBucket(const api::StorageMessage& msg);

}


