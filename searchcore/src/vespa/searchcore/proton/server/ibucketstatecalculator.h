// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/trinary.h>

namespace document { class Bucket; }

namespace proton {

struct IBucketStateCalculator
{
    virtual vespalib::Trinary shouldBeReady(const document::Bucket &bucket) const = 0;
    virtual bool clusterUp() const = 0;
    virtual bool nodeUp() const = 0;
    virtual bool nodeInitializing() const = 0;
    virtual bool nodeRetired() const = 0;
    virtual bool nodeMaintenance() const noexcept = 0;
    virtual ~IBucketStateCalculator() = default;
};

} // namespace proton
