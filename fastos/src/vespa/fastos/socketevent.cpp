// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/socketevent.h>
#include <vespa/fastos/socket.h>

FastOS_SocketEventObjects *FastOS_SocketEventObjects::_objects = NULL;
FastOS_Mutex FastOS_SocketEventObjects::_listMutex;
int FastOS_SocketEventObjects::_objectCount = 0;
bool FastOS_SocketEventObjects::_initialized = false;

FastOS_SocketEvent::FastOS_SocketEvent () :
    _epollfd(-1),
    _epollEvents(),
    _socketsInArray(0),
    _getEventsIndex(0),
    _wokeUp(false),
    _objs(NULL)
{

    _objs = FastOS_SocketEventObjects::ObtainObject(this);

    if(_objs != NULL) {
        if(_objs->_initOk) {
        }
    }
    epollInit(); // must be done after obtaining _objs
}

FastOS_SocketEvent::~FastOS_SocketEvent ()
{
    // Clean out potential remaining wakeup events.
    bool error;
    Wait(error, 0);

    epollFini(); // must be done before releasing _objs
    FastOS_SocketEventObjects::ReleaseObject(_objs);
}

bool FastOS_SocketEvent::HandleWakeUp ()
{
    int wakeUpReadHandle = _objs->_wakeUpPipe[0];

    const int MAX_WAKEUP_PIPE_READ = 128;
    char dummyBytes[MAX_WAKEUP_PIPE_READ];

    ssize_t readCount = read(wakeUpReadHandle, dummyBytes, MAX_WAKEUP_PIPE_READ);
    (void) readCount;
    _wokeUp = true;
    return true;
}

FastOS_SocketEventObjects *FastOS_SocketEventObjects::ObtainObject (FastOS_SocketEvent *event)
{
    FastOS_SocketEventObjects *node;
    _listMutex.Lock();

    if(_objects == NULL)
    {
        _objectCount++;
        _listMutex.Unlock();

        node = new FastOS_SocketEventObjects(event);
        node->_next = NULL;
    }
    else
    {
        node = _objects;
        _objects = node->_next;
        node->_next = NULL;

        _listMutex.Unlock();
    }

    return node;
}

void FastOS_SocketEventObjects::ReleaseObject (FastOS_SocketEventObjects *node)
{
    if (node != NULL)
        node->ReleasedCleanup();
    _listMutex.Lock();

    if (_initialized) {
        node->_next = _objects;
        _objects = node;
    } else {
        delete node;
        _objectCount--;
    }

    _listMutex.Unlock();
}


bool
FastOS_SocketEvent::epollInit()
{
    _epollfd = epoll_create(4093);
    if (_epollfd != -1 && _objs != NULL && _objs->_initOk) {
        epoll_event evt;
        evt.events = EPOLLIN;
        evt.data.ptr = 0;
        if (epoll_ctl(_epollfd, EPOLL_CTL_ADD, _objs->_wakeUpPipe[0], &evt) == 0) {
            return true; // SUCCESS
        }
    }
    epollFini();
    return false;
}

bool
FastOS_SocketEvent::epollEnableEvent(FastOS_SocketInterface *sock,
                                     bool read, bool write)
{
    int res = 0;
    epoll_event evt;
    evt.events = (read ? static_cast<uint32_t>(EPOLLIN) : 0) | (write ? static_cast<uint32_t>(EPOLLOUT) : 0);
    evt.data.ptr = (void *) sock;
    if (sock->_epolled) {
        if (evt.events != 0) { // modify
            res = epoll_ctl(_epollfd, EPOLL_CTL_MOD, sock->_socketHandle, &evt);
        } else {               // remove
            // NB: old versions of epoll_ctl needs evt also for remove
            res = epoll_ctl(_epollfd, EPOLL_CTL_DEL, sock->_socketHandle, &evt);
            sock->_epolled = false;
        }
    } else {
        if (evt.events != 0) { // add
            res = epoll_ctl(_epollfd, EPOLL_CTL_ADD, sock->_socketHandle, &evt);
            sock->_epolled = true;
        }
    }
    if (res == -1) {
        perror("epollEnableEvent");
        return false;
    }
    return true;
}

bool
FastOS_SocketEvent::epollWait(bool &error, int msTimeout)
{
    _wokeUp = false;
    int maxEvents = 256;
    if ((int)_epollEvents.size() < maxEvents) {
        _epollEvents.resize(maxEvents);
    }
    int res = epoll_wait(_epollfd, &_epollEvents[0], maxEvents, msTimeout);
    error = (res == -1);
    for (int i = 0; i < res; ++i) {
        const epoll_event &evt = _epollEvents[i];
        FastOS_SocketInterface *sock = (FastOS_SocketInterface *) evt.data.ptr;
        if (sock == NULL) {
            HandleWakeUp();
        } else {
            sock->_readPossible  = sock->_readEventEnabled &&
                                   ((evt.events & (EPOLLIN  | EPOLLERR | EPOLLHUP)) != 0);
            sock->_writePossible = sock->_writeEventEnabled &&
                                   ((evt.events & (EPOLLOUT | EPOLLERR | EPOLLHUP)) != 0);
        }
    }
    return (res > 0);
}

