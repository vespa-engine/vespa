// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_thread.h"

#include "connection.h"
#include "connector.h"
#include "controlpacket.h"
#include "iexecutable.h"
#include "iocomponent.h"
#include "transport.h"

#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/gate.h>

#include <csignal>

#include <vespa/log/log.h>
LOG_SETUP(".fnet");

using vespalib::ServerSocket;
using vespalib::SocketHandle;
using vespalib::SocketSpec;
using vespalib::steady_clock;
using namespace vespalib::atomic;

namespace {

struct Sync : public FNET_IExecutable {
    vespalib::Gate gate;
    void           execute() override { gate.countDown(); }
};

} // namespace

void FNET_TransportThread::AddComponent(FNET_IOComponent* comp) {
    if (comp->ShouldTimeOut()) {
        comp->_ioc_prev = _componentsTail;
        comp->_ioc_next = nullptr;
        if (_componentsTail == nullptr) {
            _componentsHead = comp;
        } else {
            _componentsTail->_ioc_next = comp;
        }
        _componentsTail = comp;
        if (_timeOutHead == nullptr)
            _timeOutHead = comp;
        store_relaxed(_componentCnt, load_relaxed(_componentCnt) + 1);
    } else {
        comp->_ioc_prev = nullptr;
        comp->_ioc_next = _componentsHead;
        if (_componentsHead == nullptr) {
            _componentsTail = comp;
        } else {
            _componentsHead->_ioc_prev = comp;
        }
        _componentsHead = comp;
        store_relaxed(_componentCnt, load_relaxed(_componentCnt) + 1);
    }
}

void FNET_TransportThread::RemoveComponent(FNET_IOComponent* comp) {
    if (comp == _componentsHead)
        _componentsHead = comp->_ioc_next;
    if (comp == _timeOutHead)
        _timeOutHead = comp->_ioc_next;
    if (comp == _componentsTail)
        _componentsTail = comp->_ioc_prev;
    if (comp->_ioc_prev != nullptr)
        comp->_ioc_prev->_ioc_next = comp->_ioc_next;
    if (comp->_ioc_next != nullptr)
        comp->_ioc_next->_ioc_prev = comp->_ioc_prev;
    store_relaxed(_componentCnt, load_relaxed(_componentCnt) - 1);
}

void FNET_TransportThread::UpdateTimeOut(FNET_IOComponent* comp) {
    comp->_ioc_timestamp = _now;
    RemoveComponent(comp);
    AddComponent(comp);
}

void FNET_TransportThread::AddDeleteComponent(FNET_IOComponent* comp) {
    assert(!comp->_flags._ioc_delete);
    comp->_flags._ioc_delete = true;
    comp->_ioc_prev = nullptr;
    comp->_ioc_next = _deleteList;
    _deleteList = comp;
}

void FNET_TransportThread::FlushDeleteList() {
    while (_deleteList != nullptr) {
        FNET_IOComponent* tmp = _deleteList;
        _deleteList = tmp->_ioc_next;
        assert(tmp->_flags._ioc_delete);
        tmp->internal_subref();
    }
}

bool FNET_TransportThread::PostEvent(FNET_ControlPacket* cpacket, FNET_Context context) {
    size_t qLen;
    {
        std::unique_lock<std::mutex> guard(_lock);
        if (_reject_events) {
            guard.unlock();
            DiscardEvent(cpacket, context);
            return false;
        }
        _queue.QueuePacket_NoLock(cpacket, context);
        qLen = _queue.GetPacketCnt_NoLock();
    }
    if ((qLen == getConfig()._events_before_wakeup) ||
        (cpacket->GetCommand() == FNET_ControlPacket::FNET_CMD_EXECUTE))
    {
        _selector.wakeup();
    }
    return true;
}

void FNET_TransportThread::DiscardEvent(FNET_ControlPacket* cpacket, FNET_Context context) {
    switch (cpacket->GetCommand()) {
    case FNET_ControlPacket::FNET_CMD_IOC_ADD:
        context._value.IOC->Close();
        context._value.IOC->internal_subref();
        break;
    case FNET_ControlPacket::FNET_CMD_IOC_ENABLE_WRITE:
    case FNET_ControlPacket::FNET_CMD_IOC_HANDSHAKE_ACT:
    case FNET_ControlPacket::FNET_CMD_IOC_CLOSE:
        context._value.IOC->internal_subref();
        break;
    }
}

