// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "transport_metrics.h"
#include "source_description.h"
#include "searchapi.h"
#include "docsumapi.h"
#include "monitorapi.h"
#include <vespa/searchlib/common/packets.h>
#include <vespa/fnet/iserveradapter.h>
#include <vespa/fnet/ipackethandler.h>
#include <vespa/fnet/task.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/fastos/thread.h>
#include <set>
#include <queue>

namespace search::engine {

/**
 * Common transport server implementation interacting with the
 * underlying search engine using the common search api. This
 * implementation has less optimization tricks compared to the
 * previous ones being integrated into specific applications.
 **/
class TransportServer : public FastOS_Runnable,
                        public FNET_IServerAdapter,
                        public FNET_IPacketHandler
{
private:
    TransportServer(const TransportServer &);
    TransportServer &operator=(const TransportServer &);

    /**
     * Task used to update listen status
     **/
    struct ListenTask : public FNET_Task
    {
        TransportServer &parent;
        ListenTask(TransportServer &p) : FNET_Task(p._transport.GetScheduler()), parent(p) {}
        void PerformTask() override { parent.updateListen(); }
    };

    /**
     * Task used to dispatch incoming requests in an untangled way
     * (aka not in the packet callback).
     **/
    struct DispatchTask : public FNET_Task
    {
        TransportServer &parent;
        DispatchTask(TransportServer &p) : FNET_Task(p._transport.GetScheduler()), parent(p) {}
        void PerformTask() override {
            parent.dispatchRequests();
            ScheduleNow(); // run each tick
        }
    };

    class Handler;

    SearchServer           &_searchServer;
    DocsumServer           &_docsumServer;
    MonitorServer          &_monitorServer;
    FNET_Transport          _transport;
    bool                    _ready;        // flag indicating initial readyness
    bool                    _failed;       // flag indicating a critical failure
    bool                    _doListen;     // flag telling us to accept requests or not
    FastOS_ThreadPool       _threadPool;   // thread pool owning transport thread
    SourceDescription       _sourceDesc;   // description of where requests are coming from
    vespalib::string        _listenSpec;   // where to listen; FNET connect spec
    FNET_Connector         *_listener;     // object accepting incoming connections
    std::set<FNET_Channel*> _clients;      // the admin channel of all client connections
    std::queue<Handler*>    _pending;      // queue of incoming requests not yet started
    DispatchTask            _dispatchTask; // task used to dispatch incoming requests
    ListenTask              _listenTask;   // task used to update listen status
    uint32_t                _connTag;      // sequential number used to tag connections
    uint32_t                _debugMask;    // enable more debug logging with this
    TransportMetrics        _metrics;      // metrics for this transport server

    /**
     * Toplevel class used to wrap incoming requests. Actual objects
     * are used both to delay starting the request until we are not in
     * the packet delivery callback and also as the callback target
     * used by the underlying api objects to notify completion of
     * individual requests.
     **/
    struct Handler
    {
        TransportServer &parent;
        uint32_t _debugMask;
        Handler(TransportServer &p) : parent(p), _debugMask(p._debugMask) {}
        bool shouldLog(uint32_t msgType) { return parent.shouldLog(msgType); } // possible thread issue
        virtual void start() = 0;
        virtual ~Handler() {}
    private:
        Handler(const Handler &rhs);
        Handler &operator=(const Handler &rhs);
    };

    /**
     * Wrapper for search requests
     **/
    struct SearchHandler : public Handler,
                           public SearchClient
    {
        SearchRequest::Source request;
        FNET_Channel     *channel;
        uint32_t          clientCnt;

        SearchHandler(TransportServer &p, SearchRequest::Source req, FNET_Channel *ch, uint32_t cnt)
            : Handler(p), request(std::move(req)), channel(ch), clientCnt(cnt) {}
        void start() override;
        void searchDone(SearchReply::UP reply) override;
        ~SearchHandler();
    };

    /**
     * Wrapper for docsum requests
     **/
    struct DocsumHandler : public Handler,
                           public DocsumClient
    {
        DocsumRequest::Source request;
        FNET_Channel     *channel;

        DocsumHandler(TransportServer &p, DocsumRequest::Source req, FNET_Channel *ch)
            : Handler(p), request(std::move(req)), channel(ch) {}
        void start() override;
        void getDocsumsDone(DocsumReply::UP reply) override;
        ~DocsumHandler();
    };

    /**
     * Wrapper for monitor requests
     **/
    struct MonitorHandler : public Handler,
                            public MonitorClient
    {
        MonitorRequest::UP request;
        FNET_Connection   *connection;

        MonitorHandler(TransportServer &p, MonitorRequest::UP req, FNET_Connection *conn)
            : Handler(p), request(std::move(req)), connection(conn) {}
        void start() override;
        void pingDone(MonitorReply::UP reply) override;
        ~MonitorHandler();
    };

    // handle incoming network packets
    HP_RetCode HandlePacket(FNET_Packet *packet, FNET_Context context) override;

    // set up admin channel for new clients
    bool InitAdminChannel(FNET_Channel *channel) override;

    // set up channel for individual request
    bool InitChannel(FNET_Channel *channel, uint32_t pcode) override;

    // entry point for thread running transport thread
    void Run(FastOS_ThreadInterface *thisThread, void *arg) override;

    // update listen status
    bool updateListen();

    // dispatch incoming requests
    void dispatchRequests();

    // discard any pending requests during shutdown
    void discardRequests();

    // convenience method used to log packets
    static void logPacket(const vespalib::stringref &msg, FNET_Packet *p, FNET_Channel *ch, FNET_Connection *conn);

    void updateQueryMetrics(double latency_s);
    void updateDocsumMetrics(double latency_s, uint32_t numDocs);

public:
    /**
     * Convenience typedes.
     */
    typedef std::unique_ptr<TransportServer> UP;
    typedef std::shared_ptr<TransportServer> SP;

    /** no debug logging flags set **/
    static constexpr uint32_t DEBUG_NONE       = 0x00000000;

    /** log connect disconnect from clients **/
    static constexpr uint32_t DEBUG_CONNECTION = 0x00000001;

    /** log channel open events **/
    static constexpr uint32_t DEBUG_CHANNEL    = 0x00000002;

    /** log search related packets **/
    static constexpr uint32_t DEBUG_SEARCH     = 0x00000004;

    /** log docsum related packets **/
    static constexpr uint32_t DEBUG_DOCSUM     = 0x00000008;

    /** log monitor related packets **/
    static constexpr uint32_t DEBUG_MONITOR    = 0x00000010;

    /** log unhandled packets **/
    static constexpr uint32_t DEBUG_UNHANDLED  = 0x00000020;

    /** all debug logging flags set **/
    static constexpr uint32_t DEBUG_ALL        = 0x0000003f;

    /**
     * Check if we should log a debug message
     *
     * @return true if we should log a message for this event
     * @param msgType the event we might want to log
     **/
    bool shouldLog(uint32_t msgType);

    /**
     * Create a transport server based on the given underlying api
     * objects. An appropriate debug mask can be made by or'ing
     * together the appropriate DEBUG_ constants defined in this
     * class.
     *
     * @param searchServer search api
     * @param docsumServer docsum api
     * @param monitorServer monitor api
     * @param port listen port.
     * @param debugMask mask indicating what information should be logged as debug messages.
     **/
    TransportServer(SearchServer &searchServer,
                    DocsumServer &docsumServer,
                    MonitorServer &monitorServer,
                    int port, uint32_t debugMask = DEBUG_NONE);

    /**
     * Obtain the metrics used by this transport server.
     *
     * @return internal metrics
     **/
    TransportMetrics &getMetrics() { return _metrics; }

    /**
     * Obtain the listen spec used by this transport server
     *
     * @return listen spec
     **/
    const vespalib::string &getListenSpec() const { return _listenSpec; }

    /**
     * Start this server.
     *
     * @return success(true)/failure(false)
     **/
    bool start();

    /**
     * Check for initial readyness.
     *
     * @return true if we are ready.
     **/
    bool isReady() const { return _ready;  }

    /**
     * Check if a critical error has occurred.
     *
     * @return true if something bad has happened.
     **/
    bool isFailed() const { return _failed;  }

    /**
     * Get a reference to the internal fnet scheduler.
     *
     * @return fnet scheduler
     **/
    FNET_Scheduler &getScheduler() { return *(_transport.GetScheduler()); }

    /**
     * Set a flag indicating whether we should accept incoming
     * requests or not. Setting the flag to false will make this
     * server unavailable to any client application.
     *
     * @param listen flag indicating if we should listen
     **/
    void setListen(bool listen) {
        _doListen = listen;
        _listenTask.ScheduleNow();
    }

    /**
     * Check which port this server is currently listening to. This
     * method is useful when using automatically allocated port
     * numbers (listening to port 0).
     *
     * @return current listening port number, -1 if not listening.
     **/
    int getListenPort();

    /**
     * Enable or disable nagles algorithm.
     *
     * @param noDelay set to true to disable nagles algorithm
     **/
    void setTCPNoDelay(bool noDelay) { _transport.SetTCPNoDelay(noDelay); }

    /**
     * Enable or disable the use of a Q for throughput between search thread and network thread.
     *
     * @param directWrite bypasses Q
     **/
    void setDirectWrite(bool directWrite) { _transport.SetDirectWrite(directWrite); }

    /**
     * Set a limit on how long a connection may be idle before closing it.
     *
     * @param millisecs max idle time in milliseconds
     **/
    void setIdleTimeout(double millisecs) { _transport.SetIOCTimeOut((uint32_t) millisecs); }

    /**
     * Shut down this component. This method will block until the
     * transport server has been shut down. After this method returns,
     * no new requests will be generated by this component.
     **/
    void shutDown() {
        _transport.ShutDown(false);
        _threadPool.Close();
    }

    /**
     * Destructor will perform shutdown if needed.
     **/
    ~TransportServer();
};

}

