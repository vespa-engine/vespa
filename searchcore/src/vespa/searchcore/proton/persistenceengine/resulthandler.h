// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/abstractpersistenceprovider.h>

namespace proton {

template <typename ResultType>
class IResultHandler {
public:
    virtual ~IResultHandler() { }
    virtual void handle(const ResultType &result) = 0;
};

typedef IResultHandler<storage::spi::BucketIdListResult> IBucketIdListResultHandler;
typedef IResultHandler<storage::spi::BucketInfoResult>   IBucketInfoResultHandler;
typedef IResultHandler<storage::spi::Result>             IGenericResultHandler;

} // namespace proton

