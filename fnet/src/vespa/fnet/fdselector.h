// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iocomponent.h"
#include "context.h"
#include <vespa/fastos/socket.h>

class FNET_FDSelector;
class FNET_Transport;

/**
 * Interface used to listen for events from a @ref FNET_FDSelector io
 * component.
 **/
class FNET_IFDSelectorHandler
{
public:
    /**
     * This method is called by the transport thread when a read is
     * possible on the underlying filedescriptor of the given
     * selector.
     *
     * @param source the source of this event
     **/
    virtual void readEvent(FNET_FDSelector *source) = 0;

    /**
     * This method is called by the transport thread when a write is
     * possible on the underlying filedescriptor of the given
     * selector.
     *
     * @param source the source of this event
     **/
    virtual void writeEvent(FNET_FDSelector *source) = 0;

protected:
    /**
     * Empty. Implemented just to keep gcc happy. Protected to avoid
     * destruction through interface pointer.
     **/
    virtual ~FNET_IFDSelectorHandler() {}
};


/**
 * This is an adapter class used to wait for read/write events on a
 * generic file descriptor. The file descriptor is owned by the
 * application, and no other operations than checking for read/write
 * availability will be performed on it. Objects of this class will be
 * hooked into the io component framework of FNET, and will therefore
 * have a lifetime controlled by reference counting. The way to use
 * this class is to pair it up with the @ref FNET_IFDSelectorHandler
 * interface. Let an object in the application inherit from the
 * selector handler interface. Create an fd selector using the
 * application handler. Appropriate events will be delivered to the
 * selector handler. When no more events are wanted, use the @ref
 * dispose method to get rid of the selector.
 **/
class FNET_FDSelector : public FNET_IOComponent
{
    FNET_FDSelector(const FNET_FDSelector&); // not used
    FNET_FDSelector& operator= (const FNET_FDSelector&); // not used
public:
#ifndef IAM_DOXYGEN
    class FDSpec
    {
    private:
        char _buf[64];
    public:
        FDSpec(int fd) : _buf() { sprintf(_buf, "fd/%d", fd); }
        const char *spec() const { return _buf; }
    };

    class FDSocket : public FastOS_Socket
    {
    public:
        FDSocket(int fd) : FastOS_Socket() { _socketHandle = fd; }
        bool valid() const { return _socketHandle != -1; }
        ~FDSocket() { _socketHandle = -1; }
    };
#endif // DOXYGEN

private:
    int                      _fd;
    FDSocket                 _fdSocket;
    FNET_IFDSelectorHandler *_handler;
    FNET_Context             _context;
    bool                     _eventBusy;
    bool                     _eventWait;

    /**
     * If an event is being delivered, wait until that event is
     * delivered.
     **/
    void waitEvent()
    {
        while (_eventBusy) {
            _eventWait = true;
            Wait();
        }
    }

    /**
     * Called directly before an event is delivered to synchronize
     * with the @ref waitEvent method.
     **/
    void beforeEvent()
    {
        _eventBusy = true;
        Unlock();
    }

    /**
     * Called directly after an event is delivered to synchronize with
     * the @ref waitEvent method.
     **/
    void afterEvent()
    {
        Lock();
        _eventBusy = false;
        if (_eventWait) {
            _eventWait = false;
            Broadcast();
        }
    }

public:
    /**
     * Construct a file descriptor selector. The created selector is
     * automatically added to one of the event loops controlled by the
     * transport object.
     *
     * @param transport the transport layer
     * @param fd the underlying file descriptor
     * @param handler the handler for this selector
     * @param context the application context for this selector
     **/
    FNET_FDSelector(FNET_Transport *transport, int fd,
                    FNET_IFDSelectorHandler *handler,
                    FNET_Context context = FNET_Context());

    /**
     * Obtain the file descriptor associated with this selector.
     *
     * @return file descriptor
     **/
    int getFD() { return _fd; }

    /**
     * Obtain the application context for this selector.
     *
     * @return the application context for this selector
     **/
    FNET_Context getContext() { return _context; }

    /**
     * Enable/disable read events. This method only acts as a proxy
     * that will notify the transport loop about the new selection.
     * When a selection is changed it may not take effect right away
     * (events already in the pipeline will still be delivered). This
     * means that the application must be able to handle events that
     * are delivered after events have been disabled.
     *
     * @param wantRead true if we want read events.
     **/
    void updateReadSelection(bool wantRead);

    /**
     * Enable/disable write events. This method only acts as a proxy
     * that will notify the transport loop about the new selection.
     * When a selection is changed it may not take effect right away
     * (events already in the pipeline will still be delivered). This
     * means that the application must be able to handle events that
     * are delivered after events have been disabled.
     *
     * @param wantWrite true if we want write events.
     **/
    void updateWriteSelection(bool wantWrite);

    /**
     * This method is used to dispose of this selector. If an event
     * callback is in progress when this method is called, it will
     * block until that callback is finished. This ensures that no
     * more events will be delivered from this selector after this
     * method has returned. This method also acts as an implicit
     * SubRef, invalidating the application pointer to this
     * object. Note: calling this method from either of the event
     * delivery methods in the selector handler interface will result
     * in a deadlock, since the calling thread will be waiting for
     * itself to complete the callback.
     **/
    void dispose();

protected:
    /**
     * Destructor. Should not be invoked from the application. Use the
     * @ref dispose method to get rid of selector objects.
     **/
    ~FNET_FDSelector();

    /**
     * This method is called from the transport thread to close this
     * io component. This method performs internal cleanup related to
     * the io component framework used in FNET.
     **/
    void Close();

    /**
     * This method is called by the transport thread when the
     * underlying file descriptor is ready for reading.
     **/
    bool HandleReadEvent();

    /**
     * This method is called by the transport layer when the
     * underlying file descriptor is ready for writing.
     **/
    bool HandleWriteEvent();
};

