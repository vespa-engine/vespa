// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

namespace proton {

/**
 * This class represents a collection of IFlushTarget objects. It is implemented
 * by DocumentDB.
 */
class IFlushHandler {
private:
    vespalib::string _name;

public:
    using IFlushTarget = searchcorespi::IFlushTarget;
    typedef IFlushTarget::SerialNum SerialNum;
    /**
     * Convenience typedefs.
     */
    typedef std::shared_ptr<IFlushHandler> SP;
    IFlushHandler(const IFlushHandler &) = delete;
    IFlushHandler & operator = (const IFlushHandler &) = delete;
    /**
     * Constructs a new instance of this class.
     *
     * @param name The unique name of this handler.
     */
    IFlushHandler(const vespalib::string &name) noexcept
        : _name(name)
    { }

    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~IFlushHandler() = default;

    /**
     * Returns the unique name of this handler.
     *
     * @return The name of this.
     */
    const vespalib::string & getName() const { return _name; }

    /**
     * Returns a list of the flush targets that belong to this handler. This
     * method is called by the flush scheduler thread.
     *
     * @return The list of targets.
     */
    virtual std::vector<IFlushTarget::SP> getFlushTargets() = 0;

    /**
     * Returns the current serial number of this handler. This is the head of
     * the transaction log for the domain of this.
     *
     * @return The current serial number.
     */
    virtual SerialNum getCurrentSerialNumber() const = 0;

    /**
     * This method is called after a flush has been completed. All transactions
     * up to the given serial number can be pruned from the domain of this
     * handler. This method is called by the flush scheduler thread.
     *
     * @param flushedSerial Serial number flushed for all flush
     *                      targets belonging to this handler.
     */
    virtual void flushDone(SerialNum flushedSerial) = 0;

    /*
     * This method is called to sync tls to stable media, up to and
     * including the given serial number.
     *
     * @param syncTo The last serial number that has to be persisted to stable media.
     */
    virtual void syncTls(SerialNum syncTo) = 0;
};

} // namespace proton

