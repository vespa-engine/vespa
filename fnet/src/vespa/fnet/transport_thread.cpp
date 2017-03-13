// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport_thread.h"
#include "iexecutable.h"
#include "iocomponent.h"
#include "controlpacket.h"
#include "connector.h"
#include "connection.h"
#include "transport.h"
#include <vespa/vespalib/util/sync.h>
#include <vespa/fastos/socket.h>

#include <vespa/log/log.h>
LOG_SETUP(".fnet");

namespace {

struct Sync : public FNET_IExecutable
{
    vespalib::Gate gate;
    virtual void execute() {
        gate.countDown();
    }
};

} // namespace<unnamed>


char *
SplitString(char *input, const char *sep, int &argc, char **argv, int maxargs)
{
    int i;
    int sepcnt = strlen(sep);

    for (argc = 0, argv[0] = input; *input != '\0'; input++) {
        if (*input == '[' && argc == 0 && argv[argc] == input) {
            argv[argc] = ++input;	// Skip '['
            for (; *input != ']' && *input != '\0'; ++input);
            if (*input == ']')
                *input++ = '\0';	// Replace ']'
            if (*input == '\0')
                break;
        }
        for (i = 0; i < sepcnt; i++) {
            if (*input == sep[i]) {
                *input = '\0';
                if (*(argv[argc]) != '\0' && ++argc >= maxargs)
                    return (input + 1);       // INCOMPLETE
                argv[argc] = (input + 1);
                break; // inner for loop
            }
        }
    }
    if (*(argv[argc]) != '\0')
        argc++;
    return nullptr;                    // COMPLETE
}

#ifndef IAM_DOXYGEN
void
FNET_TransportThread::StatsTask::PerformTask()
{
    _transport->UpdateStats();
    Schedule(5.0);
}
#endif

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
    bool wasEmpty;
    Lock();
    if (_shutdown) {
        Unlock();
        DiscardEvent(cpacket, context);
        return false;
    }
    wasEmpty = _queue.IsEmpty_NoLock();
    _queue.QueuePacket_NoLock(cpacket, context);
    Unlock();
    if (wasEmpty) {
        _socketEvent.AsyncWakeUp();
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
    case FNET_ControlPacket::FNET_CMD_IOC_ENABLE_READ:
    case FNET_ControlPacket::FNET_CMD_IOC_DISABLE_READ:
    case FNET_ControlPacket::FNET_CMD_IOC_ENABLE_WRITE:
    case FNET_ControlPacket::FNET_CMD_IOC_DISABLE_WRITE:
    case FNET_ControlPacket::FNET_CMD_IOC_CLOSE:
        context._value.IOC->SubRef();
        break;
    }
}


