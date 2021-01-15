// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/result.h>

namespace proton {

template <typename ResultType>
class IResultHandler {
public:
    virtual ~IResultHandler() = default;
    virtual void handle(const ResultType &result) = 0;
};

using IBucketIdListResultHandler = IResultHandler<storage::spi::BucketIdListResult>;
using IBucketInfoResultHandler = IResultHandler<storage::spi::BucketInfoResult>;
using IGenericResultHandler = IResultHandler<storage::spi::Result>;

} // namespace proton

