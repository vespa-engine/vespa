// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_thread.h"
#include "iexecutable.h"
#include "iocomponent.h"
#include "controlpacket.h"
#include "connector.h"
#include "connection.h"
#include "transport.h"
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/util/gate.h>
#include <csignal>

#include <vespa/log/log.h>
LOG_SETUP(".fnet");

using vespalib::ServerSocket;
using vespalib::SocketHandle;
using vespalib::SocketSpec;
using vespalib::steady_clock;

namespace {

struct Sync : public FNET_IExecutable
{
    vespalib::Gate gate;
    void execute() override {
        gate.countDown();
    }
};

} // namespace<unnamed>

void
FNET_TransportThread::AddComponent(FNET_IOComponent *comp)
{
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
        _componentCnt++;
    } else {
        comp->_ioc_prev = nullptr;
        comp->_ioc_next = _componentsHead;
        if (_componentsHead == nullptr) {
            _componentsTail = comp;
        } else {
            _componentsHead->_ioc_prev = comp;
        }
        _componentsHead = comp;
        _componentCnt++;
    }
}


void
FNET_TransportThread::RemoveComponent(FNET_IOComponent *comp)
{
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
    _componentCnt--;
}


void
FNET_TransportThread::UpdateTimeOut(FNET_IOComponent *comp)
{
    comp->_ioc_timestamp = _now;
    RemoveComponent(comp);
    AddComponent(comp);
}


void
FNET_TransportThread::AddDeleteComponent(FNET_IOComponent *comp)
{
    assert(!comp->_flags._ioc_delete);
    comp->_flags._ioc_delete = true;
    comp->_ioc_prev = nullptr;
    comp->_ioc_next = _deleteList;
    _deleteList = comp;
}


void
FNET_TransportThread::FlushDeleteList()
{
    while (_deleteList != nullptr) {
        FNET_IOComponent *tmp = _deleteList;
        _deleteList = tmp->_ioc_next;
        assert(tmp->_flags._ioc_delete);
        tmp->SubRef();
    }
}


bool
FNET_TransportThread::PostEvent(FNET_ControlPacket *cpacket,
                                FNET_Context context)
{
    size_t qLen;
    {
        std::unique_lock<std::mutex> guard(_lock);
        if (IsShutDown()) {
            guard.unlock();
            SafeDiscardEvent(cpacket, context);
            return false;
        }
        _queue.QueuePacket_NoLock(cpacket, context);
        qLen = _queue.GetPacketCnt_NoLock();
    }
    if (qLen == getConfig()._events_before_wakeup) {
        _selector.wakeup();
    }
    return true;
}


void
FNET_TransportThread::DiscardEvent(FNET_ControlPacket *cpacket,
                                   FNET_Context context)
{
    switch (cpacket->GetCommand()) {
    case FNET_ControlPacket::FNET_CMD_IOC_ADD:
        context._value.IOC->Close();
        context._value.IOC->SubRef();
        break;
    case FNET_ControlPacket::FNET_CMD_IOC_ENABLE_WRITE:
    case FNET_ControlPacket::FNET_CMD_IOC_HANDSHAKE_ACT:
    case FNET_ControlPacket::FNET_CMD_IOC_CLOSE:
        context._value.IOC->SubRef();
        break;
    }
}

void
FNET_TransportThread::SafeDiscardEvent(FNET_ControlPacket *cpacket,
                                       FNET_Context context)
{
    std::lock_guard guard(_pseudo_thread); // be the thread
    DiscardEvent(cpacket, context);
}

void
FNET_TransportThread::handle_add_cmd(FNET_IOComponent *ioc)
{
    if (ioc->handle_add_event()) {
        AddComponent(ioc);
        ioc->_flags._ioc_added = true;
        ioc->attach_selector(_selector);
    } else {
        ioc->Close();
        AddDeleteComponent(ioc);
    }
}


void
FNET_TransportThread::handle_close_cmd(FNET_IOComponent *ioc)
{
    if (ioc->_flags._ioc_added) {
        RemoveComponent(ioc);
        ioc->SubRef();
    }
    ioc->Close();
    AddDeleteComponent(ioc);
}


