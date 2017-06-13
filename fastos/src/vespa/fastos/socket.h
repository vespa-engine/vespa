// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/types.h>

#include <string>
#include <vector>

class FastOS_SocketInterface;
class FastOS_ServerSocket;
class FastOS_SocketEvent;

#include <vespa/fastos/socketevent.h>

/**
 * This class implements TCP/IP network socket.
 */
class FastOS_SocketInterface
{
private:
    friend class FastOS_SocketEvent;

protected:
    bool                    _readEventEnabled;
    bool                    _writeEventEnabled;
    bool                    _readPossible;
    bool                    _writePossible;
    bool                    _epolled; // true -> part of epoll set
    FastOS_SocketEvent     *_socketEvent;
    void                   *_eventAttribute;
    int                     _socketEventArrayPos;
    struct sockaddr_storage _address;
    int                     _socketHandle;
    bool                    _preferIPv6;

    /**
     * Cancel all socket events and detach from the current socket
     * event, if any.
     */
    void CleanupEvents ();

    /**
     * Is a valid socket handle associated with this instance?
     * @return  True if the handle is valid, else false.
     */
    bool ValidHandle () const { return (_socketHandle != -1); }

    /**
     * If no OS socket is yet associated with this instance,
     * create one.
     * @return     Boolean success/failure
     */
    bool CreateIfNoSocketYet();

    void ConstructorWork();

public:
    FastOS_SocketInterface (const FastOS_SocketInterface&) = delete;
    FastOS_SocketInterface& operator=(const FastOS_SocketInterface&) = delete;
    // Static members

    /**
     * Convenience method. Does GetErrorString(GetLastError())
     * @return Error string
     */
    static std::string getLastErrorString(void);

    static const char *InitializeServices ();
    static void CleanupServices ();

    /**
     * Default constructor. Use @ref SetUp() or the various SetAddress..()
     * methods to complete the socket setup.
     */
    FastOS_SocketInterface();
    /**
     * This constructor does @ref SetUp() with the specified parameters.
     * @param  socketHandle    OS handle to already created socket
     * @param  hostAddress     IP address or host name of remote host
     */
    FastOS_SocketInterface(int socketHandle, struct sockaddr *hostAddress);

    /**
     * Destructor
     * The socket will be closed unless it was supplied by the caller.
     */
    virtual ~FastOS_SocketInterface();

    /**
     * Setup of socket parameters from explicit address and socket handle
     * If a socket is already associated with this instance it will be
     * closed (see @ref Close()).
     * The socket is closed when the socket instance is deleted.
     * @param  socketHandle    OS handle to already created socket
     * @param  hostAddress     Address of remote host
     */
    void SetUp(int socketHandle, struct sockaddr *hostAddress);

    /**
     * Set address of host to connect to.
     * @param  portNum         IP port number of remote host
     * @param  hostAddress     IP address or host name of remote host
     * @return                 Boolean success/failure
     */
    bool SetAddress(const int portNum, const char *hostAddress);

    /**
     * Set address of host to connect to
     * @param  portNum         IP port number of remote host
     * @param  hostName        Hostname of remote host
     * @return                 Boolean success/failure
     */
    bool SetAddressByHostName (const int portNum, const char *hostName);

    /**
     * Connects to the host using the IP Address and port number predefined.
     * @return      Boolean success/failure
     */
    bool Connect();

    /**
     * Connects to the host using the IP Address and port number specified.
     * @return      Boolean success/failure
     */
    bool Connect(const char *hostNameOrIPaddress, const int portNum) {
        return SetAddress(portNum, hostNameOrIPaddress) ? Connect() : false;
    }

    /**
     * Attempts to retrieve the local port number for the socket.
     * The socket must be connected when this method is called.
     * @return     Local port number or -1 on error.
     */
    int GetLocalPort ();

    /**
     * Read [buffersize] bytes to [readbuffer]. The socket must
     * have a connection (see @ref Connect()) before a read is
     * attempted.
     * @param  readBuffer    Pointer to target memory buffer
     * @param  bufferSize    Size of the buffer / bytes to read
     * @return               Returns number of bytes read.
     */
    virtual ssize_t Read (void *readBuffer, size_t bufferSize) = 0;

    /**
     * Write [buffersize] bytes from [readbuffer].  The socket must
     * have a connection (see @ref Connect()) before a write is
     * attempted.
     * @param  writeBuffer   Pointer to target memory buffer
     * @param  bufferSize    Size of the buffer / bytes to write
     * @return               Returns number of bytes written.
     */
    virtual ssize_t Write (const void *writeBuffer, size_t bufferSize) = 0;

