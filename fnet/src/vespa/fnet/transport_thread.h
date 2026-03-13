// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "config.h"
#include "packetqueue.h"
#include "scheduler.h"
#include "task.h"

#include <vespa/vespalib/net/selector.h>
#include <vespa/vespalib/net/socket_handle.h>
#include <vespa/vespalib/util/thread.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <mutex>
#include <set>

namespace fnet {
struct TimeTools;
}
class FNET_Transport;
class FNET_ControlPacket;
class FNET_IPacketStreamer;
class FNET_IServerAdapter;

/**
 * This class represents a transport thread and handles a subset of
 * the network related work for the application in both client and
 * server aspects.
 **/
class FNET_TransportThread {
    friend class FNET_IOComponent;

public:
    using Selector = vespalib::Selector<FNET_IOComponent>;

private:
    FNET_Transport&                _owner;          // owning transport layer
    vespalib::steady_time          _now;            // current time sampler
    FNET_Scheduler                 _scheduler;      // transport thread scheduler
    FNET_IOComponent*              _componentsHead; // I/O component list head
    FNET_IOComponent*              _timeOutHead;    // first IOC in list to time out
    FNET_IOComponent*              _componentsTail; // I/O component list tail
    std::atomic<uint32_t>          _componentCnt;   // # of components
    FNET_IOComponent*              _deleteList;     // IOC delete list
    Selector                       _selector;       // I/O event generator
    FNET_PacketQueue_NoLock        _queue;          // outer event queue
    FNET_PacketQueue_NoLock        _myQueue;        // inner event queue
    std::mutex                     _lock;           // protects the Q
    std::mutex                     _shutdownLock;   // used for synchronization during shutdown
    std::condition_variable        _shutdownCond;   // used for synchronization during shutdown
    std::atomic<bool>              _started;        // event loop started ?
    std::atomic<bool>              _shutdown;       // should stop event loop ?
    std::atomic<bool>              _finished;       // event loop stopped ?
    std::set<FNET_IServerAdapter*> _detaching;      // server adapters being detached
    bool                           _reject_events;  // the transport thread does not want any more events

    /**
     * Add an IOComponent to the list of components. This operation is
     * performed immidiately and without locking. This method should
     * only be called in the transport thread.
     *
     * @param comp the component to add.
     **/
    void AddComponent(FNET_IOComponent* comp);

    /**
     * Remove an IOComponent from the list of components. This operation is
     * performed immidiately and without locking. This method should
     * only be called in the transport thread.
     *
     * @param comp the component to remove.
     **/
    void RemoveComponent(FNET_IOComponent* comp);

    /**
     * Update time-out information for the given I/O component. This
     * method may only be called in the transport thread. Calling this
     * method will update the timestamp on the given IOC and perform a
     * remove/add IOC operation, putting it last in the time-out queue.
     *
     * @param comp component to update time-out info for.
     **/
    void UpdateTimeOut(FNET_IOComponent* comp);

    /**
     * Add an IOComponent to the delete list. This operation is
     * performed immidiately and without locking. This method should
     * only be called in the transport thread. NOTE: the IOC must be
     * removed from the list of active components before this method may
     * be called.
     *
     * @param comp the component to add to the delete list.
     **/
    void AddDeleteComponent(FNET_IOComponent* comp);

    /**
     * Delete (call internal_subref on) all IO Components in the delete list.
     **/
    void FlushDeleteList();

    /**
     * Post an event (ControlPacket) on the transport thread event
     * queue. This is done to tell the transport thread that it needs to
     * do an operation that could not be performed in other threads due
     * to thread-safety. If the event queue is empty, invoking this
     * method will wake up the transport thread in order to reduce
     * latency. Note that when posting events that have a reference
     * counted object as parameter you need to increase the reference
     * counter to ensure that the object will not be deleted before the
     * event is handled.
     *
     * @return true if the event was accepted (false if rejected)
     * @param cpacket the event command
     * @param context the event parameter
     **/
    bool PostEvent(FNET_ControlPacket* cpacket, FNET_Context context);

    /**
     * Discard an event. This method is used to discard events that will
     * not be handled due to shutdown.
     *
     * @param cpacket the event command
     * @param context the event parameter
     **/
    void DiscardEvent(FNET_ControlPacket* cpacket, FNET_Context context);

    /**
     * Obtain a reference to the object holding the configuration for
     * this transport object.
     *
     * @return config object.
     **/
    const FNET_Config&     getConfig() const;
    const fnet::TimeTools& time_tools() const;

    void handle_add_cmd(FNET_IOComponent* ioc);
    void handle_close_cmd(FNET_IOComponent* ioc);
    void handle_detach_server_adapter_init_cmd(FNET_IServerAdapter* server_adapter);
    void handle_detach_server_adapter_fini_cmd(FNET_IServerAdapter* server_adapter);

