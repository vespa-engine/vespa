// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operation_sequencer.h"
#include <vespa/document/base/documentid.h>
#include <cassert>

namespace storage {
namespace distributor {

void SequencingHandle::release() {
    if (valid()) {
        _sequencer->release(*this);
        _sequencer = nullptr;
    }
}

OperationSequencer::OperationSequencer() {
}

OperationSequencer::~OperationSequencer() {
}

SequencingHandle OperationSequencer::try_acquire(const document::DocumentId& id) {
    const document::GlobalId gid(id.getGlobalId());
    const auto inserted = _active_gids.insert(gid);
    if (inserted.second) {
        return SequencingHandle(*this, gid);
    } else {
        return SequencingHandle();
    }
}

void OperationSequencer::release(const SequencingHandle& handle) {
    assert(handle.valid());
    _active_gids.erase(handle.gid());
}

} // distributor
} // storage

