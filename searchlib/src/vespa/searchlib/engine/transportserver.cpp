// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "packetconverter.h"
#include "transportserver.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/fnet/channel.h>
#include <vespa/fnet/connection.h>
#include <vespa/fnet/connector.h>
#include <vespa/fnet/iexecutable.h>

#include <vespa/log/log.h>
LOG_SETUP(".engine.transportserver");

namespace search::engine {

//-----------------------------------------------------------------------------

typedef search::fs4transport::FS4PersistentPacketStreamer PacketStreamer;

//-----------------------------------------------------------------------------

constexpr uint32_t TransportServer::DEBUG_NONE;
constexpr uint32_t TransportServer::DEBUG_CONNECTION;
constexpr uint32_t TransportServer::DEBUG_CHANNEL;
constexpr uint32_t TransportServer::DEBUG_SEARCH;
constexpr uint32_t TransportServer::DEBUG_DOCSUM;
constexpr uint32_t TransportServer::DEBUG_MONITOR;
constexpr uint32_t TransportServer::DEBUG_UNHANDLED;
constexpr uint32_t TransportServer::DEBUG_ALL;

void
TransportServer::SearchHandler::start()
{
    SearchReply::UP reply = parent._searchServer.search(std::move(request), *this);
    if (reply) {
        searchDone(std::move(reply));
    }
}

void
TransportServer::SearchHandler::searchDone(SearchReply::UP reply)
{
    if (reply) {
        const SearchReply &r = *reply;
        if (r.valid) {
            if (r.errorCode == 0) {
                PacketConverter::QUERYRESULTX *p = new PacketConverter::QUERYRESULTX();
                PacketConverter::fromSearchReply(r, *p);
                if (shouldLog(DEBUG_SEARCH)) {
                    logPacket("outgoing packet", p, channel, 0);
                }
                channel->Send(p);
            } else {
                PacketConverter::ERROR *p = new PacketConverter::ERROR();
                p->_errorCode = r.errorCode;
                p->setErrorMessage(r.errorMessage);
                if (shouldLog(DEBUG_SEARCH)) {
                    logPacket("outgoing packet", p, channel, 0);
                }
                channel->Send(p);
            }
            if (r.request) {
                parent.updateQueryMetrics(r.request->getTimeUsed().sec()); // possible thread issue
            }
        } else {
            PacketConverter::EOL *p = new PacketConverter::EOL();
            if (shouldLog(DEBUG_SEARCH)) {
                logPacket("outgoing packet", p, channel, 0);
            }
            channel->Send(p);
        }
    } else {
        LOG(warning, "got <null> search reply from back-end");
    }
    delete this; // we are done
}

TransportServer::SearchHandler::~SearchHandler()
{
    channel->Free();
}

//-----------------------------------------------------------------------------

void
TransportServer::DocsumHandler::start()
{
    DocsumReply::UP reply = parent._docsumServer.getDocsums(std::move(request), *this);
    if (reply) {
        getDocsumsDone(std::move(reply));
    }
}

void
TransportServer::DocsumHandler::getDocsumsDone(DocsumReply::UP reply)
{
    if (reply) {
        const DocsumReply &r = *reply;
        for (uint32_t i = 0; i < r.docsums.size(); ++i) {
            PacketConverter::DOCSUM *p = new PacketConverter::DOCSUM();
            PacketConverter::fromDocsumReplyElement(r.docsums[i], *p);
            if (shouldLog(DEBUG_DOCSUM)) {
                logPacket("outgoing packet", p, channel, 0);
            }
            channel->Send(p);
        }
        PacketConverter::EOL *p = new PacketConverter::EOL();
        if (shouldLog(DEBUG_DOCSUM)) {
            logPacket("outgoing packet", p, channel, 0);
        }
        channel->Send(p);
        if (r.request) {
            parent.updateDocsumMetrics(r.request->getTimeUsed().sec(), r.docsums.size());
        }
    } else {
        LOG(warning, "got <null> docsum reply from back-end");
    }
    delete this; // we are done
}

TransportServer::DocsumHandler::~DocsumHandler()
{
    channel->Free();
}

//-----------------------------------------------------------------------------

void
TransportServer::MonitorHandler::start()
{
    MonitorReply::UP reply = parent._monitorServer.ping(std::move(request), *this);
    if (reply) {
        pingDone(std::move(reply));
    }
}

void
TransportServer::MonitorHandler::pingDone(MonitorReply::UP reply)
{
    if (reply) {
        const MonitorReply &r = *reply;
        PacketConverter::MONITORRESULTX *p = new PacketConverter::MONITORRESULTX();
        PacketConverter::fromMonitorReply(r, *p);
        if (shouldLog(DEBUG_MONITOR)) {
            logPacket("outgoing packet", p, 0, connection);
        }
        connection->PostPacket(p, FNET_NOID);
    } else {
        LOG(warning, "got <null> monitor reply from back-end");
    }
    delete this; // we are done
}

TransportServer::MonitorHandler::~MonitorHandler()
{
    connection->SubRef();
}

//-----------------------------------------------------------------------------

FNET_IPacketHandler::HP_RetCode
TransportServer::HandlePacket(FNET_Packet *packet, FNET_Context context)
{
    uint32_t pcode = packet->GetPCODE();
    FNET_Channel *channel = context._value.CHANNEL;
    HP_RetCode rc = FNET_FREE_CHANNEL;

    if (channel->GetID() == FNET_NOID) { // admin packet
        if (packet->IsChannelLostCMD()) {
            _clients.erase(channel);
            if (shouldLog(DEBUG_CONNECTION)) {
                LOG(debug, "connection closed: tag=%u", channel->GetConnection()->GetContext()._value.INT);
            }
        } else if (pcode == search::fs4transport::PCODE_MONITORQUERYX) {
            const PacketConverter::MONITORQUERYX &mqx = static_cast<PacketConverter::MONITORQUERYX&>(*packet);
            if (shouldLog(DEBUG_MONITOR)) {
                logPacket("incoming packet", packet, channel, 0);
            }
            MonitorRequest::UP req(new MonitorRequest());
            PacketConverter::toMonitorRequest(mqx, *req);
            channel->GetConnection()->AddRef();
            _pending.push(new MonitorHandler(*this, std::move(req), channel->GetConnection()));
            rc = FNET_KEEP_CHANNEL;
        } else if (shouldLog(DEBUG_UNHANDLED)) {
            logPacket("unhandled packet", packet, channel, 0);
        }
    } else {                             // search/docsum request
        if (pcode == search::fs4transport::PCODE_QUERYX) {
            PacketConverter::QUERYX * qx = static_cast<PacketConverter::QUERYX *>(packet);
            if (shouldLog(DEBUG_SEARCH)) {
                logPacket("incoming packet", packet, channel, 0);
            }
            SearchRequest::Source req(qx, _sourceDesc);
            packet = NULL;
            _pending.push(new SearchHandler(*this, std::move(req), channel, _clients.size()));
            rc = FNET_CLOSE_CHANNEL;
        } else if (pcode == search::fs4transport::PCODE_GETDOCSUMSX) {
            PacketConverter::GETDOCSUMSX * gdx = static_cast<PacketConverter::GETDOCSUMSX *>(packet);
            if (shouldLog(DEBUG_DOCSUM)) {
                logPacket("incoming packet", packet, channel, 0);
            }
            DocsumRequest::Source req(gdx, _sourceDesc);
            packet = NULL;
            _pending.push(new DocsumHandler(*this, std::move(req), channel));
            rc = FNET_CLOSE_CHANNEL;
        } else if (shouldLog(DEBUG_UNHANDLED)) {
            logPacket("unhandled packet", packet, channel, 0);
        }
    }
    if (packet != NULL) {
        packet->Free();
    }
    return rc;
}

bool
TransportServer::InitAdminChannel(FNET_Channel *channel)
{
    if (_listener == NULL) {
        // handle race where we get an incoming connection and
        // disables listening at the 'same time'. Note that sync close
        // is only allowed in the InitAdminChannel method
        channel->GetConnection()->Close(); // sync close
        return false;
    }
    channel->SetContext(channel);
    channel->SetHandler(this);
    assert(_clients.count(channel) == 0);
    _clients.insert(channel);
    channel->GetConnection()->SetContext(FNET_Context(++_connTag));
    if (shouldLog(DEBUG_CONNECTION)) {
        LOG(debug, "connection established: tag=%u", _connTag);
    }
    return true;
}

bool
TransportServer::InitChannel(FNET_Channel *channel, uint32_t pcode)
{
    channel->SetContext(channel);
    channel->SetHandler(this);
    if (shouldLog(DEBUG_CHANNEL)) {
        LOG(debug, "new channel: id=%u, first pcode=%u", channel->GetID(), pcode);
    }
    return true;
}

void
TransportServer::Run(FastOS_ThreadInterface *, void *)
{
    _dispatchTask.ScheduleNow();
    _ready = true;
    _transport.Main(); // <- transport event loop
    _dispatchTask.Kill();
    _listenTask.Kill();
    discardRequests();
}

bool
TransportServer::updateListen()
{
    bool doListen = _doListen;
    if (doListen) {
        if (_listener == NULL) { // start listening
            _listener = _transport.Listen(_listenSpec.c_str(), &PacketStreamer::Instance, this);
            if (_listener == NULL) {
                LOG(error, "Could not bind fnet transport socket to %s", _listenSpec.c_str());
                _failed = true;
                return false;
            }
        }
    } else {
        if (_listener != NULL) { // stop listening
            _transport.Close(_listener); // async close
            _listener->SubRef();
            _listener = NULL;
            // also close client connections
            std::set<FNET_Channel*>::iterator it = _clients.begin();
            for (; it != _clients.end(); ++it) {
                _transport.Close((*it)->GetConnection()); // async close
            }
        }
    }
    return true;
}

void
TransportServer::dispatchRequests()
{
    while (!_pending.empty()) {
        Handler *h = _pending.front();
        _pending.pop();
        h->start();
    }
}

void
TransportServer::discardRequests()
{
    while (!_pending.empty()) {
        Handler *h = _pending.front();
        _pending.pop();
        delete h;
    }
}

void
TransportServer::logPacket(const vespalib::stringref &msg, FNET_Packet *p, FNET_Channel *ch, FNET_Connection *conn)
{
    uint32_t chid = -1;
    uint32_t conntag = -1;
    vespalib::string str;
    if (ch != 0) {
        chid = ch->GetID();
        conntag = ch->GetConnection()->GetContext()._value.INT;
    } else if (conn != 0) {
        conntag = conn->GetContext()._value.INT;
    }
    search::fs4transport::FS4Packet *fs4p = dynamic_cast<search::fs4transport::FS4Packet*>(p);
    if (fs4p != 0) {
        str = fs4p->toString(0);
    } else {
        str = vespalib::make_string("packet { pcode=%u }", p->GetPCODE());
    }
    LOG(debug, "%s (chid=%u, conn=%u):\n%s", msg.c_str(), chid, conntag, str.c_str());
}

void
TransportServer::updateQueryMetrics(double latency_s)
{
    vespalib::LockGuard guard(_metrics.updateLock);
    _metrics.query.count.inc();
    _metrics.query.latency.set(latency_s);
}

void
TransportServer::updateDocsumMetrics(double latency_s, uint32_t numDocs)
{
    vespalib::LockGuard guard(_metrics.updateLock);
    _metrics.docsum.count.inc();
    _metrics.docsum.docs.inc(numDocs);
    _metrics.docsum.latency.set(latency_s);
}

//-----------------------------------------------------------------------------

bool
TransportServer::shouldLog(uint32_t msgType) {
    return (((msgType & _debugMask) != 0)
            && ((msgType != DEBUG_MONITOR && LOG_WOULD_LOG(debug)) ||
                (msgType == DEBUG_MONITOR && LOG_WOULD_LOG(spam))));
}

TransportServer::TransportServer(SearchServer &searchServer,
                                 DocsumServer &docsumServer,
                                 MonitorServer &monitorServer,
                                 int port, uint32_t debugMask)
    : _searchServer(searchServer),
      _docsumServer(docsumServer),
      _monitorServer(monitorServer),
      _transport(),
      _ready(false),
      _failed(false),
      _doListen(true),
      _threadPool(256 * 1024),
      _sourceDesc(port),
      _listenSpec(),
      _listener(0),
      _clients(),
      _pending(),
      _dispatchTask(*this),
      _listenTask(*this),
      _connTag(0),
      _debugMask(debugMask),
      _metrics()
{
    _listenSpec = vespalib::make_string("tcp/%d", port);
}

bool
TransportServer::start()
{
    if (!updateListen()) {
        return false;
    }
    if (_threadPool.NewThread(this) == 0) {
        LOG(error, "Could not start internal transport thread");
        _failed = true;
        return false;
    }
    return true;
}

int
TransportServer::getListenPort()
{
    struct Cmd : public FNET_IExecutable {
        TransportServer &server;
        vespalib::Gate   done;
        int              port;
        Cmd(TransportServer &s) : server(s), done(), port(-1) {}
        void execute() override {
            if (server._listener != 0) {
                port = server._listener->GetPortNumber();
            }
            done.countDown();
        }
    };
    Cmd cmd(*this);
    if (_transport.execute(&cmd)) {
        cmd.done.await();
    }
    return cmd.port;
};

TransportServer::~TransportServer()
{
    shutDown(); // ensure shutdown
    if (_listener != 0) {
        _listener->SubRef();
        _listener = 0;
    }
}

}

