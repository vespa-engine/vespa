// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "operation.h"
#include <vespa/storage/distributor/operation_sequencer.h>

namespace storage::distributor {

/**
 * A sequenced operation is an operation whose concurrency against a specific document ID
 * may be limited by the distributor to avoid race conditions caused by concurrent
 * modifications.
 */
class SequencedOperation : public Operation {
    SequencingHandle _sequencingHandle;
public:
    SequencedOperation() : Operation(), _sequencingHandle() {}

    explicit SequencedOperation(SequencingHandle sequencingHandle)
        : Operation(),
          _sequencingHandle(std::move(sequencingHandle)) {
    }
};

} // storage::distributor