extern "C" {

    static void pipehandler(int)
    {
        // nop
    }

    static void trapsigpipe()
    {
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

FNET_TransportThread::FNET_TransportThread(FNET_Transport &owner_in)
    : _owner(owner_in),
      _now(steady_clock ::now()),
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
      _pseudo_thread(),
      _started(false),
      _shutdown(false),
      _finished(false)
{
    trapsigpipe();
}


FNET_TransportThread::~FNET_TransportThread()
{
    {
        std::lock_guard<std::mutex> guard(_shutdownLock);
    }
    if (_started.load() && !_finished) {
        LOG(error, "Transport: delete called on active object!");
    } else {
        std::lock_guard guard(_pseudo_thread);
    }
}

const FNET_Config &
FNET_TransportThread::getConfig() const {
    return _owner.getConfig();
}

bool
FNET_TransportThread::tune(SocketHandle &handle) const
{
    handle.set_keepalive(true);
    handle.set_linger(true, 0);
    handle.set_nodelay(getConfig()._tcpNoDelay);
    return handle.set_blocking(false);
}


FNET_Connector*
FNET_TransportThread::Listen(const char *spec, FNET_IPacketStreamer *streamer,
                             FNET_IServerAdapter *serverAdapter)
{
    ServerSocket server_socket{SocketSpec(spec)};
    if (server_socket.valid() && server_socket.set_blocking(false)) {
        FNET_Connector *connector = new FNET_Connector(this, streamer, serverAdapter, spec, std::move(server_socket));
        connector->EnableReadEvent(true);
        connector->AddRef_NoLock();
        Add(connector, /* needRef = */ false);
        return connector;
    }
    return nullptr;
}


FNET_Connection*
FNET_TransportThread::Connect(const char *spec, FNET_IPacketStreamer *streamer,
                              FNET_IPacketHandler *adminHandler,
                              FNET_Context adminContext,
                              FNET_IServerAdapter *serverAdapter,
                              FNET_Context connContext)
{
    std::unique_ptr<FNET_Connection> conn = std::make_unique<FNET_Connection>(this, streamer, serverAdapter,
            adminHandler, adminContext, connContext, spec);
    if (conn->Init()) {
        return conn.release();
    }
    return nullptr;
}


void
FNET_TransportThread::Add(FNET_IOComponent *comp, bool needRef)
{
    if (needRef) {
        comp->AddRef();
    }
    PostEvent(&FNET_ControlPacket::IOCAdd,
              FNET_Context(comp));
}


void
FNET_TransportThread::EnableWrite(FNET_IOComponent *comp, bool needRef)
{
    if (needRef) {
        comp->AddRef();
    }
    PostEvent(&FNET_ControlPacket::IOCEnableWrite,
              FNET_Context(comp));
}

void
FNET_TransportThread::handshake_act(FNET_IOComponent *comp, bool needRef)
{
    if (needRef) {
        comp->AddRef();
    }
    PostEvent(&FNET_ControlPacket::IOCHandshakeACT,
              FNET_Context(comp));
}

void
FNET_TransportThread::Close(FNET_IOComponent *comp, bool needRef)
{
    if (needRef) {
        comp->AddRef();
    }
    PostEvent(&FNET_ControlPacket::IOCClose,
              FNET_Context(comp));
}


bool
FNET_TransportThread::execute(FNET_IExecutable *exe)
{
    return PostEvent(&FNET_ControlPacket::Execute, FNET_Context(exe));
}


void
FNET_TransportThread::sync()
{
    Sync exe;
    if (execute(&exe)) {
        exe.gate.await();
    } else {
        WaitFinished();
    }
}


void
FNET_TransportThread::ShutDown(bool waitFinished)
{
    bool wasEmpty = false;
    {
        std::lock_guard<std::mutex> guard(_lock);
        if (!IsShutDown()) {
            _shutdown.store(true, std::memory_order_relaxed);
            wasEmpty  = _queue.IsEmpty_NoLock();
        }
    }
    if (wasEmpty) {
        _selector.wakeup();
    }
    if (waitFinished) {
        WaitFinished();
    }
}


void
FNET_TransportThread::WaitFinished()
{
    if (_finished)
        return;

    std::unique_lock<std::mutex> guard(_shutdownLock);
    while (!_finished)
        _shutdownCond.wait(guard);
}


bool
FNET_TransportThread::InitEventLoop()
{
    if (_started.exchange(true)) {
        LOG(error, "Transport: InitEventLoop: object already active!");
        return false;
    }
    _now = steady_clock::now();
    return true;
}

void
FNET_TransportThread::handle_wakeup()
{
    {
        std::lock_guard<std::mutex> guard(_lock);
        _queue.FlushPackets_NoLock(&_myQueue);
    }

    FNET_Context context;
    FNET_Packet *packet = nullptr;
    while ((packet = _myQueue.DequeuePacket_NoLock(&context)) != nullptr) {

        if (packet->GetCommand() == FNET_ControlPacket::FNET_CMD_EXECUTE) {
            context._value.EXECUTABLE->execute();
            continue;
        }

        if (context._value.IOC->_flags._ioc_delete) {
            context._value.IOC->SubRef();
            continue;
        }

        switch (packet->GetCommand()) {
        case FNET_ControlPacket::FNET_CMD_IOC_ADD:
            handle_add_cmd(context._value.IOC);
            break;
        case FNET_ControlPacket::FNET_CMD_IOC_ENABLE_WRITE:
            context._value.IOC->EnableWriteEvent(true);
            if (context._value.IOC->HandleWriteEvent()) {
                context._value.IOC->SubRef();
            } else {
                handle_close_cmd(context._value.IOC);
            }
            break;
        case FNET_ControlPacket::FNET_CMD_IOC_HANDSHAKE_ACT:
            if (context._value.IOC->handle_handshake_act()) {
                context._value.IOC->SubRef();
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


void
FNET_TransportThread::handle_event(FNET_IOComponent &ctx, bool read, bool write)
{
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


bool
FNET_TransportThread::EventLoopIteration() {

    if (!IsShutDown()) {
        int msTimeout = FNET_Scheduler::tick_ms.count();
        // obtain I/O events
        _selector.poll(msTimeout);

        // sample current time (performed once per event loop iteration)
        _now = steady_clock::now();

        // handle io-events
        auto dispatchResult = _selector.dispatch(*this);

        if ((dispatchResult == vespalib::SelectorDispatchResult::NO_WAKEUP) && (getConfig()._events_before_wakeup > 1)) {
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
    }                      // -- END OF MAIN EVENT LOOP --

    if (!IsShutDown())
        return true;
    if (_finished)
        return false;

    endEventLoop();
    return false;
}

void
FNET_TransportThread::checkTimedoutComponents(vespalib::duration timeout) {
    vespalib::steady_time oldest = (_now - timeout);
    while (_timeOutHead != nullptr && oldest > _timeOutHead->_ioc_timestamp) {
        FNET_IOComponent *component = _timeOutHead;
        RemoveComponent(component);
        component->Close();
        AddDeleteComponent(component);
    }
}

void
FNET_TransportThread::endEventLoop() {
    // flush event queue
    {
        std::lock_guard<std::mutex> guard(_lock);
        _queue.FlushPackets_NoLock(&_myQueue);
    }

    // discard remaining events
    FNET_Context context;
    FNET_Packet *packet = nullptr;
    while ((packet = _myQueue.DequeuePacket_NoLock(&context)) != nullptr) {
        if (packet->GetCommand() == FNET_ControlPacket::FNET_CMD_EXECUTE) {
            context._value.EXECUTABLE->execute();
        } else {
            DiscardEvent((FNET_ControlPacket *)packet, context);
        }
    }

    // close and remove all I/O Components
    FNET_IOComponent *component = _componentsHead;
    while (component != nullptr) {
        assert(component == _componentsHead);
        FNET_IOComponent *tmp = component;
        component = component->_ioc_next;
        RemoveComponent(tmp);
        tmp->Close();
        tmp->SubRef();
    }
    assert(_componentsHead == nullptr &&
           _componentsTail == nullptr &&
           _timeOutHead    == nullptr &&
           _componentCnt   == 0    &&
           _queue.IsEmpty_NoLock() &&
           _myQueue.IsEmpty_NoLock());

    {
        std::lock_guard<std::mutex> guard(_shutdownLock);
        _finished = true;
        _shutdownCond.notify_all();
    }

    LOG(spam, "Transport: event loop finished.");

}


bool
FNET_TransportThread::Start(FastOS_ThreadPool *pool)
{
    return (pool != nullptr && pool->NewThread(this));
}


void
FNET_TransportThread::Main()
{
    Run(nullptr, nullptr);
}


void
FNET_TransportThread::Run(FastOS_ThreadInterface *thisThread, void *)
{
    std::lock_guard guard(_pseudo_thread); // be the thread
    if (!InitEventLoop()) {
        LOG(warning, "Transport: Run: Could not init event loop");
        return;
    }
    while (EventLoopIteration()) {
        if (thisThread != nullptr && thisThread->GetBreakFlag())
            ShutDown(false);
    }
}
