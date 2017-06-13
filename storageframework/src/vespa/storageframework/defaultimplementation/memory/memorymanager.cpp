// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memorymanager.h"
#include "memorystate.h"
#include <vespa/vespalib/util/exceptions.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

MemoryTokenImpl::MemoryTokenImpl(AllocationLogic& logic,
                                 const MemoryAllocationType& type,
                                 uint64_t allocated,
                                 uint8_t p,
                                 ReduceMemoryUsageInterface* reducer)
    : _logic(logic),
      _reducer(reducer),
      _currentlyAllocated(allocated),
      _allocCount(1),
      _type(type),
      _priority(p)
{
}

MemoryTokenImpl::~MemoryTokenImpl()
{
    _logic.freeToken(*this);
}

bool
MemoryTokenImpl::resize(uint64_t min, uint64_t max)
{
    return _logic.resize(*this, min, max);
}

void
MemoryTokenImpl::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "MemoryToken(" << _type.getName() << ": Allocated(" << _allocCount << " - "
        << _currentlyAllocated << ")";
}

AllocationLogic::~AllocationLogic()
{
}

MemoryToken::UP
AllocationLogic::allocate(const MemoryAllocationType& type,
                          uint64_t min,
                          uint64_t max,
                          uint8_t priority,
                          ReduceMemoryUsageInterface* reducer)
{
    MemoryToken::UP token(allocate(type, priority, reducer));
    assert(token.get());
    if (!resize(*token, min, max, 1)) token.reset();
    return token;
}

bool
AllocationLogic::resize(MemoryTokenImpl& token, uint64_t min, uint64_t max)
{
    return resize(token, min, max, 0);
}

MemoryManager::MemoryManager(AllocationLogic::UP logic)
    : _logic(std::move(logic))
{
    if (_logic.get() == 0) {
        throw vespalib::IllegalArgumentException(
                "Needs a real logic class to run. (Got null pointer)",
                VESPA_STRLOC);
    }
}

MemoryManager::~MemoryManager()
{
}

void
MemoryManager::setMaximumMemoryUsage(uint64_t max)
{
    _logic->setMaximumMemoryUsage(max);
}

void
MemoryManager::getState(MemoryState& state, bool resetMax)
{
    return _logic->getState(state, resetMax);
}

const MemoryAllocationType&
MemoryManager::registerAllocationType(const MemoryAllocationType& type)
{
    vespalib::LockGuard lock(_typeLock);
    _types[type.getName()] = MemoryAllocationType::UP(
                                new MemoryAllocationType(type));
    return *_types[type.getName()];
}

const MemoryAllocationType&
MemoryManager::getAllocationType(const std::string& name) const
{
    vespalib::LockGuard lock(_typeLock);
    std::map<std::string, MemoryAllocationType::UP>::const_iterator it(
            _types.find(name));
    if (it == _types.end()) {
        throw vespalib::IllegalArgumentException(
                "Allocation type not found: " + name, VESPA_STRLOC);
    }
    return *it->second;
}

std::vector<const MemoryAllocationType*>
MemoryManager::getAllocationTypes() const
{
    vespalib::LockGuard lock(_typeLock);
    std::vector<const MemoryAllocationType*> types;
    for(std::map<std::string, MemoryAllocationType::UP>::const_iterator it
            = _types.begin(); it != _types.end(); ++it)
    {
        types.push_back(it->second.get());
    }
    return types;
}

MemoryToken::UP
MemoryManager::allocate(const MemoryAllocationType& type,
                        uint64_t min,
                        uint64_t max,
                        uint8_t p,
                        ReduceMemoryUsageInterface* i)
{
    return _logic->allocate(type, min, max, p, i);
}

uint64_t
MemoryManager::getMemorySizeFreeForPriority(uint8_t priority) const
{
    return _logic->getMemorySizeFreeForPriority(priority);
}

void
MemoryManager::print(std::ostream& out, bool verbose,
                     const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "Memory Manager {" << "\n" << indent << "  ";
    _logic->print(out, verbose, indent + "  ");
    out << "\n" << indent << "}";
}

} // defaultimplementation
} // framework
} // storage
