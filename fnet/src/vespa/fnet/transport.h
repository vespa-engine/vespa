// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "config.h"
#include "context.h"

#include <vespa/vespalib/net/async_resolver.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/time.h>

class FNET_TransportThread;
class FNET_Connector;
class FNET_IPacketStreamer;
class FNET_IServerAdapter;
class FNET_IPacketHandler;
class FNET_Scheduler;

namespace fnet {

/**
 * Low-level abstraction for event-loop time management. The
 * event_timeout function returns the timeout to be used when waiting
 * for io-events. The current_time function returns the current
 * time. This interface may be implemented to control both how time is
 * spent (event_timeout) as well as how time is observed
 * (current_time). The default implementation will use
 * FNET_Scheduler::tick_ms as event timeout and
 * vespalib::steady_clock::now() as current time.
 **/
struct TimeTools {
    using SP = std::shared_ptr<TimeTools>;
    virtual vespalib::duration    event_timeout() const = 0;
    virtual vespalib::steady_time current_time() const = 0;
    virtual ~TimeTools() = default;
    static TimeTools::SP make_debug(
        vespalib::duration event_timeout, std::function<vespalib::steady_time()> current_time);
};

class TransportConfig {
public:
    TransportConfig() : TransportConfig(1) {}
    explicit TransportConfig(int num_threads);
    ~TransportConfig();
    vespalib::AsyncResolver::SP resolver() const;
    vespalib::CryptoEngine::SP  crypto() const;
    fnet::TimeTools::SP         time_tools() const;
    TransportConfig&            resolver(vespalib::AsyncResolver::SP resolver_in) {
        _resolver = std::move(resolver_in);
        return *this;
    }
    TransportConfig& crypto(vespalib::CryptoEngine::SP crypto_in) {
        _crypto = std::move(crypto_in);
        return *this;
    }
    TransportConfig& time_tools(fnet::TimeTools::SP time_tools_in) {
        _time_tools = std::move(time_tools_in);
        return *this;
    }

    const FNET_Config& config() const { return _config; }
    uint32_t           num_threads() const { return _num_threads; }

    TransportConfig& events_before_wakeup(uint32_t v) {
        if (v > 1) {
            _config._events_before_wakeup = v;
        }
        return *this;
    }
    TransportConfig& maxInputBufferSize(uint32_t v) {
        _config._maxInputBufferSize = v;
        return *this;
    }
    TransportConfig& maxOutputBufferSize(uint32_t v) {
        _config._maxOutputBufferSize = v;
        return *this;
    }
    TransportConfig& tcpNoDelay(bool v) {
        _config._tcpNoDelay = v;
        return *this;
    }
    TransportConfig& drop_empty_buffers(bool v) {
        _config._drop_empty_buffers = v;
        return *this;
    }

private:
    FNET_Config                 _config;
    vespalib::AsyncResolver::SP _resolver;
    vespalib::CryptoEngine::SP  _crypto;
    fnet::TimeTools::SP         _time_tools;
    uint32_t                    _num_threads;
};

} // namespace fnet

/**
 * This class represents the transport layer and handles a collection
 * of transport threads. Note: remember to shut down your transport
 * layer appropriately before deleting it.
 **/
class FNET_Transport {
private:
    using Thread = std::unique_ptr<FNET_TransportThread>;
    using Threads = std::vector<Thread>;

    vespalib::AsyncResolver::SP                       _async_resolver;
    vespalib::CryptoEngine::SP                        _crypto_engine;
    fnet::TimeTools::SP                               _time_tools;
    std::unique_ptr<vespalib::SyncableThreadExecutor> _work_pool;
    Threads                                           _threads;
    vespalib::ThreadPool                              _pool;
    const FNET_Config                                 _config;

    /**
     * Wait for all pending resolve requests.
     **/
    void wait_for_pending_resolves();

public:
    FNET_Transport(const FNET_Transport&) = delete;
    FNET_Transport& operator=(const FNET_Transport&) = delete;
    /**
     * Construct a transport layer. To activate your newly created
     * transport object you need to call either the Start method to
     * spawn a new thread(s) to handle IO, or the Main method to let
     * the current thread become the transport thread. Main may only
     * be called for single-threaded transports.
     **/
    explicit FNET_Transport(const fnet::TransportConfig& config);

    explicit FNET_Transport(uint32_t num_threads) : FNET_Transport(fnet::TransportConfig(num_threads)) {}
    FNET_Transport() : FNET_Transport(fnet::TransportConfig()) {}
    ~FNET_Transport();

    const FNET_Config&     getConfig() const { return _config; }
    const fnet::TimeTools& time_tools() const { return *_time_tools; }

