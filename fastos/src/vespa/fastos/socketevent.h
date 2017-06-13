// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/types.h>
#include <vespa/fastos/mutex.h>
#include <poll.h>

#include <sys/epoll.h>
#include <vector>

class FastOS_IOEvent
{
public:
    bool _readOccurred, _writeOccurred;
    void *_eventAttribute;
};

class FastOS_SocketEvent;
class FastOS_SocketInterface;

class FastOS_SocketEventObjects
{
private:
    FastOS_SocketEventObjects(const FastOS_SocketEventObjects&);
    FastOS_SocketEventObjects& operator=(const FastOS_SocketEventObjects&);

    static FastOS_Mutex _listMutex;
    static int          _objectCount;
    static bool         _initialized;

    bool Init (FastOS_SocketEvent *event);
    void Cleanup ();
    void ReleasedCleanup(void);

public:
    FastOS_SocketEventObjects *_next;
    bool                       _initOk;
    FastOS_SocketInterface   **_socketArray;
    unsigned int               _socketArrayAllocSize;
    pollfd                    *_pollfds;
    unsigned int               _pollfdsAllocSize;
    int                        _wakeUpPipe[2];

    FastOS_SocketEventObjects(FastOS_SocketEvent *event);
    ~FastOS_SocketEventObjects ();
    static FastOS_SocketEventObjects *_objects;
    static FastOS_SocketEventObjects *ObtainObject (FastOS_SocketEvent *event);
    static void ReleaseObject (FastOS_SocketEventObjects *node);
    static void ClassCleanup(void);
    static void InitializeClass(void);
};


/**
 * This class is used to handle events caused by @ref FastOS_Socket
 * instances.
 *
 * A @ref FastOS_SocketEvent can be associated with multiple sockets.
 * Use @ref FastOS_Socket::EnableReadEvent() and
 * @ref FastOS_Socket::EnableWriteEvent() to register for read and
 * and write events.
 *
 * A @ref Wait() sleeps until either the specified timeout elapses,
 * or one or more socket events trigger.
 *
 * After a @ref Wait() with return code true, @ref QueryReadEvent()
 * and @ref QueryWriteEvent() is used to find the socket(s) that
 * caused an event.
 *
 * Example:
 * @code
 *    // Simple connection class
 *    class Connection
 *    {
 *    public:
 *       Connection *_next;  // Single-linked list of connections
 *
 *       FastOS_Socket *_socket;
 *       void HandleReadEvent ();
 *       void HandleWriteEvent ();
 *    };
 *
 *    void EventExample (Connection *connections)
 *    {
 *       Connection *conn;
 *       FastOS_SocketEvent socketEvent;
 *
 *       // Walk through single-linked list of connections
 *       for(conn=connections; conn!=NULL; conn = conn->_next)
 *       {
 *          // Associate each socket with socketEvent
 *          conn->_socket->SetSocketEvent(&socketEvent);
 *
 *          // Enable read event notifications
 *          conn->_socket->EnableReadEvent(true);
 *
 *          // In this example, we pretend that write events are turned
 *          // on somewhere else.
 *       }
 *
 *       for(;;)    // Event handling loop (loop forever)
 *       {
 *          // Wait for events (timeout = 200ms)
 *          if(socketEvent.Wait(200))
 *          {
 *             // Walk through list of connections
 *             for(conn=connections; conn!=NULL; conn = conn->_next)
 *             {
 *                // For each socket, check for read event
 *                if(socketEvent.QueryReadEvent(conn->_socket))
 *                {
 *                   conn->HandleReadEvent();
 *                }
 *
 *                // ..and write event
 *                if(socketEvent.QueryWriteEvent(conn->_socket))
 *                {
 *                   conn->HandleWriteEvent();
 *                }
 *             }
 *          }
 *          else
 *          {
 *             // Timeout
 *          }
 *       }
 *    }
 * @endcode
 */
class FastOS_SocketEvent
{
    friend class FastOS_SocketInterface;
    friend class FastOS_SocketEventObjects;

private:
    FastOS_SocketEvent(const FastOS_SocketEvent&);
    FastOS_SocketEvent& operator=(const FastOS_SocketEvent&);
protected:

    int                      _epollfd;     // fd of epoll kernel service
    std::vector<epoll_event> _epollEvents; // internal epoll event storage

    int _socketsInArray;
    int _getEventsIndex;

    bool _wokeUp;

    FastOS_SocketEventObjects *_objs;

    bool HandleWakeUp ();
    void EnableEvent (FastOS_SocketInterface *sock, bool read, bool write) {
        epollEnableEvent(sock, read, write);
    }

    bool epollInit();
    bool epollEnableEvent(FastOS_SocketInterface *sock, bool read, bool write);
    bool epollWait(bool &error, int msTimeout);
    int  epollGetEvents(bool *wakeUp, int msTimeout,
                        FastOS_IOEvent *events, int maxEvents);
    void epollFini();

public:
    FastOS_SocketEvent ();
    ~FastOS_SocketEvent ();

    /**
     * Was the socketevent object created successfully?
     * @return  Boolean success/failure
     */
    bool GetCreateSuccess () {
        if (_epollfd == -1) {
            return false;
        }
        return (_objs != NULL) ? _objs->_initOk : false;
    }

    /**
     * Wait for a socket event, or timeout after [msTimeout] milliseconds.
     *
     * @param  error            This will be set to true if an error occured.
     *                          If Wait succeeds, this will always be false.
     * @param  msTimeout        Number of milliseconds to wait for an event
     *                          before timeout. -1 means wait forever.
     *
     * @return                  True if an event occurred, else false.
     */
    bool Wait (bool &error, int msTimeout) {
        return epollWait(error, msTimeout);
    }

    /**
     * Wait for socket event, or timeout after [msTimeout] milliseconds.
     * An array of IO events is filled in.
     *
     * @param  wakeUp           Has a wakeup occurred? (out parameter)
     * @param  msTimeout        Number of milliseconds to wait for an event
     *                          before timeout. -1 means wait forever.
     * @param  events           Pointer to FastOS_IOEvent array
     * @param  maxEvents        Size of event array. Up to this many events
     *                          may be filled in the array. Invoke the method
     *                          multiple times to get all events if the array
     *                          is too small to hold all events that occurred.
     *
     * @return                  Number of events occurred, or -1 on failure.
     */
    int GetEvents (bool *wakeUp, int msTimeout, FastOS_IOEvent *events, int maxEvents) {
        return epollGetEvents(wakeUp, msTimeout, events, maxEvents);
    }

    /**
     * Make FastOS_SocketEvent methods Wait/GetEvents
     * stop waiting and return ASAP.
     */
    void AsyncWakeUp (void);

    void Attach(FastOS_SocketInterface *sock, bool read, bool write);

    void Detach(FastOS_SocketInterface *sock);

    /**
     * Check for a read-event with socket [socket].
     *
     * This method will also clear the read event indication for the
     * given socket, making this method return false for the given
     * socket until another event has been detected by invoking
     * Wait/GetEvents.
     *
     * @return            True if an event occurred, else false.
     */
    bool QueryReadEvent (FastOS_SocketInterface *socket);

    /**
     * Check for a write-event with socket [socket].
     *
     * This method will also clear the write event indication for the
     * given socket, making this method return false for the given
     * socket until another event has been detected by invoking
     * Wait/GetEvents.
     *
     * @return            True if an event occurred, else false.
     */
    bool QueryWriteEvent (FastOS_SocketInterface *socket);
};