    /**
     * Close the socket.
     * Only socket handles created within this class are closed. This
     * means user-supplied socket handles (through constructor or
     * @ref SetUp) will not be closed.
     * If the socket is already closed, the method call will return
     * success.
     * @return     Boolean success/failure
     */
    virtual bool Close () = 0;

    /**
     * Shut down a connection.
     * If the socket is already closed, the method call will return
     * success. Socket write events are disabled.
     * @return     Boolean success/failure
     */
    virtual bool Shutdown() = 0;

    /**
     * Get socket error
     * If getting the socket error fails, the error code of GetLastError()
     * is returned instad. If getting the socket error succeds with a wrong
     * parameter EINVAL is returned.
     * @return          Socket error
     */
    int GetSoError();

    /**
     * Set SO_KEEPALIVE flag on socket
     * @param  keep            SO_KEEPALIVE on (true) / off (false)
     * @return                 Boolean success/failure
     */
    bool SetSoKeepAlive (bool keep) {
        return SetSoIntOpt(SO_KEEPALIVE, keep ? 1 : 0);
    }

    /**
     * Set SO_REUSEADDR flag on socket
     * @param  reuse           SO_REUSEADDR on (true) / off (false)
     * @return                 Boolean success/failure
     */
    bool SetSoReuseAddr (bool reuse) {
        return SetSoIntOpt(SO_REUSEADDR, reuse ? 1 : 0);
    }

    /**
     * Set SO_LINGER flag on socket
     * @param  doLinger        SO_LINGER on (true) / off (false)
     * @param  seconds         Seconds to linger after close
     * @return                 Boolean success/failure
     */
    bool SetSoLinger (bool doLinger, int seconds);

    /**
     * Set TCP Nodelay option on socket
     * @param  noDelay         Don't delay data (true) / delay data (false)
     * @return                 Boolean success/failure
     */
    bool SetNoDelay(bool noDelay);
    /**
     * Set socket option
     * @param  option          Number of option to set
     * @param  value           Value of option to set
     * @return                 Boolean success/failure
     */
    bool SetSoIntOpt (int option, int value);

    /**
     * Get socket option
     * @param  option          Number of option to get
     * @param  value           Ref to variable for holding the value of option
     * @return                 Boolean success/failure
     */
    bool GetSoIntOpt (int option, int &value);

    /**
     * Set blocking flag on socket
     * @param blockingEnabled  SO_BLOCKING on (true) / off (false)
     * @return Boolean success/failure
     */
    virtual bool SetSoBlocking (bool blockingEnabled)=0;

    /**
     * Associate a socket event object with this socket.
     * Any events registered with an already associated event
     * object is cancelled.
     * @param  event       Socket event object
     * @param  attribute   Event attribute pointer
     * @return Boolean success/failure.
     */
    bool SetSocketEvent (FastOS_SocketEvent *event, void *attribute=NULL);

    /**
     * Get socket event object
     * @return             Associated socket event object or NULL
     */
    FastOS_SocketEvent *GetSocketEvent () { return _socketEvent; }

    /**
     * Enable or disable read events for the socket.
     * The behaviour caused by invoking this method while waiting for
     * socket events is undefined.
     * A @ref FastOS_SocketEvent must be associated with the socket prior
     * to calling @ref EnableReadEvent and @ref EnableWriteEvent.
     */
    void EnableReadEvent (bool enabled);

    /**
     * Enable or disable write events for the socket.
     * The behaviour caused by invoking this method while waiting for
     * socket events is undefined.
     * A @ref FastOS_SocketEvent must be associated with the socket prior
     * to calling @ref EnableReadEvent and @ref EnableWriteEvent.
     */
    void EnableWriteEvent (bool enabled);

    /**
     * Is the socket open?
     * @return True if opened, false if cloed.
     */
    bool IsOpened () const {
        return ValidHandle();
    }

    /**
     * Return socket port.
     */
    unsigned short GetPort () const
    {
        switch (_address.ss_family) {
        case AF_INET:
            return reinterpret_cast<const sockaddr_in &>(_address).sin_port;
        case AF_INET6:
            return reinterpret_cast<const sockaddr_in6 &>(_address).sin6_port;
        default:
            return 0;
        }
    }

    /**
     * Tune the socket for transport use.
     * This includes:
     *  SO_KEEPALIVE = 1
     *  SO_LINGER = 0
     *
     * @return Boolean success/failure
     */
    bool TuneTransport ();
    bool getPreferIPv6(void) const { return _preferIPv6; }
    void setPreferIPv6(bool preferIPv6) { _preferIPv6 = preferIPv6; }
};

#include <vespa/fastos/unix_socket.h>
typedef FastOS_UNIX_Socket FASTOS_PREFIX(Socket);



