// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serversocket.h"
#include <netinet/in.h>
#include <cstring>


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

    return (_socketFactory != nullptr)
           ? _socketFactory->CreateSocket()
           : (FastOS_SocketInterface *)new FastOS_Socket();
}


FastOS_SocketInterface *FastOS_ServerSocket::Accept()
{
    struct sockaddr_storage  clientAddress;

    socklen_t clientAddressLength = sizeof(clientAddress);

    memset(&clientAddress, 0, sizeof(clientAddress));

    int handlerSocketHandle = accept(_socketHandle, reinterpret_cast<struct sockaddr *>(&clientAddress),
                                     &clientAddressLength);

    if (handlerSocketHandle >= 0) {
        FastOS_SocketInterface *handlerSocket = CreateHandlerSocket();

        if (handlerSocket != nullptr) {
            handlerSocket->SetUp(handlerSocketHandle, reinterpret_cast<struct sockaddr *>(&clientAddress));
        }
        return handlerSocket;
    }

    return nullptr;
}

FastOS_Socket *FastOS_ServerSocket::AcceptPlain()
{
    struct sockaddr_storage clientAddress;

    socklen_t clientAddressLength = sizeof(clientAddress);

    memset(&clientAddress, 0, sizeof(clientAddress));

    int handlerSocketHandle = accept(_socketHandle, reinterpret_cast<struct sockaddr *>(&clientAddress),
                                     &clientAddressLength);

    if (handlerSocketHandle >= 0) {
        FastOS_Socket *handlerSocket = new FastOS_Socket();

        if (handlerSocket != nullptr) {
            handlerSocket->SetUp(handlerSocketHandle, reinterpret_cast<struct sockaddr *>(&clientAddress));
        }
        return handlerSocket;
    }

    return nullptr;
}

FastOS_ServerSocket::FastOS_ServerSocket(int socketHandle, FastOS_SocketFactory *socketFactory)
    : _portNumber(-1),
      _backLog(-1),
      _socketFactory(socketFactory),
      _validAddress(false)
{
    _socketHandle = socketHandle;
    memset(&_address, 0, sizeof(_address));
    _validAddress = true;
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
        if (SetSoReuseAddr(reuseAddr)) {
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
            if(bind(_socketHandle, reinterpret_cast<struct sockaddr *>(&_address), socketAddrLen) >= 0) {
                if(listen(_socketHandle, _backLog) >= 0) {
                    rc = true;
                }
            }
        }
    }

    return rc;
}
