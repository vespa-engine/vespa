// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "flushstats.h"
#include "flushtask.h"
#include <vespa/vespalib/util/time.h>
#include <vector>

namespace search { class IFlushToken; }

namespace searchcorespi {

/**
 * This abstract class represents a flushable object that uses
 * getApproxBytesBeforeFlush() bytes of memory, that will be reduced to
 * getApproxBytesAfterFlush() if flushed.
 */
class IFlushTarget
{
public:
    /**
     * The flush types that a flush target can represent.
     */
    enum class Type {
        FLUSH,
        SYNC,
        GC,
        OTHER
    };

    /**
     * The component types that a flush target can be used for.
     */
    enum class Component {
        ATTRIBUTE,
        INDEX,
        DOCUMENT_STORE,
        OTHER
    };

private:
    vespalib::string _name;
    Type      _type;
    Component _component;

public:
    template<typename T>
    class Gain {
    public:
        Gain() noexcept : _before(0), _after(0) { }
        Gain(T before, T after) noexcept : _before(before), _after(after) { }
        T getBefore() const { return _before; }
        T  getAfter() const { return _after; }
        T gain() const { return _before - _after; }
        double gainRate() const { return (_before != 0) ? double(gain())/_before : 0;}
        Gain & operator += (const Gain & b) { _before += b.getBefore(); _after += b.getAfter(); return *this; }
        static Gain noGain(size_t currentSize) { return Gain(currentSize, currentSize); }
    private:
        T _before;
        T _after;
    };
    using MemoryGain = Gain<int64_t>;
    using DiskGain = Gain<int64_t>;
    using SerialNum = search::SerialNum;
    using Time = vespalib::system_time;

    /**
     * Convenience typedefs.
     */
    using SP = std::shared_ptr<IFlushTarget>;
    using List = std::vector<SP>;
    using Task = FlushTask;

    /**
     * Constructs a new instance of this class.
     *
     * @param name The handler-wide unique name of this target.
     */
    IFlushTarget(const vespalib::string &name) noexcept;

    /**
     * Constructs a new instance of this class.
     *
     * @param name The handler-wide unique name of this target.
     * @param type The flush type of this target.
     * @param component The component type of this target.
     */
    IFlushTarget(const vespalib::string &name,
                 const Type &type,
                 const Component &component) noexcept;

    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~IFlushTarget();

    /**
     * Returns the handler-wide unique name of this target.
     *
     * @return The name of this.
     */
    const vespalib::string & getName() const { return _name; }

    /**
     * Returns the flush type of this target.
     */
    Type getType() const { return _type; }

    /**
     * Returns the component type of this target.
     */
    Component getComponent() const { return _component; }

    /**
     * Returns the approximate memory gain of this target, in bytes.
     *
     * @return The gain
     */
    virtual MemoryGain getApproxMemoryGain() const = 0;

    /**
     * Returns the approximate memory gain of this target, in bytes.
     *
     * @return The gain
     */
    virtual DiskGain getApproxDiskGain() const = 0;

    /**
     * Returns the approximate amount of bytes this target writes to disk if flushed.
     */
    virtual uint64_t getApproxBytesToWriteToDisk() const = 0;

    /**
     * Return cost of replaying a feed operation relative to cost of reading a feed operation from tls.
     */
    virtual double get_replay_operation_cost() const { return 0.0; }

    /**
     * Returns the last serial number for the transaction applied to
     * target before it was flushed to disk.  The transaction log can
     * not be pruned beyond this.
     *
     * @return The last serial number represented in flushed target
     */
    virtual SerialNum getFlushedSerialNum() const = 0;

    /**
     * Returns the time of last flush.
     *
     * @return The last flush time.
     */
    virtual Time getLastFlushTime() const = 0;

    /**
     * Return if the target itself is in bad need for a flush.
     *
     * @return true if an urgent flush is needed
     */
    virtual bool needUrgentFlush() const { return false; }

    /**
     * Initiates the flushing of temporary memory. This method must perform
     * everything required to allow another thread to complete the flush. This
     * method is called by the flush scheduler thread.
     *
     * @param currentSerial The current transaction serial number.
     * @return The task used to complete the flush.
     */
    virtual Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) = 0;

    /**
     * Returns the stats for the last completed flush operation
     * for this flush target.
     *
     * @return The stats for the last flush.
     */
    virtual FlushStats getLastFlushStats() const = 0;

};

} // namespace searchcorespi