    /**
     * This method is called to initialize the transport thread event
     * loop. It is called from the FRT_Transport::Run method. If you
     * want to customize the event loop, you should do this by invoking
     * this method once, then invoke the @ref EventLoopIteration method
     * until it returns false (indicating transport shutdown).
     *
     * @return true on success, false on failure.
     **/
    bool InitEventLoop();

    void endEventLoop();
    void checkTimedoutComponents(vespalib::duration timeout);

    /**
     * Perform a single transport thread event loop iteration. This
     * method is called by the FRT_Transport::Run method. If you want to
     * customize the event loop, you should do this by invoking the @ref
     * InitEventLoop method once, then invoke this method until it
     * returns false (indicating transport shutdown).
     *
     * @return true when active, false after shutdown.
     **/
    bool EventLoopIteration();

    [[nodiscard]] bool should_shut_down() const noexcept { return _shutdown.load(std::memory_order_relaxed); }

    [[nodiscard]] bool is_finished() const noexcept { return _finished.load(std::memory_order_acquire); }

public:
    FNET_TransportThread(const FNET_TransportThread&) = delete;
    FNET_TransportThread& operator=(const FNET_TransportThread&) = delete;
    /**
     * Construct a transport object. To activate your newly created
     * transport object you need to call either the Start method to
     * spawn a new thread to handle IO, or the Main method to let the
     * current thread become the transport thread.
     *
     * @param owner owning transport layer
     **/
    explicit FNET_TransportThread(FNET_Transport& owner_in);

    /**
     * Destruct object. This should NOT be done before the transport
     * thread has completed it's work and raised the finished flag.
     **/
    ~FNET_TransportThread();

    /**
     * Obtain the owning transport layer
     *
     * @return transport layer owning this transport thread
     **/
    FNET_Transport& owner() const { return _owner; }

    /**
     * Tune the given socket handle to be used as an async transport
     * connection.
     **/
    bool tune(vespalib::SocketHandle& handle) const;

    /**
     * Add a network listener in an abstract way. The given 'spec'
     * string has the following format: 'type/where'. 'type' specifies
     * the protocol used; currently only 'tcp' is allowed, but it is
     * included for future extensions. 'where' specifies where to listen
     * in a way depending on the 'type' field; with tcp this field holds
     * a port number. Example: listen for tcp/ip connections on port
     * 8001: spec = 'tcp/8001'. If you want to enable strict binding you
     * may supply a hostname as well, like this:
     * 'tcp/mycomputer.mydomain:8001'.
     *
     * @return the connector object, or nullptr if listen failed.
     * @param spec string specifying how and where to listen.
     * @param streamer custom packet streamer.
     * @param serverAdapter object for custom channel creation.
     **/
    FNET_Connector* Listen(const char* spec, FNET_IPacketStreamer* streamer, FNET_IServerAdapter* serverAdapter);

    /**
     * Connect to a target host in an abstract way. The given 'spec'
     * string has the following format: 'type/where'. 'type' specifies
     * the protocol used; currently only 'tcp' is allowed, but it is
     * included for future extensions. 'where' specifies where to
     * connect in a way depending on the type field; with tcp this field
     * holds a host name (or IP address) and a port number. Example:
     * connect to www.fast.no on port 80 (using tcp/ip): spec =
     * 'tcp/www.fast.no:80'. The newly created connection will be
     * serviced by this transport layer object.
     *
     * @return an object representing the new connection.
     * @param spec string specifying how and where to connect.
     * @param streamer custom packet streamer.
     * @param serverAdapter adapter used to support 2way channel creation.
     * @param connContext application context for the connection.
     **/
    FNET_Connection* Connect(const char* spec, FNET_IPacketStreamer* streamer,
                             FNET_IServerAdapter* serverAdapter = nullptr, FNET_Context connContext = FNET_Context());

    /**
     * This method may be used to determine how many IO Components are
     * currently controlled by this transport layer object. Note that
     * locking is not used, since this information is volatile anyway.
     *
     * @return the current number of IOComponents.
     **/
    uint32_t GetNumIOComponents() const noexcept { return _componentCnt.load(std::memory_order_relaxed); }

    /**
     * Add an I/O component to the working set of this transport
     * object. Note that the actual work is performed by the transport
     * thread. This method simply posts an event on the transport thread
     * event queue. NOTE: in order to post async events regarding I/O
     * components, an extra reference to the component needs to be
     * allocated. The needRef flag indicates wether the caller already
     * has done this.
     *
     * @param comp the component you want to add.
     * @param needRef should be set to false if the caller of this
     *        method already has obtained an extra reference to the
     *        component. If this flag is true, this method will call the
     *        internal_addref method on the component.
     **/
    void Add(FNET_IOComponent* comp, bool needRef = true);