void FNET_TransportThread::handle_add_cmd(FNET_IOComponent* ioc) {
    if ((_detaching.count(ioc->server_adapter()) == 0) && ioc->handle_add_event()) {
        AddComponent(ioc);
        ioc->_flags._ioc_added = true;
        ioc->attach_selector(_selector);
    } else {
        ioc->Close();
        AddDeleteComponent(ioc);
    }
}

void FNET_TransportThread::handle_close_cmd(FNET_IOComponent* ioc) {
    if (ioc->_flags._ioc_added) {
        RemoveComponent(ioc);
        ioc->internal_subref();
    }
    ioc->Close();
    AddDeleteComponent(ioc);
}

void FNET_TransportThread::handle_detach_server_adapter_init_cmd(FNET_IServerAdapter* server_adapter) {
    _detaching.insert(server_adapter);
    FNET_IOComponent* component = _componentsHead;
    while (component != nullptr) {
        FNET_IOComponent* tmp = component;
        component = component->_ioc_next;
        if (tmp->server_adapter() == server_adapter) {
            RemoveComponent(tmp);
            tmp->Close();
            AddDeleteComponent(tmp);
        }
    }
}

void FNET_TransportThread::handle_detach_server_adapter_fini_cmd(FNET_IServerAdapter* server_adapter) {
    _detaching.erase(server_adapter);
}

extern "C" {

static void pipehandler(int) {
    // nop
}

static void trapsigpipe() {
    struct sigaction act;
    memset(&act, 0, sizeof(act));
    sigaction(SIGPIPE, nullptr, &act);
    if (act.sa_handler == SIG_DFL) {
        memset(&act, 0, sizeof(act));
        act.sa_handler = pipehandler;
        sigaction(SIGPIPE, &act, nullptr);
        LOG(warning, "missing signal handler for SIGPIPE (added no-op)");
    }
}

} // extern "C"

FNET_TransportThread::FNET_TransportThread(FNET_Transport& owner_in)
    : _owner(owner_in),
      _now(owner_in.time_tools().current_time()),
      _scheduler(&_now),
      _componentsHead(nullptr),
      _timeOutHead(nullptr),
      _componentsTail(nullptr),
      _componentCnt(0),
      _deleteList(nullptr),
      _selector(),
      _queue(),
      _myQueue(),
      _lock(),
      _shutdownLock(),
      _shutdownCond(),
      _started(false),
      _shutdown(false),
      _finished(false),
      _detaching(),
      _reject_events(false) {
    trapsigpipe();
}

FNET_TransportThread::~FNET_TransportThread() {
    {
        std::lock_guard<std::mutex> guard(_shutdownLock);
    }
    if (_started.load() && !is_finished()) {
        LOG(error, "Transport: delete called on active object!");
    }
}

const FNET_Config& FNET_TransportThread::getConfig() const { return _owner.getConfig(); }

const fnet::TimeTools& FNET_TransportThread::time_tools() const { return _owner.time_tools(); }

bool FNET_TransportThread::tune(SocketHandle& handle) const {
    handle.set_keepalive(true);
    handle.set_linger(true, 0);
    handle.set_nodelay(getConfig()._tcpNoDelay);
    return handle.set_blocking(false);
}

FNET_Connector* FNET_TransportThread::Listen(
    const char* spec, FNET_IPacketStreamer* streamer, FNET_IServerAdapter* serverAdapter) {
    ServerSocket server_socket{SocketSpec(spec)};
    if (server_socket.valid() && server_socket.set_blocking(false)) {
        FNET_Connector* connector = new FNET_Connector(this, streamer, serverAdapter, spec, std::move(server_socket));
        connector->EnableReadEvent(true);
        connector->internal_addref();
        Add(connector, /* needRef = */ false);
        return connector;
    }
    return nullptr;
}

FNET_Connection* FNET_TransportThread::Connect(
    const char* spec, FNET_IPacketStreamer* streamer, FNET_IServerAdapter* serverAdapter, FNET_Context connContext) {
    std::unique_ptr<FNET_Connection> conn =
        std::make_unique<FNET_Connection>(this, streamer, serverAdapter, connContext, spec);
    if (conn->Init()) {
        return conn.release();
    }
    return nullptr;
}

