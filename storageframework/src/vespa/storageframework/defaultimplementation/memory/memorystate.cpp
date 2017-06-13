// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memorystate.h"
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".memory.state");

namespace storage::framework::defaultimplementation {

MemoryState::Entry::Entry()
    : _currentUsedSize(0),
      _totalUserCount(0),
      _currentUserCount(0),
      _wantedCount(0),
      _minimumCount(0),
      _deniedCount(0),
      _forcedBeyondMaximumCount(0)
{
}

void
MemoryState::Entry::operator+=(const Entry& other)
{
    _currentUsedSize += other._currentUsedSize;
    _currentUserCount += other._currentUserCount;
    _totalUserCount += other._totalUserCount;
    _wantedCount += other._wantedCount;
    _minimumCount += other._minimumCount;
    _deniedCount += other._deniedCount;
    _forcedBeyondMaximumCount += other._forcedBeyondMaximumCount;
}

MemoryState::SnapShot&
MemoryState::SnapShot::operator+=(const MemoryState::SnapShot& other)
{
    for (AllocationMap::const_iterator  it = other._allocations.begin();
         it != other._allocations.end(); ++it)
    {
        PriorityMap& map(_allocations[it->first]);
        for (PriorityMap::const_iterator it2 = it->second.begin();
             it2 != it->second.end(); ++it2)
        {
            Entry& entry(map[it2->first]);
            entry += it2->second;
        }
    }
    return *this;
}

uint64_t
MemoryState::SnapShot::getUserCount() const
{
    uint64_t count = 0;
    for (AllocationMap::const_iterator it = _allocations.begin();
         it != _allocations.end(); ++it)
    {
        for (PriorityMap::const_iterator it2 = it->second.begin();
             it2 != it->second.end(); ++it2)
        {
            count += it2->second._currentUserCount;
        }
    }
    return count;
}

MemoryState::MemoryState(Clock& clock, uint64_t maxMemory)
    : _clock(&clock),
      _maxMemory(maxMemory),
      _current(),
      _max(),
      _minJumpToUpdateMax(10 * 1024 * 1024)
{
}

MemoryState::MemoryState(const MemoryState &) = default;
MemoryState & MemoryState::operator = (const MemoryState &) = default;

MemoryState::~MemoryState() {}

void
MemoryState::addToEntry(const MemoryAllocationType& type, uint64_t memory,
                        uint8_t priority,
                        AllocationResult result, bool forcedAllocation,
                        uint64_t allocationCounts)
{
    LOG(spam, "Allocating memory %s - %" PRIu64 " bytes at priority %u. "
              "Count %" PRIu64 ".",
        type.getName().c_str(), memory, priority, allocationCounts);
    PriorityMap& map(_current._allocations[&type]);
    Entry& e(map[priority]);
    e._currentUsedSize += memory;
    e._totalUserCount += allocationCounts;
    if (allocationCounts == 0) {
            // Resizes adding no more users still count as another total
            // allocation attempt.
        ++e._totalUserCount;
    }
    e._currentUserCount += allocationCounts;
    switch (result) {
        case GOT_MAX: ++e._wantedCount; break;
        case GOT_MIN: ++e._minimumCount; break;
        case DENIED: ++e._deniedCount; break;
    }
    if (forcedAllocation) ++e._forcedBeyondMaximumCount;
    _current._usedMemory += memory;
    if (!type.isCache()) {
        _current._usedWithoutCache += memory;
    }
    if (_current._usedWithoutCache
                > _max._usedWithoutCache + _minJumpToUpdateMax)
    {
        LOG(spam, "Updating max to current %" PRIu64 " bytes of memory used",
            _current._usedWithoutCache);
        _max = _current;
        _max._timeTaken = _clock->getTimeInSeconds();
    }
}

void
MemoryState::removeFromEntry(const MemoryAllocationType& type, uint64_t memory,
                             uint8_t priority,
                             uint64_t allocationCounts)
{
    LOG(spam, "Freeing memory %s - %" PRIu64 " bytes at priority %u. "
              "Count %" PRIu64 ".",
        type.getName().c_str(), memory, priority, allocationCounts);
    PriorityMap& map(_current._allocations[&type]);
    Entry& e(map[priority]);
    e._currentUsedSize -= memory;
    e._currentUserCount -= allocationCounts;
    _current._usedMemory -= memory;
    if (!type.isCache()) {
        _current._usedWithoutCache -= memory;
    }
}

void
MemoryState::Entry::print(std::ostream& out, bool verbose,
                          const std::string& indent) const
{
    (void) verbose; (void) indent;
    std::ostringstream ost;
    ost << "Used(" << _currentUsedSize << " B / "
        << _currentUserCount << ") ";
    for (uint32_t i=ost.str().size(); i<20; ++i) {
        ost << " ";
    }

    out << ost.str()
        << "Stats(" << _totalUserCount
        << ", " << _wantedCount
        << ", " << _minimumCount
        << ", " << _deniedCount
        << ", " << _forcedBeyondMaximumCount << ")";
}

namespace {
    void printAllocations(std::ostream& out,
                          const MemoryState::AllocationMap& map,
                          const std::string& indent)
    {
        std::map<std::string, std::string> allocs;

        for (MemoryState::AllocationMap::const_iterator it = map.begin();
             it != map.end(); ++it)
        {
            for (MemoryState::PriorityMap::const_iterator it2
                    = it->second.begin(); it2 != it->second.end(); ++it2)
            {
                std::ostringstream name;
                name << it->first->getName() << "("
                     << static_cast<uint16_t>(it2->first) << "): ";
                for (uint32_t i=name.str().size(); i<25; ++i) {
                    name << " ";
                }

                std::ostringstream tmp;
                it2->second.print(tmp, true, indent + "    ");

                allocs[name.str()] = tmp.str();
            }
        }

        for (std::map<std::string, std::string>::const_iterator it
                = allocs.begin(); it != allocs.end(); ++it)
        {
            out << "\n" << indent << "  " << it->first << it->second;
        }
    }
}

void
MemoryState::SnapShot::print(std::ostream& out, bool verbose,
                             const std::string& indent) const
{
    out << "SnapShot(Used " << _usedMemory << ", w/o cache "
        << _usedWithoutCache;
    if (verbose) {
        out << ") {";
        if (_usedMemory > 0) {
            out << "\n" << indent << "  Type(Pri): Used(Size/Allocs) "
                << "Stats(Allocs, Wanted, Min, Denied, Forced)";
        }
        printAllocations(out, _allocations, indent);
        out << "\n" << indent << "}";
    }
}

void
MemoryState::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    bool maxSet = (_max._usedWithoutCache > _current._usedWithoutCache);
    out << "MemoryState(Max memory: " << _maxMemory << ") {"
        << "\n" << indent << "  Current: ";
    _current.print(out, verbose, indent + "  ");
    if (maxSet) {
        out << "\n" << indent << "  Max: ";
        _max.print(out, verbose, indent + "  ");
    }
    out << "\n" << indent << "}";
}

}
