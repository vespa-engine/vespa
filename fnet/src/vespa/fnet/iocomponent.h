// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "stats.h"
#include <vespa/fastos/cond.h>
#include <vespa/fastos/timestamp.h>
#include <vespa/vespalib/net/selector.h>

class FNET_TransportThread;
class FNET_StatCounters;
class FNET_Config;

/**
 * This is the common superclass of all components that may be part of
 * the transport layer event based I/O framework. Note that all IO
 * Components do IO against the network and that they use sockets to
 * perform that IO.
 **/
class FNET_IOComponent
{
    friend class FNET_TransportThread;

    FNET_IOComponent(const FNET_IOComponent &);
    FNET_IOComponent &operator=(const FNET_IOComponent &);

    using Selector = vespalib::Selector<FNET_IOComponent>;

    struct Flags {
        Flags(bool shouldTimeout) :
            _ioc_readEnabled(false),
            _ioc_writeEnabled(false),
            _ioc_shouldTimeOut(shouldTimeout),
            _ioc_added(false),
            _ioc_delete(false)
        { }
        bool  _ioc_readEnabled;   // read event enabled ?
        bool  _ioc_writeEnabled;  // write event enabled ?
        bool  _ioc_shouldTimeOut; // component should timeout ?
        bool  _ioc_added;         // was added to event loop
        bool  _ioc_delete;        // going down...
    };
protected:
    FNET_IOComponent        *_ioc_next;          // next in list
    FNET_IOComponent        *_ioc_prev;          // prev in list
    FNET_TransportThread    *_ioc_owner;         // owner(TransportThread) ref.
    FNET_StatCounters       *_ioc_counters;      // stat counters
    int                      _ioc_socket_fd;     // source of events.
    Selector                *_ioc_selector;      // attached event selector
    char                    *_ioc_spec;          // connect/listen spec
    Flags                    _flags;             // Compressed representation of boolean flags;
    fastos::TimeStamp        _ioc_timestamp;     // last I/O activity
    FastOS_Cond              _ioc_cond;          // synchronization
    uint32_t                 _ioc_refcnt;        // reference counter

    // direct write stats kept locally
    uint32_t   _ioc_directPacketWriteCnt;
    uint32_t   _ioc_directDataWriteCnt;

public:

    /**
     * Construct an IOComponent with the given owner. The socket that
     * will be used for IO is also given. The reason for this is to
     * enable the IOC superclass to handle all event registration and
     * deregistration without having to rely on code located in
     * subclasses.
     *
     * @param owner the TransportThread object owning this component
     * @param socket_fd the socket handle used by this IOC
     * @param spec listen/connect spec for this IOC
     * @param shouldTimeOut should this IOC time out if idle ?
     **/
    FNET_IOComponent(FNET_TransportThread *owner, int socket_fd,
                     const char *spec, bool shouldTimeOut);


    /**
     * Destruct component.
     **/
    virtual ~FNET_IOComponent();


    /**
     * @return connect/listen spec
     **/
    const char *GetSpec() const { return _ioc_spec; }


    /**
     * Lock object to gain exclusive access.
     **/
    void Lock()       { _ioc_cond.Lock();   }


    /**
     * Unlock object to yield exclusive access.
     **/
    void Unlock()     { _ioc_cond.Unlock(); }


    /**
     * Wait on this object. Caller should have lock on object.
     **/
    void Wait()       { _ioc_cond.Wait();   }


    /**
     * Signal one thread waiting on this object. Caller should have
     * lock.
     **/
    void Signal()     { _ioc_cond.Signal(); }


    /**
     * Signal all thread waiting on this object. Caller should have
     * lock.
     **/
    void Broadcast()  { _ioc_cond.Broadcast(); }


    /**
     * Allocate a reference to this component. This method locks the
     * object to protect the reference counter.
     **/
    void AddRef();


    /**
     * Allocate a reference to this component without locking the
     * object. Caller already has lock on object.
     **/
    void AddRef_NoLock();


    /**
     * Free a reference to this component. This method locks the object
     * to protect the reference counter.
     **/
    void SubRef();


    /**
     * Free a reference to this component. This method uses locking to
     * protect the reference counter, but assumes that the lock has
     * already been obtained when this method is called.
     **/
    void SubRef_HasLock();


    /**
     * Free a reference to this component without locking the
     * object. NOTE: this method may only be called on objects with more
     * than one reference.
     **/
    void SubRef_NoLock();


    /**
     * @return the owning TransportThread object.
     **/
    FNET_TransportThread *Owner() { return _ioc_owner; }