void FNET_TransportThread::Add(FNET_IOComponent* comp, bool needRef) {
    if (needRef) {
        comp->internal_addref();
    }
    PostEvent(&FNET_ControlPacket::IOCAdd, FNET_Context(comp));
}

void FNET_TransportThread::EnableWrite(FNET_IOComponent* comp, bool needRef) {
    if (needRef) {
        comp->internal_addref();
    }
    PostEvent(&FNET_ControlPacket::IOCEnableWrite, FNET_Context(comp));
}

void FNET_TransportThread::handshake_act(FNET_IOComponent* comp, bool needRef) {
    if (needRef) {
        comp->internal_addref();
    }
    PostEvent(&FNET_ControlPacket::IOCHandshakeACT, FNET_Context(comp));
}

void FNET_TransportThread::Close(FNET_IOComponent* comp, bool needRef) {
    if (needRef) {
        comp->internal_addref();
    }
    PostEvent(&FNET_ControlPacket::IOCClose, FNET_Context(comp));
}

void FNET_TransportThread::init_detach(FNET_IServerAdapter* server_adapter) {
    PostEvent(&FNET_ControlPacket::DetachServerAdapterInit, FNET_Context(server_adapter));
}

void FNET_TransportThread::fini_detach(FNET_IServerAdapter* server_adapter) {
    PostEvent(&FNET_ControlPacket::DetachServerAdapterFini, FNET_Context(server_adapter));
}

bool FNET_TransportThread::execute(FNET_IExecutable* exe) {
    return PostEvent(&FNET_ControlPacket::Execute, FNET_Context(exe));
}

void FNET_TransportThread::sync() {
    Sync exe;
    if (execute(&exe)) {
        exe.gate.await();
    } else {
        WaitFinished();
    }
}

void FNET_TransportThread::ShutDown(bool waitFinished) {
    bool wasEmpty = false;
    {
        std::lock_guard<std::mutex> guard(_lock);
        if (!should_shut_down()) {
            _shutdown.store(true, std::memory_order_relaxed);
            wasEmpty = _queue.IsEmpty_NoLock();
        }
    }
    if (wasEmpty) {
        _selector.wakeup();
    }
    if (waitFinished) {
        WaitFinished();
    }
}

void FNET_TransportThread::WaitFinished() {
    if (is_finished())
        return;

    std::unique_lock<std::mutex> guard(_shutdownLock);
    while (!is_finished())
        _shutdownCond.wait(guard);
}

bool FNET_TransportThread::InitEventLoop() {
    if (_started.exchange(true)) {
        LOG(error, "Transport: InitEventLoop: object already active!");
        return false;
    }
    _now = time_tools().current_time();
    return true;
}

void FNET_TransportThread::handle_wakeup() {
    {
        std::lock_guard<std::mutex> guard(_lock);
        _queue.FlushPackets_NoLock(&_myQueue);
    }

    FNET_Context context;
    FNET_Packet* packet = nullptr;
    while ((packet = _myQueue.DequeuePacket_NoLock(&context)) != nullptr) {

        if (packet->GetCommand() == FNET_ControlPacket::FNET_CMD_EXECUTE) {
            context._value.EXECUTABLE->execute();
            continue;
        }

        if (packet->GetCommand() == FNET_ControlPacket::FNET_CMD_DETACH_SERVER_ADAPTER_INIT) {
            handle_detach_server_adapter_init_cmd(context._value.SERVER_ADAPTER);
            continue;
        }

        if (packet->GetCommand() == FNET_ControlPacket::FNET_CMD_DETACH_SERVER_ADAPTER_FINI) {
            handle_detach_server_adapter_fini_cmd(context._value.SERVER_ADAPTER);
            continue;
        }

        if (context._value.IOC->_flags._ioc_delete) {
            context._value.IOC->internal_subref();
            continue;
        }

        switch (packet->GetCommand()) {
        case FNET_ControlPacket::FNET_CMD_IOC_ADD:
            handle_add_cmd(context._value.IOC);
            break;
        case FNET_ControlPacket::FNET_CMD_IOC_ENABLE_WRITE:
            context._value.IOC->EnableWriteEvent(true);
            if (context._value.IOC->HandleWriteEvent()) {
                context._value.IOC->internal_subref();
            } else {
                handle_close_cmd(context._value.IOC);
            }
            break;
        case FNET_ControlPacket::FNET_CMD_IOC_HANDSHAKE_ACT:
            if (context._value.IOC->handle_handshake_act()) {
                context._value.IOC->internal_subref();
            } else {
                handle_close_cmd(context._value.IOC);
            }
            break;
        case FNET_ControlPacket::FNET_CMD_IOC_CLOSE:
            handle_close_cmd(context._value.IOC);
            break;
        }
    }
}

