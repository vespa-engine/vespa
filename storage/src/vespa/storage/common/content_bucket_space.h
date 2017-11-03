// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/bucketdb/storbucketdb.h>

namespace storage {

/**
 * Class representing a bucket space (with associated bucket database) on a content node.
 */
class ContentBucketSpace {
private:
    StorBucketDatabase _bucketDatabase;

public:
    using UP = std::unique_ptr<ContentBucketSpace>;
    ContentBucketSpace();
    StorBucketDatabase &bucketDatabase() { return _bucketDatabase; }
};

}
