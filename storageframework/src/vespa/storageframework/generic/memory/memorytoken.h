// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::framework::MemoryToken
 * \ingroup memory
 *
 * \brief Token to keep by client for current allocations.
 *
 * This class is a token a memory manager client will get from the memory
 * manager when getting memory. It can be used to know how much you currently
 * have allocated, and through it you can request more or less memory.
 */

#pragma once

#include <memory>

namespace storage::framework {

class MemoryToken {
protected:
public:
    typedef std::unique_ptr<MemoryToken> UP;
    virtual ~MemoryToken();

    virtual uint64_t getSize() const = 0;
    virtual bool resize(uint64_t min, uint64_t max) = 0;
};

}