void FNET_TransportThread::handle_event(FNET_IOComponent& ctx, bool read, bool write) {
    if (!ctx._flags._ioc_delete) {
        bool rc = true;
        if (read) {
            rc = rc && ctx.HandleReadEvent();
        }
        if (write) {
            rc = rc && ctx.HandleWriteEvent();
        }
        if (!rc) { // IOC is broken, close it
            RemoveComponent(&ctx);
            ctx.Close();
            AddDeleteComponent(&ctx);
        }
    }
}

bool FNET_TransportThread::EventLoopIteration() {

    if (!should_shut_down()) {
        int msTimeout = vespalib::count_ms(time_tools().event_timeout());
        // obtain I/O events
        _selector.poll(msTimeout);

        // sample current time (performed once per event loop iteration)
        _now = time_tools().current_time();

        // handle io-events
        auto dispatchResult = _selector.dispatch(*this);

        if ((dispatchResult == vespalib::SelectorDispatchResult::NO_WAKEUP) &&
            (getConfig()._events_before_wakeup > 1))
        {
            handle_wakeup();
        }

        // handle IOC time-outs
        if (getConfig()._iocTimeOut > vespalib::duration::zero()) {
            checkTimedoutComponents(getConfig()._iocTimeOut);
        }

        // perform pending tasks
        _scheduler.CheckTasks();

        // perform scheduled delete operations
        FlushDeleteList();
    } // -- END OF MAIN EVENT LOOP --

    if (!should_shut_down())
        return true;
    if (is_finished())
        return false;

    endEventLoop();
    return false;
}

void FNET_TransportThread::checkTimedoutComponents(vespalib::duration timeout) {
    vespalib::steady_time oldest = (_now - timeout);
    while (_timeOutHead != nullptr && oldest > _timeOutHead->_ioc_timestamp) {
        FNET_IOComponent* component = _timeOutHead;
        RemoveComponent(component);
        component->Close();
        AddDeleteComponent(component);
    }
}

void FNET_TransportThread::endEventLoop() {
    // close and remove all I/O Components
    FNET_IOComponent* component = _componentsHead;
    while (component != nullptr) {
        assert(component == _componentsHead);
        FNET_IOComponent* tmp = component;
        component = component->_ioc_next;
        RemoveComponent(tmp);
        tmp->Close();
        tmp->internal_subref();
    }

    // flush event queue
    {
        std::lock_guard<std::mutex> guard(_lock);
        _queue.FlushPackets_NoLock(&_myQueue);
        _reject_events = true;
    }

    // discard remaining events
    FNET_Context context;
    FNET_Packet* packet = nullptr;
    while ((packet = _myQueue.DequeuePacket_NoLock(&context)) != nullptr) {
        if (packet->GetCommand() == FNET_ControlPacket::FNET_CMD_EXECUTE) {
            context._value.EXECUTABLE->execute();
        } else {
            DiscardEvent((FNET_ControlPacket*)packet, context);
        }
    }

    assert(_componentsHead == nullptr && _componentsTail == nullptr && _timeOutHead == nullptr &&
           load_relaxed(_componentCnt) == 0 && _queue.IsEmpty_NoLock() && _myQueue.IsEmpty_NoLock());

    {
        std::lock_guard<std::mutex> guard(_shutdownLock);
        _finished.store(true, std::memory_order_release);
        _shutdownCond.notify_all();
    }

    LOG(spam, "Transport: event loop finished.");
}

bool FNET_TransportThread::Start(vespalib::ThreadPool& pool) {
    pool.start([this]() { run(); });
    return true;
}

void FNET_TransportThread::Main() { run(); }

void FNET_TransportThread::run() {
    if (!InitEventLoop()) {
        LOG(warning, "Transport: Run: Could not init event loop");
        return;
    }
    while (EventLoopIteration()) {
        // event loop must be stopped from the outside
    }
}
