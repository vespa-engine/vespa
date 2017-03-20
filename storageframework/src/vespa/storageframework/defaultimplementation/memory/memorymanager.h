// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::MemoryManager
 *
 * \brief Utility for tracking memory usage on distributor and storage nodes.
 *
 * The memory manager is responsible for limiting the memory allocated by
 * various users within VDS storage and distributor nodes, such that these
 * nodes don't use more memory than they can use, to avoid swapping.
 *
 * It will produce a status page to view big memory users, and give some
 * historic data. It will track memory users and give them less and less memory
 * as closer we are to utilizing all memory we are able to use. When getting
 * close to full it will deny memory allocations to incoming commands that wants
 * to use additional memory in able to complete the operation.
 *
 * The main class here defines the interface the client has to worry about. It
 * should thus not point to the implementation in any way.
 *
 */

#pragma once

#include <map>
#include <vespa/storageframework/generic/memory/memorymanagerinterface.h>
#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/util/sync.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

class MemoryManager;
class AllocationLogic;
class MemoryState;

class MemoryTokenImpl : public vespalib::Printable,
                        public MemoryToken
{
    friend class AllocationLogic;

    AllocationLogic& _logic;
    ReduceMemoryUsageInterface* _reducer;
    uint64_t _currentlyAllocated;
    uint32_t _allocCount;
    const MemoryAllocationType& _type;
    uint8_t _priority;

public:
    typedef std::unique_ptr<MemoryTokenImpl> UP;

    MemoryTokenImpl(const MemoryTokenImpl &) = delete;
    MemoryTokenImpl & operator = (const MemoryTokenImpl &) = delete;
    MemoryTokenImpl(AllocationLogic& logic,
                    const MemoryAllocationType& type,
                    uint64_t allocated,
                    uint8_t priority,
                    ReduceMemoryUsageInterface* = 0);

    virtual ~MemoryTokenImpl();

    uint64_t getSize() const { return _currentlyAllocated; }
    uint64_t getAllocationCount() const { return _allocCount; }
    const MemoryAllocationType& getType() const { return _type; }
    ReduceMemoryUsageInterface* getReducer() const { return _reducer; }

    uint8_t getPriority() const { return _priority; }

    virtual bool resize(uint64_t min, uint64_t max);

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;
};

class AllocationLogic : public vespalib::Printable
{
protected:
    /**
     * MemoryTokens are friends with this class, such that logic classes
     * can use this function to alter token size, without function for that
     * being public.
     */
    void setTokenSize(MemoryTokenImpl& token, uint64_t size)
        { token._currentlyAllocated = size; }

    virtual MemoryToken::UP allocate(const MemoryAllocationType&,
                                     uint8_t priority,
                                     ReduceMemoryUsageInterface*) = 0;
    virtual bool resize(MemoryToken& token, uint64_t min, uint64_t max,
                        uint32_t allocationCounts) = 0;
public:
    typedef std::unique_ptr<AllocationLogic> UP;
    virtual ~AllocationLogic() = 0;

    virtual void setMaximumMemoryUsage(uint64_t max) = 0;

    virtual void getState(MemoryState&, bool resetMax = false) = 0;

    /**
     * Decide how much to allocate for this request. Should be between min
     * and max, unless it's of a type that can be denied (such as external
     * requests), in which case we can also deny allocation by returning a null
     * token.
     */
    MemoryToken::UP allocate(const MemoryAllocationType&,
                             uint64_t min,
                             uint64_t max,
                             uint8_t priority,
                             ReduceMemoryUsageInterface* = 0);
    /**
     * Resize the size in a token. If more memory is requested, then it might
     * fail. The sizes given in min and max is given as total min and max,
     * including any memory you may already have. If successful, the logic will
     * have added this size to the token passed in.
     */
    bool resize(MemoryTokenImpl& token, uint64_t min, uint64_t max);

        // Called by token destructor to free up tracked resources
    virtual void freeToken(MemoryTokenImpl& token) = 0;

    virtual uint64_t getMemorySizeFreeForPriority(uint8_t priority) const = 0;

        // vespalib::Printable implementation
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const = 0;
};

class MemoryManager : public vespalib::Printable,
                      public MemoryManagerInterface
{
    AllocationLogic::UP _logic;
    vespalib::Lock _typeLock;
    std::map<std::string, MemoryAllocationType::UP> _types;

public:
    typedef std::unique_ptr<MemoryManager> UP;

    MemoryManager(AllocationLogic::UP);
    ~MemoryManager();

    virtual void setMaximumMemoryUsage(uint64_t max);
    virtual void getState(MemoryState& state, bool resetMax = false);

    virtual const MemoryAllocationType&
    registerAllocationType(const MemoryAllocationType& type);

    virtual const MemoryAllocationType&
    getAllocationType(const std::string& name) const;

    virtual std::vector<const MemoryAllocationType*> getAllocationTypes() const;

    MemoryToken::UP allocate(
            const MemoryAllocationType&,
            uint64_t min,
            uint64_t max,
            uint8_t p,
            ReduceMemoryUsageInterface* = 0);
    
    virtual uint64_t getMemorySizeFreeForPriority(uint8_t priority) const;

    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;

};

} // defaultimplementation
} // framework
} // storage

