// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "scheduler.h"

#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/util/ref_counted.h>

#include <chrono>
#include <condition_variable>
#include <mutex>

class FNET_IServerAdapter;
class FNET_TransportThread;
class FNET_Config;

/**
 * This is the common superclass of all components that may be part of
 * the transport layer event based I/O framework. Note that all IO
 * Components do IO against the network and that they use sockets to
 * perform that IO.
 **/
class FNET_IOComponent : public vespalib::enable_ref_counted {
    friend class FNET_TransportThread;

    using Selector = vespalib::Selector<FNET_IOComponent>;

    struct Flags {
        Flags(bool shouldTimeout)
            : _ioc_readEnabled(false),
              _ioc_writeEnabled(false),
              _ioc_shouldTimeOut(shouldTimeout),
              _ioc_added(false),
              _ioc_delete(false) {}
        bool _ioc_readEnabled;   // read event enabled ?
        bool _ioc_writeEnabled;  // write event enabled ?
        bool _ioc_shouldTimeOut; // component should timeout ?
        bool _ioc_added;         // was added to event loop
        bool _ioc_delete;        // going down...
    };

protected:
    FNET_IOComponent*       _ioc_next;      // next in list
    FNET_IOComponent*       _ioc_prev;      // prev in list
    FNET_TransportThread*   _ioc_owner;     // owner(TransportThread) ref.
    Selector*               _ioc_selector;  // attached event selector
    std::string             _ioc_spec;      // connect/listen spec
    Flags                   _flags;         // Compressed representation of boolean flags;
    int                     _ioc_socket_fd; // source of events.
    vespalib::steady_time   _ioc_timestamp; // last I/O activity
    std::mutex              _ioc_lock;      // synchronization
    std::condition_variable _ioc_cond;      // synchronization

public:
    FNET_IOComponent(const FNET_IOComponent&) = delete;
    FNET_IOComponent& operator=(const FNET_IOComponent&) = delete;

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
    FNET_IOComponent(FNET_TransportThread* owner, int socket_fd, const char* spec, bool shouldTimeOut);

    /**
     * Destruct component.
     **/
    virtual ~FNET_IOComponent();

    /**
     * @return connect/listen spec
     **/
    const char* GetSpec() const { return _ioc_spec.c_str(); }

    /*
     * Get a guard to gain exclusive access.
     */
    std::unique_lock<std::mutex> getGuard() { return std::unique_lock<std::mutex>(_ioc_lock); }

    /**
     * @return the owning TransportThread object.
     **/
    FNET_TransportThread* Owner() { return _ioc_owner; }

    /**
     * Get the configuration object associated with the owning transport
     * object.
     *
     * @return config object.
     **/
    const FNET_Config& getConfig() const;

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
     * Attach an event selector to this component. Before deleting an
     * IOC, one must first call detach_selector to detach the
     * selector.
     *
     * @param selector event selector to be attached.
     **/
    void attach_selector(Selector& selector);

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
     * Used to identify which components are related to a specific
     * server adapter to be able to perform partial shutdown.
     *
     * @return the server adapter attached to this component
     **/
    virtual FNET_IServerAdapter* server_adapter() = 0;

    /**
     * This function is called as the first step of adding an io
     * component to the selection loop. The default implementation
     * will always return true. This can be overridden to perform
     * delayed setup in the network thread. If this function returns
     * false, the component is broken and should be closed
     * immediately.
     *
     * @return false if broken, true otherwise.
     **/
    virtual bool handle_add_event();

    /**
     * This function is called by the transport thread to handle the
     * completion of an asynchronous invocation of
     * 'do_handshake_work'. This functionality is used by TLS
     * connections in order to move expensive cpu work out of the
     * transport thread. If this function returns false, the component
     * is broken and should be closed immediately.
     *
     * @return false if broken, true otherwise.
     **/
    virtual bool handle_handshake_act();

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