    /**
     * Try to execute the given task on the internal work pool
     * executor (post). If the executor has been closed or there is
     * too much pending work the task is run in the context of the
     * current thread instead (perform). The idea is to move work away
     * from the transport threads as long as pending work is not
     * piling up.
     *
     * @param task work to be done
     **/
    void post_or_perform(vespalib::Executor::Task::UP task);

    /**
     * Resolve a connect spec into a socket address to be used to
     * connect to a remote socket. This operation will be performed
     * asynchronously and the result will be given to the result
     * handler when ready. The result handler may be discarded to
     * cancel the request.
     *
     * @param spec connect spec
     * @param result handler
     **/
    void resolve_async(const std::string& spec, vespalib::AsyncResolver::ResultHandler::WP result_handler);

    /**
     * Wrap a plain socket endpoint (client side) in a CryptoSocket. The
     * implementation will be determined by the CryptoEngine used by
     * this Transport.
     *
     * @return socket abstraction able to perform encryption and decryption
     * @param socket low-level socket
     * @param spec who we are connecting to
     **/
    vespalib::CryptoSocket::UP create_client_crypto_socket(
        vespalib::SocketHandle socket, const vespalib::SocketSpec& spec);

    /**
     * Wrap a plain socket endpoint (server side) in a CryptoSocket. The
     * implementation will be determined by the CryptoEngine used by
     * this Transport.
     *
     * @return socket abstraction able to perform encryption and decryption
     * @param socket low-level socket
     **/
    vespalib::CryptoSocket::UP create_server_crypto_socket(vespalib::SocketHandle socket);

    /**
     * Select one of the underlying transport threads. The selection
     * is based on hashing the given key as well as the current stack
     * pointer.
     *
     * @return selected transport thread
     **/
    FNET_TransportThread* select_thread(const void* key, size_t key_len) const;

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
    uint32_t GetNumIOComponents();

    /**
     * Synchronize with all transport threads. This method will block
     * until all events posted before this method was invoked has been
     * processed. If a transport thread has been shut down (or is in
     * the progress of being shut down) this method will instead wait
     * for the transport thread to complete, since no more commands
     * will be performed, and waiting would be forever. Invoking this
     * method from a transport thread is not a good idea.
     **/
    void sync();

    /**
     * Detach a server adapter from this transport.
     *
     * This will close all connectors and connections referencing the
     * server adapter. Note that this function will also synchronize
     * with async address resolving and underlying transport threads.
     **/
    void detach(FNET_IServerAdapter* server_adapter);

    /**
     * Obtain a pointer to a transport thread scheduler.
     *
     * @return transport thread scheduler.
     **/
    FNET_Scheduler* GetScheduler();

    /**
     * Post an execution event on one of the transport threads. The
     * return value from this method indicate whether the execution
     * request was accepted or not. If it was accepted, the transport
     * thread will execute the given executable at a later
     * time. However, if it was rejected (this method returns false),
     * the caller of this method will need to handle the fact that the
     * executor will never be executed. Also note that it is the
     * responsibility of the caller to ensure that all needed context
     * for the executor is kept alive until the time of execution. It
     * is ok to assume that execution requests will only be rejected
     * due to transport thread shutdown. Calling sync will ensure that
     * all previously posted execution events are handled.
     *
     * @return true if the execution request was accepted, false if it was rejected
     * @param exe the executable we want to execute in any transport thread
     **/
    bool execute(FNET_IExecutable* exe);

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

    /**
     * Start transport threads. Note that the return value of this
     * method only indicates whether the spawning of new threads went
     * ok.
     *
     * @return thread create status.
     **/
    bool Start();

    /**
     * Capture transport threads. Used for testing purposes,
     * preferably combined with a debug variant of TimeTools.
     *
     * After this function is called, the capture_hook will be called
     * repeatedly as long as it returns true. The first time it
     * returns false, appropriate cleanup will be performed and the
     * capture_hook will never be called again; it detaches
     * itself. All transport threads will be blocked while the
     * capture_hook is called. Between calls to the capture_hook each
     * transport thread will run its event loop exactly once, all
     * pending work in the work pool will be performed and all pending
     * dns lookups will be performed. Note that the capture_hook
     * should detach itself by returning false before the transport
     * itself is shut down.
     *
     * @param capture_hook called until it returns false
     **/
    void attach_capture_hook(std::function<bool()> capture_hook);

    //-------------------------------------------------------------------------
    // forward async IO Component operations to their owners
    //-------------------------------------------------------------------------

    static void Add(FNET_IOComponent* comp, bool needRef = true);
    static void Close(FNET_IOComponent* comp, bool needRef = true);

    //-------------------------------------------------------------------------
    // single-threaded API forwarding. num_threads must be 1. Note: Choose
    // only one of: (a) Start, (b) Main
    // -------------------------------------------------------------------------

    void Main();
};