    /**
     * Get the configuration object associated with the owning transport
     * object.
     *
     * @return config object.
     **/
    FNET_Config *GetConfig();


    /**
     * @return whether this component should time-out if idle.
     **/
    bool ShouldTimeOut() { return _flags._ioc_shouldTimeOut; }


    /**
     * Update time-out information. This method simply performs a
     * proxy-call to the owning transport object, calling
     * FNET_TransportThread::UpdateTimeOut() with itself as parameter.
     **/
    void UpdateTimeOut();


    /**
     * Count packet read(s). This is a proxy method updating the stat
     * counters associated with the owning transport object.
     *
     * @param cnt the number of packets read (default is 1).
     **/
    void CountPacketRead(uint32_t cnt = 1)
    { _ioc_counters->CountPacketRead(cnt); }


    /**
     * Count packet write(s). This is a proxy method updating the stat
     * counters associated with the owning transport object.
     *
     * @param cnt the number of packets written (default is 1).
     **/
    void CountPacketWrite(uint32_t cnt = 1)
    { _ioc_counters->CountPacketWrite(cnt); }


    /**
     * Count direct packet write(s). This method will increase an
     * internal counter. The shared stat counters may not be used
     * because this method may be called by other threads than the
     * transport thread. Note: The IO Component should be locked when
     * this method is called.
     *
     * @param cnt the number of packets written (default is 1).
     **/
    void CountDirectPacketWrite(uint32_t cnt = 1)
    { _ioc_directPacketWriteCnt += cnt; }


    /**
     * Count read data. This is a proxy method updating the stat
     * counters associated with the owning transport object.
     *
     * @param bytes the number of bytes read.
     **/
    void CountDataRead(uint32_t bytes)
    { _ioc_counters->CountDataRead(bytes); }


    /**
     * Count written data. This is a proxy method updating the stat
     * counters associated with the owning transport object.
     *
     * @param bytes the number of bytes written.
     **/
    void CountDataWrite(uint32_t bytes)
    { _ioc_counters->CountDataWrite(bytes); }


    /**
     * Count direct written data. This method will increase an
     * internal counter. The shared stat counters may not be used
     * because this method may be called by other threads than the
     * transport thread. Note: The IO Component should be locked when
     * this method is called.
     *
     * @param bytes the number of bytes written.
     **/
    void CountDirectDataWrite(uint32_t bytes)
    { _ioc_directDataWriteCnt += bytes; }


    /**
     * Transfer the direct write stats held by this IO Component over to
     * the stat counters associated with the owning transport object
     * (and reset the local counters). Note: This method should only be
     * called from the transport thread while having the lock on this IO
     * Component. Note: This method is called from the transport loop
     * and should generally not be called by application code.
     **/
    void FlushDirectWriteStats()
    {
        _ioc_counters->CountPacketWrite(_ioc_directPacketWriteCnt);
        _ioc_counters->CountDataWrite(_ioc_directDataWriteCnt);
        _ioc_directPacketWriteCnt = 0;
        _ioc_directDataWriteCnt = 0;
    }


    /**
     * Attach an event selector to this component. Before deleting an
     * IOC, one must first call detach_selector to detach the
     * selector.
     *
     * @param selector event selector to be attached.
     **/
    void attach_selector(Selector &selector);

    /**
     * Detach from the attached event selector. This will disable
     * future selector events.
     **/
    void detach_selector();

    /**
     * Enable or disable read events.
     *
     * @param enabled enabled(true)/disabled(false).
     **/
    void EnableReadEvent(bool enabled);


    /**
     * Enable or disable write events.
     *
     * @param enabled enabled(true)/disabled(false).
     **/
    void EnableWriteEvent(bool enabled);


    //----------- virtual methods below ----------------------//


    /**
     * This method is called by the SubRef methods just before the
     * object is deleted. It may be used to perform cleanup tasks that
     * must be done before the destructor is invoked.
     **/
    virtual void CleanupHook();


    /**
     * Close this component immediately. NOTE: this method should only
     * be called by the transport thread. If you want to close an IO
     * Component from another thread you should use the
     * FNET_TransportThread::Close method instead (with the IOC you want to
     * close as parameter).
     **/
    virtual void Close() = 0;


    /**
     * Called by the transport thread when a read event has
     * occurred.
     *
     * @return false if broken, true otherwise.
     **/
    virtual bool HandleReadEvent() = 0;


    /**
     * Called by the transport thread when a write event has
     * occurred.
     *
     * @return false if broken, true otherwise.
     **/
    virtual bool HandleWriteEvent() = 0;
};

