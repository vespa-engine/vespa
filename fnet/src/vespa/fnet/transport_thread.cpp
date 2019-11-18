// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_thread.h"
#include "iexecutable.h"
#include "iocomponent.h"
#include "controlpacket.h"
#include "connector.h"
#include "connection.h"
#include "transport.h"
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/server_socket.h>
#include <csignal>

#include <vespa/log/log.h>
LOG_SETUP(".fnet");

using vespalib::ServerSocket;
using vespalib::SocketHandle;
using vespalib::SocketSpec;

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
    comp->_ioc_timestamp = fastos::UTCTimeStamp(_now);
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
    bool wasEmpty;
    {
        std::unique_lock<std::mutex> guard(_lock);
        if (IsShutDown()) {
            guard.unlock();
            SafeDiscardEvent(cpacket, context);
            return false;
        }
        wasEmpty = _queue.IsEmpty_NoLock();
        _queue.QueuePacket_NoLock(cpacket, context);
    }
    if (wasEmpty) {
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
      _startTime(),
      _now(),
      _scheduler(&_now),
      _config(),
      _componentsHead(nullptr),
      _timeOutHead(nullptr),
      _componentsTail(nullptr),
      _componentCnt(0),
      _deleteList(nullptr),
      _selector(),
      _queue(),
      _myQueue(),
      _lock(),
      _cond(),
      _pseudo_thread(),
      _started(false),
      _shutdown(false),
      _finished(false),
      _waitFinished(false),
      _deleted(false)
{
    _now.SetNow();
    trapsigpipe();
}


FNET_TransportThread::~FNET_TransportThread()
{
    {
        std::lock_guard<std::mutex> guard(_lock);
        _deleted = true;
    }
    if (_started && !_finished) {
        LOG(error, "Transport: delete called on active object!");
    } else {
        std::lock_guard guard(_pseudo_thread);
    }
}


bool
FNET_TransportThread::tune(SocketHandle &handle) const
{
    handle.set_keepalive(true);
    handle.set_linger(true, 0);
    handle.set_nodelay(_config._tcpNoDelay);
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

    std::unique_lock<std::mutex> guard(_lock);
    _waitFinished = true;
    while (!_finished)
        _cond.wait(guard);
}


bool
FNET_TransportThread::InitEventLoop()
{
    bool wasStarted;
    bool wasDeleted;
    {
        std::lock_guard<std::mutex> guard(_lock);
        wasStarted = _started;
        wasDeleted = _deleted;
        if (!_started && !_deleted) {
            _started = true;
        }
    }
    if (wasStarted) {
        LOG(error, "Transport: InitEventLoop: object already active!");
        return false;
    }
    if (wasDeleted) {
        LOG(error, "Transport: InitEventLoop: object was deleted!");
        return false;
    }
    _now.SetNow();
    _startTime = _now;
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
FNET_TransportThread::EventLoopIteration()
{
    FNET_IOComponent   *component = nullptr;
    int                 msTimeout = FNET_Scheduler::SLOT_TICK;

#ifdef FNET_SANITY_CHECKS
    FastOS_Time beforeGetEvents;
#endif

    if (!IsShutDown()) {

#ifdef FNET_SANITY_CHECKS
        // Warn if event loop takes more than 250ms
        beforeGetEvents.SetNow();
        double loopTime = beforeGetEvents.MilliSecs() - _now.MilliSecs();
        if (loopTime > 250.0)
            LOG(warning, "SANITY: Transport loop time: %.2f ms", loopTime);
#endif

        // obtain I/O events
        _selector.poll(msTimeout);

        // sample current time (performed once per event loop iteration)
        _now.SetNow();

#ifdef FNET_SANITY_CHECKS
        // Warn if event extraction takes more than timeout + 100ms
        double extractTime = _now.MilliSecs() - beforeGetEvents.MilliSecs();
        if (extractTime > (double) msTimeout + 100.0)
            LOG(warning, "SANITY: Event extraction time: %.2f ms (timeout: %d ms)",
                extractTime, msTimeout);
#endif

        // handle wakeup and io-events
        _selector.dispatch(*this);

        // handle IOC time-outs
        if (_config._iocTimeOut > 0) {

            FastOS_Time t = _now;
            t.SubtractMilliSecs((double)_config._iocTimeOut);
            fastos::UTCTimeStamp oldest(t);
            while (_timeOutHead != nullptr &&
                   oldest > _timeOutHead->_ioc_timestamp) {

                component = _timeOutHead;
                RemoveComponent(component);
                component->Close();
                AddDeleteComponent(component);
            }
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
    component = _componentsHead;
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
        std::lock_guard<std::mutex> guard(_lock);
        _finished = true;
        if (_waitFinished) {
            _cond.notify_all();
        }
    }

    LOG(spam, "Transport: event loop finished.");

    return false;
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
