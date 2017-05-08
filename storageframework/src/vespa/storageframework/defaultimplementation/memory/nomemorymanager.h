// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::NoMemoryManager
 *
 * \brief Memory manager that gives out max memory to everyone.
 *
 * Memory manager to use for testing and for apps not wanting to track memory.
 * This manager will merely give out max to everyone who asks and not even keep
 * track of anything.
 */

#pragma once

#include <vespa/storageframework/generic/memory/memorymanagerinterface.h>
#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/util/sync.h>
#include <map>

namespace storage {
namespace framework {
namespace defaultimplementation {

class SimpleMemoryTokenImpl : public MemoryToken
{
    uint64_t _allocated;

public:
    SimpleMemoryTokenImpl(const SimpleMemoryTokenImpl &) = delete;
    SimpleMemoryTokenImpl & operator = (const SimpleMemoryTokenImpl &) = delete;
    SimpleMemoryTokenImpl(uint64_t allocated) : _allocated(allocated) {}

    uint64_t getSize() const override { return _allocated; }
    bool resize(uint64_t /* min */, uint64_t max) override { _allocated = max; return true; }
};

class NoMemoryManager : public MemoryManagerInterface
{
    vespalib::Lock _typeLock;
    std::map<std::string, MemoryAllocationType::UP> _types;

public:
    typedef std::unique_ptr<NoMemoryManager> UP;

    ~NoMemoryManager();

    void setMaximumMemoryUsage(uint64_t) override {}
    const MemoryAllocationType & registerAllocationType(const MemoryAllocationType& type) override;
    const MemoryAllocationType & getAllocationType(const std::string& name) const override;

    MemoryToken::UP allocate(const MemoryAllocationType&, uint64_t /* min */, uint64_t max,
                             uint8_t /* priority */, ReduceMemoryUsageInterface* = 0) override
    {
        return SimpleMemoryTokenImpl::UP(new SimpleMemoryTokenImpl(max));
    }
    uint64_t getMemorySizeFreeForPriority(uint8_t priority) const override {
        (void) priority;
        return std::numeric_limits<uint64_t>().max();
    }

    std::vector<const MemoryAllocationType*> getAllocationTypes() const override;
};

} // defaultimplementation
} // framework
} // storage

