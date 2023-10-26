// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_operation_storer.h"
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

/**
 * Interface for writing to the TransactionLogServer.
 */
struct TlsWriter : public IOperationStorer {
    virtual ~TlsWriter() = default;

    virtual bool erase(search::SerialNum oldest_to_keep) = 0;
    virtual search::SerialNum sync(search::SerialNum syncTo) = 0;
};

}
