// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MemoryState
 *
 * \brief Shows the state of current memory users
 *
 */

#pragma once

#include "memorymanager.h"
#include <vespa/storageframework/storageframework.h>
#include <vespa/vespalib/util/sync.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

class MemoryState : public vespalib::Printable {
public:
    struct Entry {
            // Total number of bytes allocated to this entry right now
        uint64_t _currentUsedSize;
            // Total number of allocations done on this entry
        uint64_t _totalUserCount;
            // Total number of allocations for this entry right now
        uint32_t _currentUserCount;
            // Amount of times this entry has gotten all the memory it wanted
        uint32_t _wantedCount;
            // Amount of times this entry has gotten less than all the memory
            // it wanted
        uint32_t _minimumCount;
            // Amount of times this entry has been denied getting memory
        uint32_t _deniedCount;
            // Amount of times this entry has forced memory allocations beyond
            // the maximum
        uint32_t _forcedBeyondMaximumCount;

        Entry();

        void print(std::ostream& out, bool verbose,
                   const std::string& indent) const;

        /**
         * Set this instances counts to the counts from the other entry.
         */
        void transferCounts(const Entry& other);

        void operator+=(const Entry& other);
    };

    typedef std::map<uint8_t, Entry> PriorityMap;
    typedef std::map<const MemoryAllocationType*, PriorityMap> AllocationMap;

    /**
     * A snapshot contains data for either current or max seen data.
     * When a new maximum is seen, current is copied to max.
     */
    class SnapShot : public vespalib::Printable {
        friend class MemoryState;

        uint64_t _usedMemory;
        uint64_t _usedWithoutCache;
        SecondTime _timeTaken;
        AllocationMap _allocations;

    public:
        SnapShot() : vespalib::Printable() { clear(); }
        SnapShot(const SnapShot& o) : vespalib::Printable() { (*this) = o; }

        void print(std::ostream& out, bool verbose, const std::string& indent) const override;

        void clear() {
            _usedMemory = 0;
            _usedWithoutCache = 0;
            _timeTaken.setTime(0);
            _allocations.clear();
        }

        SnapShot& operator=(const SnapShot& other) {
            _usedMemory = other._usedMemory;
            _usedWithoutCache = other._usedWithoutCache;
            _timeTaken = other._timeTaken;
            _allocations = other._allocations;
            return *this;
        }

        SnapShot& operator+=(const SnapShot& other);

        const AllocationMap& getAllocations() const { return _allocations; }
        uint64_t getUsedSize() const { return _usedMemory; }
        uint64_t getUsedSizeIgnoringCache() const { return _usedWithoutCache; }
        uint64_t getUserCount() const;
    };

private:
    Clock* _clock;
    uint64_t _maxMemory;
    SnapShot _current;
    SnapShot _max;
    uint32_t _minJumpToUpdateMax;

public:
    MemoryState(Clock& clock, uint64_t maxMemory);
    MemoryState(const MemoryState &);
    MemoryState & operator = (const MemoryState &);
    MemoryState(MemoryState &&) = default;
    MemoryState & operator = (MemoryState &&) = default;
    ~MemoryState();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void setMaximumMemoryUsage(uint64_t max) { _maxMemory = max; }
    void setMinJumpToUpdateMax(uint32_t bytes) { _minJumpToUpdateMax = bytes; }

    enum AllocationResult { GOT_MAX, GOT_MIN, DENIED };
    void addToEntry(const MemoryAllocationType& type, uint64_t memory,
                    uint8_t priority,
                    AllocationResult result, bool forcedAllocation = false,
                    uint64_t allocationCounts = 1);

    void removeFromEntry(const MemoryAllocationType& type, uint64_t memory,
                         uint8_t priority,
                         uint64_t allocationCounts = 1);
    void resetMax() {
        _max = _current;
        _max._timeTaken = _clock->getTimeInSeconds();
    }

    const SnapShot& getCurrentSnapshot() const { return _current; }
    const SnapShot& getMaxSnapshot() const { return _max; }


    uint64_t getTotalSize() const { return _maxMemory; }
    uint64_t getFreeSize() const {
        return _maxMemory > _current._usedMemory
            ? _maxMemory - _current._usedMemory : 0;
    }
};

} // defaultimplementation
} // framework
} // storage

