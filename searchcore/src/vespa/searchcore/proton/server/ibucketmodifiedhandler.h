// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document { class BucketId;}

namespace proton {

class IBucketModifiedHandler
{
public:
    virtual void notifyBucketModified(const document::BucketId &bucket) = 0;
    virtual ~IBucketModifiedHandler() = default;
};

}
