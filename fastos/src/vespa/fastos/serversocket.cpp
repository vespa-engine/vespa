// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//************************************************************************
/**
 * Implementation of FastOS_ServerSocket methods.
 *
 * @author  Div, Oivind H. Danielsen
 */

#include <vespa/fastos/serversocket.h>


/**
 * Set the socket factory object. Usefull if you want the ServerSocket to
 * create a custom typed socket on incoming connections.
 * @param  socketFactory   Pointer to socket factory object to be used.
 */
void FastOS_ServerSocket::SetSocketFactory(FastOS_SocketFactory *socketFactory)
{
    _socketFactory = socketFactory;
}


FastOS_SocketInterface *FastOS_ServerSocket::CreateHandlerSocket(void)
{
    FastOS_SocketInterface *newSocket = NULL;

    if (_socketFactory != NULL)
    {
        newSocket = _socketFactory->CreateSocket();
    }
    else
    {
        newSocket = new FastOS_Socket();
    }

    return newSocket;
}


FastOS_SocketInterface *FastOS_ServerSocket::Accept()
{
    FastOS_SocketInterface  *handlerSocket = NULL;
    int                      handlerSocketHandle;
    struct sockaddr_storage  clientAddress;

    socklen_t clientAddressLength = sizeof(clientAddress);

    memset(&clientAddress, 0, sizeof(clientAddress));

    handlerSocketHandle = accept(_socketHandle,
                                 reinterpret_cast<struct sockaddr *>(&clientAddress),
                                 &clientAddressLength);

    if (handlerSocketHandle >= 0)
    {
        handlerSocket = CreateHandlerSocket();

        if (handlerSocket != NULL)
        {
            handlerSocket->SetUp(handlerSocketHandle,
                                 reinterpret_cast<struct sockaddr *>(&clientAddress));
        }
    }

    return handlerSocket;
}

FastOS_Socket *FastOS_ServerSocket::AcceptPlain()
{
    FastOS_Socket      *handlerSocket = NULL;
    int                 handlerSocketHandle;
    struct sockaddr_storage clientAddress;

    socklen_t clientAddressLength = sizeof(clientAddress);

    memset(&clientAddress, 0, sizeof(clientAddress));

    handlerSocketHandle = accept(_socketHandle,
                                 reinterpret_cast<struct sockaddr *>(&clientAddress),
                                 &clientAddressLength);

    if (handlerSocketHandle >= 0)
    {
        handlerSocket = new FastOS_Socket();

        if (handlerSocket != NULL)
        {
            handlerSocket->SetUp(handlerSocketHandle,
                                 reinterpret_cast<struct sockaddr *>(&clientAddress));
        }
    }

    return handlerSocket;
}


bool FastOS_ServerSocket::Listen ()
{
    bool rc=false;
    bool reuseAddr = false;

    if(CreateIfNoSocketYet())
    {
        if ((_address.ss_family == AF_INET &&
             reinterpret_cast<const sockaddr_in &>(_address).sin_port != 0) ||
            (_address.ss_family == AF_INET6 &&
             reinterpret_cast<const sockaddr_in6 &>(_address).sin6_port != 0))
            reuseAddr = true;
        if (SetSoReuseAddr(reuseAddr))
        {
            size_t socketAddrLen;
            switch (_address.ss_family)
            {
            case AF_INET:
                socketAddrLen = sizeof(sockaddr_in);
                break;
            case AF_INET6:
                socketAddrLen = sizeof(sockaddr_in6);
                {
                    int disable = 0;
                    ::setsockopt(_socketHandle, IPPROTO_IPV6, IPV6_V6ONLY, &disable, sizeof(disable));
                }
                break;
            default:
                socketAddrLen = sizeof(sockaddr_storage);
            }
            if(bind(_socketHandle, reinterpret_cast<struct sockaddr *>(&_address),
                    socketAddrLen) >= 0)
            {
                if(listen(_socketHandle, _backLog) >= 0)
                {
                    rc = true;
                }
            }
        }
    }

    return rc;
}