int
FastOS_SocketEvent::epollGetEvents(bool *wakeUp, int msTimeout,
                                   FastOS_IOEvent *events, int maxEvents)
{
    _wokeUp = false;
    if ((int)_epollEvents.size() < maxEvents) {
        _epollEvents.resize(maxEvents);
    }
    int res = epoll_wait(_epollfd, &_epollEvents[0], maxEvents, msTimeout);
    if (res <= 0) {
        return res;
    }
    int idx = 0; // application event index
    for (int i = 0; i < res; ++i) {
        const epoll_event &evt = _epollEvents[i];
        FastOS_IOEvent &appEvt = events[idx];
        FastOS_SocketInterface *sock = (FastOS_SocketInterface *) evt.data.ptr;
        if (sock == NULL) {
            HandleWakeUp(); // sets _wokeUp
        } else {
            appEvt._readOccurred = sock->_readEventEnabled &&
                                   ((evt.events & (EPOLLIN  | EPOLLERR | EPOLLHUP)) != 0);
            appEvt._writeOccurred = sock->_writeEventEnabled &&
                                    ((evt.events & (EPOLLOUT | EPOLLERR | EPOLLHUP)) != 0);
            appEvt._eventAttribute = sock->_eventAttribute;
            ++idx;
        }
    }
    *wakeUp = _wokeUp;
    return idx;
}

void
FastOS_SocketEvent::epollFini()
{
    if (_epollfd != -1) {
        // do we need to unregister pipe read before closing?
        int res = close(_epollfd);
        if (res == -1) {
            perror("epollFini");
        }
        _epollfd = -1;
    }
}

void
FastOS_SocketEventObjects::InitializeClass(void)
{
    _listMutex.Lock();
    _initialized = true;
    _listMutex.Unlock();
}


void FastOS_SocketEventObjects::ClassCleanup(void)
{
    _listMutex.Lock();
    _initialized = false;
    for (;;)
    {
        FastOS_SocketEventObjects *node = _objects;

        if(node == NULL)
            break;
        else
        {
            _objects = node->_next;
            delete node;
            _objectCount--;
        }
    }
    _listMutex.Unlock();
}


FastOS_SocketEventObjects::FastOS_SocketEventObjects(FastOS_SocketEvent *event)
    : _next(NULL),
      _initOk(false),
      _socketArray(NULL),
      _socketArrayAllocSize(0u),
      _pollfds(NULL),
      _pollfdsAllocSize(0)
{
    // Connect ourselves to the socketevent object.
    event->_objs = this;

    _initOk = Init(event);
}

void
FastOS_SocketEventObjects::ReleasedCleanup(void)
{
    if (_socketArrayAllocSize > 16) {
        delete [] _socketArray;
        _socketArray = NULL;
        _socketArrayAllocSize = 0;
    }
    if (_pollfdsAllocSize > 16) {
        free(_pollfds);
        _pollfds = NULL;
        _pollfdsAllocSize = 0;
    }
}


FastOS_SocketEventObjects::~FastOS_SocketEventObjects ()
{
    Cleanup();
    delete [] _socketArray;
    free(_pollfds);
}


void
FastOS_SocketEvent::Attach(FastOS_SocketInterface *sock,
                           bool readEnabled,
                           bool writeEnabled)
{
    assert(!sock->_epolled);
    if (readEnabled || writeEnabled)
        EnableEvent(sock, readEnabled, writeEnabled);
}


void
FastOS_SocketEvent::Detach(FastOS_SocketInterface *sock)
{
    if (sock->_readEventEnabled || sock->_writeEventEnabled)
        EnableEvent(sock, false, false);
}

void FastOS_SocketEvent::AsyncWakeUp (void)
{
    char dummy = 'c';
    size_t writeCount = write(_objs->_wakeUpPipe[1], &dummy, 1);
    (void) writeCount;
}

bool FastOS_SocketEvent::QueryReadEvent (FastOS_SocketInterface *sock)
{
    bool ret = sock->_readPossible;
    sock->_readPossible = false;
    return ret;
}

bool FastOS_SocketEvent::QueryWriteEvent (FastOS_SocketInterface *sock)
{
    bool ret = sock->_writePossible;
    sock->_writePossible = false;
    return ret;
}