void
FNET_TransportThread::UpdateStats()
{
    _now.SetNow(); // trade some overhead for better stats
    double ms = _now.MilliSecs() - _statTime.MilliSecs();
    _statTime = _now;
    for (FNET_IOComponent *comp = _componentsHead;
         comp != nullptr; comp = comp->_ioc_next)
    {
        comp->Lock();
        comp->FlushDirectWriteStats();
        comp->Unlock();
    }
    Lock();
    _stats.Update(&_counters, ms / 1000.0);
    Unlock();
    _counters.Clear();

    if (_config._logStats)
        _stats.Log();
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
      _counters(),
      _stats(),
      _statsTask(&_scheduler, this),
      _statTime(),
      _config(),
      _componentsHead(nullptr),
      _timeOutHead(nullptr),
      _componentsTail(nullptr),
      _componentCnt(0),
      _deleteList(nullptr),
      _socketEvent(),
      _events(nullptr),
      _queue(),
      _myQueue(),
      _cond(),
      _started(false),
      _shutdown(false),
      _finished(false),
      _waitFinished(false),
      _deleted(false)
{
    _now.SetNow();
    assert(_socketEvent.GetCreateSuccess());
    trapsigpipe();
}


FNET_TransportThread::~FNET_TransportThread()
{
    Lock();
    _deleted = true;
    Unlock();
    if (_started && !_finished) {
        LOG(error, "Transport: delete called on active object!");
    }
}


FNET_Connector*
FNET_TransportThread::Listen(const char *spec, FNET_IPacketStreamer *streamer,
                             FNET_IServerAdapter *serverAdapter)
{
    int    speclen = strlen(spec);
    char   tmp[1024];
    int    argc;
    char  *argv[32];

    assert(speclen < 1024);
    memcpy(tmp, spec, speclen);
    tmp[speclen] = '\0';
    if (SplitString(tmp, "/", argc, argv, 32) != nullptr
        || argc != 2)
        return nullptr;    // wrong number of parameters

    // handle different connection types (currently only TCP/IP support)
    if (strcasecmp(argv[0], "tcp") == 0) {
        if (SplitString(argv[1], ":", argc, argv, 32) != nullptr
            || argc < 1 || argc > 2)
            return nullptr;    // wrong number of parameters

        int port = atoi(argv[argc - 1]); // last param is port
        if (port < 0)
            return nullptr;
        if (port == 0 && strcmp(argv[argc - 1], "0") != 0)
            return nullptr;
        FNET_Connector *connector;
        connector = new FNET_Connector(this, streamer, serverAdapter, spec, port,
                                       500, nullptr, (argc == 2) ? argv[0] : nullptr);
        if (connector->Init()) {
            connector->AddRef_NoLock();
            Add(connector, /* needRef = */ false);
            return connector;
        } else {
            delete connector;
            return nullptr;
        }
    } else {
        return nullptr;
    }
}


FNET_Connection*
FNET_TransportThread::Connect(const char *spec, FNET_IPacketStreamer *streamer,
                              FNET_IPacketHandler *adminHandler,
                              FNET_Context adminContext,
                              FNET_IServerAdapter *serverAdapter,
                              FNET_Context connContext)
{
    int    speclen = strlen(spec);
    char   tmp[1024];
    int    argc;
    char  *argv[32];

    assert(speclen < 1024);
    memcpy(tmp, spec, speclen);
    tmp[speclen] = '\0';
    if (SplitString(tmp, "/", argc, argv, 32) != nullptr
        || argc != 2)
        return nullptr;    // wrong number of parameters

    // handle different connection types (currently only TCP/IP support)
    if (strcasecmp(argv[0], "tcp") == 0) {
        if (SplitString(argv[1], ":", argc, argv, 32) != nullptr
            || argc != 2)
            return nullptr;    // wrong number of parameters

        int port = atoi(argv[1]);
        if (port <= 0)
            return nullptr;
        FastOS_Socket *mysocket = new FastOS_Socket();
        mysocket->SetAddress(port, argv[0]);
        FNET_Connection *conn = new FNET_Connection(this, streamer, serverAdapter,
                                                    adminHandler, adminContext,
                                                    connContext, mysocket, spec);
        if (conn->Init()) {
            conn->AddRef_NoLock();
            Add(conn, /* needRef = */ false);
            return conn;
        } else {
            delete conn;
            return nullptr;
        }
    } else {
        return nullptr;
    }
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
FNET_TransportThread::EnableRead(FNET_IOComponent *comp, bool needRef)
{
    if (needRef) {
        comp->AddRef();
    }
    PostEvent(&FNET_ControlPacket::IOCEnableRead,
              FNET_Context(comp));
}


void
FNET_TransportThread::DisableRead(FNET_IOComponent *comp, bool needRef)
{
    if (needRef) {
        comp->AddRef();
    }
    PostEvent(&FNET_ControlPacket::IOCDisableRead,
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
FNET_TransportThread::DisableWrite(FNET_IOComponent *comp, bool needRef)
{
    if (needRef) {
        comp->AddRef();
    }
    PostEvent(&FNET_ControlPacket::IOCDisableWrite,
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
    Lock();
    if (!_shutdown) {
        _shutdown = true;
        wasEmpty  = _queue.IsEmpty_NoLock();
    }
    Unlock();
    if (wasEmpty)
        _socketEvent.AsyncWakeUp();

    if (waitFinished)
        WaitFinished();
}


void
FNET_TransportThread::WaitFinished()
{
    if (_finished)
        return;

    Lock();
    _waitFinished = true;
    while (!_finished)
        Wait();
    Unlock();
}


bool
FNET_TransportThread::InitEventLoop()
{
    bool wasStarted;
    bool wasDeleted;
    Lock();
    wasStarted = _started;
    wasDeleted = _deleted;
    if (!_started && !_deleted) {
        _started = true;
    }
    Unlock();
    if (wasStarted) {
        LOG(error, "Transport: InitEventLoop: object already active!");
        return false;
    }
    if (wasDeleted) {
        LOG(error, "Transport: InitEventLoop: object was deleted!");
        return false;
    }

    _events = new FastOS_IOEvent[EVT_MAX];
    assert(_events != nullptr);

    _now.SetNow();
    _startTime = _now;
    _statTime  = _now;
    _statsTask.Schedule(5.0);
    return true;
}


bool
FNET_TransportThread::EventLoopIteration()
{
    FNET_Packet        *packet    = nullptr;
    FNET_Context        context;
    FNET_IOComponent   *component = nullptr;
    int                 evt_cnt   = 0;
    FastOS_IOEvent     *events    = _events;
    int                 msTimeout = FNET_Scheduler::SLOT_TICK;
    bool                wakeUp    = false;

#ifdef FNET_SANITY_CHECKS
    FastOS_Time beforeGetEvents;
#endif

    if (!_shutdown) {

#ifdef FNET_SANITY_CHECKS
        // Warn if event loop takes more than 250ms
        beforeGetEvents.SetNow();
        double loopTime = beforeGetEvents.MilliSecs() - _now.MilliSecs();
        if (loopTime > 250.0)
            LOG(warning, "SANITY: Transport loop time: %.2f ms", loopTime);
#endif

        // obtain I/O events
        evt_cnt = _socketEvent.GetEvents(&wakeUp, msTimeout, events, EVT_MAX);
        CountEventLoop();

        // sample current time (performed once per event loop iteration)
        _now.SetNow();

#ifdef FNET_SANITY_CHECKS
        // Warn if event extraction takes more than timeout + 100ms
        double extractTime = _now.MilliSecs() - beforeGetEvents.MilliSecs();
        if (extractTime > (double) msTimeout + 100.0)
            LOG(warning, "SANITY: Event extraction time: %.2f ms (timeout: %d ms)",
                extractTime, msTimeout);
#endif

        // report event error (if any)
        if (evt_cnt < 0) {
            std::string str = FastOS_Socket::getLastErrorString();
            LOG(spam, "Transport: event error: %s", str.c_str());
        } else {
            CountIOEvent(evt_cnt);
        }

        // handle internal transport layer events
        if (wakeUp) {

            Lock();
            CountEvent(_queue.FlushPackets_NoLock(&_myQueue));
            Unlock();

            while ((packet = _myQueue.DequeuePacket_NoLock(&context)) != nullptr) {

                if (context._value.IOC->_flags._ioc_delete) {
                    context._value.IOC->SubRef();
                    continue;
                }

                switch (packet->GetCommand()) {
                case FNET_ControlPacket::FNET_CMD_IOC_ADD:
                    AddComponent(context._value.IOC);
                    context._value.IOC->_flags._ioc_added = true;
                    context._value.IOC->SetSocketEvent(&_socketEvent);
                    break;
                case FNET_ControlPacket::FNET_CMD_IOC_ENABLE_READ:
                    context._value.IOC->EnableReadEvent(true);
                    context._value.IOC->SubRef();
                    break;
                case FNET_ControlPacket::FNET_CMD_IOC_DISABLE_READ:
                    context._value.IOC->EnableReadEvent(false);
                    context._value.IOC->SubRef();
                    break;
                case FNET_ControlPacket::FNET_CMD_IOC_ENABLE_WRITE:
                    context._value.IOC->EnableWriteEvent(true);
                    context._value.IOC->SubRef();
                    break;
                case FNET_ControlPacket::FNET_CMD_IOC_DISABLE_WRITE:
                    context._value.IOC->EnableWriteEvent(false);
                    context._value.IOC->SubRef();
                    break;
                case FNET_ControlPacket::FNET_CMD_IOC_CLOSE:
                    if (context._value.IOC->_flags._ioc_added) {
                        RemoveComponent(context._value.IOC);
                        context._value.IOC->SubRef();
                    }
                    context._value.IOC->Close();
                    AddDeleteComponent(context._value.IOC);
                    break;
                case FNET_ControlPacket::FNET_CMD_EXECUTE:
                    context._value.EXECUTABLE->execute();
                    break;
                }
            }
        }

        // handle I/O events
        for (int i = 0; i < evt_cnt; i++) {

            component = (FNET_IOComponent *) events[i]._eventAttribute;
            if (component == nullptr || component->_flags._ioc_delete)
                continue;

            bool rc = true;
            if (events[i]._readOccurred)
                rc = rc && component->HandleReadEvent();
            if (events[i]._writeOccurred)
                rc = rc && component->HandleWriteEvent();
            if (!rc) { // IOC is broken, close it
                RemoveComponent(component);
                component->Close();
                AddDeleteComponent(component);
            }
        }

        // handle IOC time-outs
        if (_config._iocTimeOut > 0) {

            FastOS_Time t = _now;
            t.SubtractMilliSecs((double)_config._iocTimeOut);
            fastos::TimeStamp oldest(t);
            while (_timeOutHead != nullptr &&
                   oldest >= _timeOutHead->_ioc_timestamp) {

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

    if (!_shutdown)
        return true;
    if (_finished)
        return false;

    // unschedule statistics task
    _statsTask.Kill();

    // flush event queue
    Lock();
    _queue.FlushPackets_NoLock(&_myQueue);
    Unlock();

    // discard remaining events
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

    delete [] _events;

    Lock();
    _finished = true;
    if (_waitFinished)
        Broadcast();
    Unlock();

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
    if (!InitEventLoop()) {
        LOG(warning, "Transport: Run: Could not init event loop");
        return;
    }
    while (EventLoopIteration()) {
        if (thisThread != nullptr && thisThread->GetBreakFlag())
            ShutDown(false);
    }
}
