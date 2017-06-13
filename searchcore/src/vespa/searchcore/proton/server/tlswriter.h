// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>

namespace proton {
class FeedOperation;

/**
 * Interface for writing to the TransactionLogServer.
 */
struct TlsWriter {
    virtual ~TlsWriter() {}

    virtual void storeOperation(const FeedOperation &op) = 0;
    virtual bool erase(search::SerialNum oldest_to_keep) = 0;

    virtual search::SerialNum
    sync(search::SerialNum syncTo) = 0;
};

}  // namespace proton