    /**
     * Calling this method enables write events for the given I/O
     * component. Note that the actual work is performed by the
     * transport thread. This method simply posts an event on the
     * transport thread event queue. NOTE: in order to post async events
     * regarding I/O components, an extra reference to the component
     * needs to be allocated. The needRef flag indicates wether the
     * caller already has done this.
     *
     * @param comp the component that wants write events.
     * @param needRef should be set to false if the caller of this
     *        method already has obtained an extra reference to the
     *        component. If this flag is true, this method will call the
     *        internal_addref method on the component.
     **/
    void EnableWrite(FNET_IOComponent* comp, bool needRef = true);

    /**
     * Signal the completion of an asyncronous handshake operation for
     * the given io component. Note that the actual work is performed
     * by the transport thread. This method simply posts an event on
     * the transport thread event queue. NOTE: in order to post async
     * events regarding I/O components, an extra reference to the
     * component needs to be allocated. The needRef flag indicates
     * wether the caller already has done this.
     *
     * @param comp the component to signal about operation completion
     * @param needRef should be set to false if the caller of this
     *        method already has obtained an extra reference to the
     *        component. If this flag is true, this method will call the
     *        internal_addref method on the component.
     **/
    void handshake_act(FNET_IOComponent* comp, bool needRef = true);

    /**
     * Close an I/O component and remove it from the working set of this
     * transport object. Note that the actual work is performed by the
     * transport thread. This method simply posts an event on the
     * transport thread event queue. NOTE: in order to post async events
     * regarding I/O components, an extra reference to the component
     * needs to be allocated. The needRef flag indicates wether the
     * caller already has done this.
     *
     * @param comp the component you want to close (and remove).
     * @param needRef should be set to false if the caller of this
     *        method already has obtained an extra reference to the
     *        component. If this flag is true, this method will call the
     *        internal_addref method on the component.
     **/
    void Close(FNET_IOComponent* comp, bool needRef = true);

    /**
     * Start the operation of detaching a server adapter from this
     * transport.
     **/
    void init_detach(FNET_IServerAdapter* server_adapter);

    /**
     * Complete the operation of detaching a server adapter from this
     * transport.
     **/
    void fini_detach(FNET_IServerAdapter* server_adapter);

    /**
     * Post an execution event on the transport event queue. The return
     * value from this method indicate whether the execution request was
     * accepted or not. If it was accepted, the transport thread will
     * execute the given executable at a later time. However, if it was
     * rejected (this method returns false), the caller of this method
     * will need to handle the fact that the executor will never be
     * executed. Also note that it is the responsibility of the caller
     * to ensure that all needed context for the executor is kept alive
     * until the time of execution. It is ok to assume that execution
     * requests will only be rejected due to transport thread shutdown.
     *
     * @return true if the execution request was accepted, false if it was rejected
     * @param exe the executable we want to execute in the transport thread
     **/
    bool execute(FNET_IExecutable* exe);

    /**
     * Synchronize with the transport thread. This method will block
     * until all events posted before this method was invoked has been
     * processed. If the transport thread has been shut down (or is in
     * the progress of being shut down) this method will instead wait
     * for the transport thread to complete, since no more commands will
     * be performed, and waiting would be forever. Invoking this method
     * from the transport thread is not a good idea.
     **/
    void sync();

    /**
     * Obtain a pointer to the transport thread scheduler. This
     * scheduler may be used to schedule tasks to be run by the
     * transport thread.
     *
     * @return transport thread scheduler.
     **/
    FNET_Scheduler* GetScheduler() { return &_scheduler; }

    /**
     * Calling this method will shut down the transport layer in a nice
     * way. Note that the actual task of shutting down is performed by
     * the transport thread. This method simply posts an event on the
     * transport thread event queue telling it to shut down.
     *
     * @param waitFinished if this flag is set, the method call will not
     *        return until the transport layer is shut down. NOTE: do
     *        not set this flag if you are calling this method from a
     *        callback from the transport layer, as it will create a
     *        deadlock.
     **/
    void ShutDown(bool waitFinished);

    /**
     * This method will make the calling thread wait until the transport
     * layer has been shut down. NOTE: do not invoke this method if you
     * are in a callback from the transport layer, as it will create a
     * deadlock. See @ref ShutDown.
     **/
    void WaitFinished();

    // selector call-back for wakeup events
    void handle_wakeup();

    // selector call-back for io-events
    void handle_event(FNET_IOComponent& ctx, bool read, bool write);

    /**
     * Start transport layer operation in a separate thread. Note that
     * the return value of this method only indicates whether the
     * spawning of the new thread went ok.
     *
     * @return thread create status.
     * @param pool threadpool that may be used to spawn a new thread.
     **/
    bool Start(vespalib::ThreadPool& pool);

    /**
     * Calling this method will give the current thread to the transport
     * layer. The method will not return until the transport layer is
     * shut down by calling the @ref ShutDown method.
     **/
    void Main();

    /**
     * This is where the transport thread lives, when started by
     * invoking one of the @ref Main or @ref Start methods.
     **/
    void run();
};
