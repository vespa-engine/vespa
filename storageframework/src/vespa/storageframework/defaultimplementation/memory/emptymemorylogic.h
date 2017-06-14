// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <storage/memorymanager/memorymanager.h>
#include <storage/memorymanager/memorystate.h>

namespace storage {

class EmptyMemoryLogic : public AllocationLogic
{
private:
    MemoryState _state;

public:
    EmptyMemoryLogic() : _state(100) {}

    virtual void getState(MemoryState& state, bool resetMax) {
        state = _state;
        if (resetMax) _state.resetMax();
    }

    virtual MemoryToken::UP allocate(
                                const AllocationType& type,
                                uint64_t, uint64_t max,
                                storage::api::StorageMessage::Priority p,
                                ReduceMemoryUsageInterface* = 0) {
        return MemoryToken::UP(
                new MemoryToken(*this, type, max, p));
    }

    virtual bool resize(MemoryToken& token, uint64_t min, uint64_t max) {
        setTokenSize(token, max);
        return true;
    }

    virtual void freeToken(MemoryToken&) {}

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const
    {
        (void) verbose; (void) indent;
        out << "EmptyMemoryLogic()";
    }
};

} // storage

