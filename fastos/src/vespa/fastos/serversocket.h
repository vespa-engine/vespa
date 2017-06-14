// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * @file
 * Class definitons for FastOS_SocketFactory and FastOS_ServerSocket.
 *
 * @author  Div, Oivind H. Danielsen
 */

#pragma once


#include <vespa/fastos/socket.h>


/**
 * This base class is used to create Socket objects. You can supply
 * a subclassed @ref FastOS_SocketFactory to an instance of
 * @ref FastOS_ServerSocket, to have your own type of socket created
 * by @ref FastOS_ServerSocket::Accept().
 */
class FastOS_SocketFactory
{
public:
    /**
     * Destructor.  No cleanup needed for base class.
     */
    virtual ~FastOS_SocketFactory(void) { }

    /**
     * Create a socket object. Override this method to create
     * your own subclassed socket objects. It is not allowed
     * for the constructor to use the socket object, as it
     * is not set up yet at this point.
     */
    virtual FastOS_SocketInterface *CreateSocket()
    {
        return new FastOS_Socket();
    }
};


/**
 * This socket class provides a listening server socket that is
 * able to accept connections on a specified port number.
 *
 * The port number and connection backlog are specified in the
 * constructor  * (FastOS_ServerSocket(int portnum, int backLog)).
 *
 * Call @ref Listen() to create and configure the socket for
 * listening on the specified port number.
 *
 * To accept an incoming connection, call @ref Accept(). This will
 * return a newly created @ref FastOS_Socket object to handle
 * the new connection. If you want a different type of socket,
 * specify your own @ref FastOS_SocketFactory with @ref SetSocketFactory().
 */
class FastOS_ServerSocket : public FastOS_Socket
{
private:
    FastOS_ServerSocket(const FastOS_ServerSocket&);
    FastOS_ServerSocket& operator=(const FastOS_ServerSocket&);

protected:
    /**
     * The TCP port number to listen to.
     */
    int _portNumber;

    /**
     * Max number of connections in backlog.
     */
    int _backLog;

    /**
     * The socket factory to use for incoming connections.
     * If this is NULL, the default action is to create an
     * instance of the regular @ref FastOS_Socket.
     */
    FastOS_SocketFactory *_socketFactory;

    /**
     * Create socket for handling an incoming connection
     * @return    Returns pointer to newly created socket, or NULL on
     *            failure.
     */
    FastOS_SocketInterface *CreateHandlerSocket();

    bool _validAddress;

public:
    /**
     * Constructor. If strict binding is used, call @ref GetValidAddressFlag()
     * to check if setting the specified address was successful or not.
     * @param portnum        Listen on this port number.
     * @param backLog        Max number of connections in backlog.
     * @param socketFactory  See @ref SetSocketFactory().
     * @param strictBindHostName   IP address or hostname for strict binding
     */
    FastOS_ServerSocket (int portnum, int backLog=5,
                         FastOS_SocketFactory *socketFactory=NULL,
                         const char *strictBindHostName=NULL)
        : _portNumber(portnum),
          _backLog(backLog),
          _socketFactory(socketFactory),
          _validAddress(false)
    {
        setPreferIPv6(true);
        _validAddress = SetAddress(_portNumber, strictBindHostName);
    }

    bool GetValidAddressFlag () { return _validAddress; }

    /**
     * Use this constructor to supply a pre-created, configured,
     * bound and listening socket. When using this constructor,
     * don't call @ref Listen().
     * @param socketHandle   OS handle of supplied socket.
     * @param socketFactory  See @ref SetSocketFactory().
     */
    FastOS_ServerSocket(int socketHandle,
                        FastOS_SocketFactory *socketFactory)
        : _portNumber(-1),
          _backLog(-1),
          _socketFactory(socketFactory),
          _validAddress(false)
    {
        _socketHandle = socketHandle;
        memset(&_address, 0, sizeof(_address));
        _validAddress = true;
    }

    /**
     * Create a listening socket. This involves creating an OS
     * socket, setting SO_REUSEADDR(true), binding the socket and
     * start to listen for incoming connections. You should
     * call @ref Listen() if you have supplied a pre-created listening
     * socket handle trough the constructor
     * @ref FastOS_ServerSocket(int listenSocketHandle, FastOS_SocketFactory *socketFactory=NULL).
     * @return    Boolean success/failure
     */
    bool Listen ();

    /**
     * Accept incoming connections. The socket factory (if present)
     * is used to create a socket instance for the new connection.
     * Make sure you have a listening socket (see @ref Listen()) before
     * calling @ref Accept().
     * @return   Returns pointer to newly created socket object for the
     *           connection that was accepted, or NULL on failure.
     */
    FastOS_SocketInterface *Accept ();

    /**
     * Accept incoming connections. This version does not use the
     * associated socket factory.
     * Make sure you have a listening socket (see @ref Listen()) before
     * calling @ref AcceptPlain().
     * @return   Returns pointer to newly created socket object for the
     *           connection that was accepted, or NULL on failure.
     */
    FastOS_Socket *AcceptPlain ();

    /**
     * Specify your own @ref FastOS_SocketFactory for this serversocket.
     * When new connections are accepted with @ref Accept, this socket
     * factory will be called to create a new socket object for the
     * connection.
     *
     * SetSocketFactory(NULL) will enable the default socket factory
     * mechanism which will create regular @ref FastOS_Socket instances
     * on accepted connections.
     */
    void SetSocketFactory(FastOS_SocketFactory *socketFactory);

    /**
     * Return the portnumber of the listing socket.
     * @return Port number.
     */
    int GetPortNumber ()
    {
        return _portNumber;
    }
};


