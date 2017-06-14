// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/socket.h>
#include <sstream>

FastOS_SocketInterface::FastOS_SocketInterface()
    : _readEventEnabled(false),
      _writeEventEnabled(false),
      _readPossible(false),
      _writePossible(false),
      _epolled(false),
      _socketEvent(NULL),
      _eventAttribute(NULL),
      _socketEventArrayPos(-1),
      _address(),
      _socketHandle(-1),
      _preferIPv6(false)
{
    ConstructorWork();
}

FastOS_SocketInterface::FastOS_SocketInterface(int socketHandle, struct sockaddr *hostAddress)
    : _readEventEnabled(false),
      _writeEventEnabled(false),
      _readPossible(false),
      _writePossible(false),
      _epolled(false),
      _socketEvent(NULL),
      _eventAttribute(NULL),
      _socketEventArrayPos(-1),
      _address(),
      _socketHandle(-1),
      _preferIPv6(false)
{
    ConstructorWork();
    SetUp(socketHandle, hostAddress);
}

FastOS_SocketInterface::~FastOS_SocketInterface() { }

bool FastOS_SocketInterface::Connect()
{
    bool rc=false;

    if (CreateIfNoSocketYet()) {
        switch (_address.ss_family) {
        case AF_INET:
            rc = (0 == connect(_socketHandle,
                               reinterpret_cast<struct sockaddr *>(&_address),
                               sizeof(sockaddr_in)));
            break;
        case AF_INET6:
            rc = (0 == connect(_socketHandle,
                               reinterpret_cast<struct sockaddr *>(&_address),
                               sizeof(sockaddr_in6)));
            break;
        default:
            rc = false;
        }
    }

    return rc;
}

bool FastOS_SocketInterface::SetAddress (const int portNum, const char *address)
{
    bool rc = false;
    memset(&_address, 0, sizeof(_address));

    addrinfo hints;
    memset(&hints, 0, sizeof(addrinfo));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = 0;
    hints.ai_flags = (AI_PASSIVE | AI_NUMERICSERV | AI_ADDRCONFIG);
    char service[32];
    snprintf(service, sizeof(service), "%d", portNum);
    addrinfo *list = nullptr;
    if (getaddrinfo(address, service, &hints, &list) == 0) {
        const addrinfo *best = nullptr;
        for (const addrinfo *info = list; info != nullptr; info = info->ai_next) {
            if (best == nullptr) {
                best = info;
            } else if (_preferIPv6) {
                if ((best->ai_family != AF_INET6) && (info->ai_family == AF_INET6)) {
                    best = info;
                }
            } else {
                if ((best->ai_family != AF_INET) && (info->ai_family == AF_INET)) {
                    best = info;
                }
            }
        }
        if (best != nullptr) {
            memcpy(&_address, best->ai_addr, best->ai_addrlen);
            rc = true;
        }
        freeaddrinfo(list);
    }
    return rc;
}

bool FastOS_SocketInterface::SetAddressByHostName (const int portNum, const char *hostName)
{
    return SetAddress(portNum, hostName);
}

void FastOS_SocketInterface::SetUp(int socketHandle, struct sockaddr *hostAddress)
{
    Close();

    _socketHandle = socketHandle;
    switch (hostAddress->sa_family) {
    case AF_INET:
        memcpy(&_address, hostAddress, sizeof(sockaddr_in));
        break;
    case AF_INET6:
        memcpy(&_address, hostAddress, sizeof(sockaddr_in6));
        break;
    default:
        ;
    }
}

bool FastOS_SocketInterface::CreateIfNoSocketYet ()
{
    if (ValidHandle()) {
        return true;
    } else if (_address.ss_family == AF_INET) {
        _socketHandle = socket(AF_INET, SOCK_STREAM, 0);
        return (_socketHandle != -1);
    } else if (_address.ss_family == AF_INET6) {
        _socketHandle = socket(AF_INET6, SOCK_STREAM, 0);
        return (_socketHandle != -1);
    }
    return false;
}

void FastOS_SocketInterface::ConstructorWork ()
{
    _socketHandle = -1;
    _epolled = false;
    _socketEvent = NULL;
    _readEventEnabled = false;
    _writeEventEnabled = false;
    _eventAttribute = NULL;
    _socketEventArrayPos = -1;
}

bool FastOS_SocketInterface::SetSoLinger( bool doLinger, int seconds )
{
    bool rc=false;

    struct linger lingerTime;
    lingerTime.l_onoff = doLinger ? 1 : 0;
    lingerTime.l_linger = seconds;

    if (CreateIfNoSocketYet()) {
        rc = (0 == setsockopt(_socketHandle, SOL_SOCKET, SO_LINGER, &lingerTime, sizeof(lingerTime)));
    }

    return rc;
}

bool
FastOS_SocketInterface::SetNoDelay(bool noDelay)
{
    bool rc = false;
    int noDelayInt = noDelay ? 1 : 0;

    if (CreateIfNoSocketYet()) {
        rc = (setsockopt(_socketHandle, IPPROTO_TCP, TCP_NODELAY, &noDelayInt, sizeof(noDelayInt)) == 0);
    }
    return rc;
}

int FastOS_SocketInterface::GetSoError ()
{
    if (!ValidHandle()) { return EINVAL; }

    // Fetch this first, as getsockopt(..SO_ERROR, resets the
    // WSAGetLastError()
    int lastError = FastOS_Socket::GetLastError();
    int  soError = 0;

    socklen_t soErrorLen = sizeof(soError);

    if (getsockopt(_socketHandle, SOL_SOCKET, SO_ERROR, &soError, &soErrorLen) != 0) {
        return lastError;
    }

    if (soErrorLen != sizeof(soError)) {
        return EINVAL;          // 'invalid argument'
    }

    return soError;
}

bool FastOS_SocketInterface::SetSoIntOpt (int option, int value)
{
    bool rc=false;

    if (CreateIfNoSocketYet()) {
        rc = (0 == setsockopt(_socketHandle, SOL_SOCKET, option, &value, sizeof(value)));
    }

    return rc;
}

bool FastOS_SocketInterface::GetSoIntOpt(int option, int &value)
{
    bool rc=false;

    if (CreateIfNoSocketYet()) {
        socklen_t len = sizeof(value);

        int retval = getsockopt(_socketHandle, SOL_SOCKET, option, &value, &len);

        if (len != sizeof(value)) {
            // FIX! - What about GetLastError() in this case?
            return false;
        }

        rc = (0 == retval);
    }

    return rc;
}

void FastOS_SocketInterface::CleanupEvents ()
{
    if (_socketEvent != NULL) {
        _socketEvent->Detach(this);
        assert(!_epolled);
        _socketEvent = NULL;
    }
}

bool FastOS_SocketInterface::TuneTransport ()
{
    if (!SetSoIntOpt(SO_KEEPALIVE, 1)) { return false; }
    if (!SetSoLinger(true, 0)) { return false; }
    return true;
}

int FastOS_SocketInterface::GetLocalPort ()
{
    int result = -1;
    sockaddr_storage addr;
    socklen_t len = sizeof(addr);
    if(getsockname(_socketHandle, reinterpret_cast<struct sockaddr *>(&addr), &len) == 0) {
        if ((addr.ss_family == AF_INET) && (len == sizeof(sockaddr_in))) {
            const sockaddr_in *my_addr = reinterpret_cast<const sockaddr_in *>(&addr);
            result = ntohs(my_addr->sin_port);
        }
        if ((addr.ss_family == AF_INET6) && (len == sizeof(sockaddr_in6))) {
            const sockaddr_in6 *my_addr = reinterpret_cast<const sockaddr_in6 *>(&addr);
            result = ntohs(my_addr->sin6_port);
        }
    }
    return result;
}

std::string
FastOS_SocketInterface::getLastErrorString(void) {
    return FastOS_Socket::getErrorString(FastOS_Socket::GetLastError());
}

const char *
FastOS_SocketInterface::InitializeServices(void) {
    FastOS_SocketEventObjects::InitializeClass();
    return NULL;
}

void FastOS_SocketInterface::CleanupServices () {
    FastOS_SocketEventObjects::ClassCleanup();
}

bool FastOS_SocketInterface::SetSocketEvent (FastOS_SocketEvent *event, void *attribute) {
    bool rc=false;

    _eventAttribute = attribute;

    if (CreateIfNoSocketYet()) {
        if (_socketEvent != event) {
            if (_socketEvent != NULL) {
                // Disable events for this socket on the old SocketEvent
                _socketEvent->Detach(this);
                assert(!_epolled);
                _socketEvent = NULL;
            }

            if (event != NULL) {
                event->Attach(this, _readEventEnabled, _writeEventEnabled);
                _socketEvent = event;
            }
        }
        rc = true;
    }

    return rc;
}

void FastOS_SocketInterface::EnableReadEvent (bool enabled) {
    if (_readEventEnabled == enabled) { return; }
    _readEventEnabled = enabled;
    if (_socketEvent != NULL) {
        _socketEvent->EnableEvent(this, _readEventEnabled, _writeEventEnabled);
    }
}

/**
 * Enable or disable write events for the socket.
 * The behaviour caused by invoking this method while waiting for
 * socket events is undefined.
 * A @ref FastOS_SocketEvent must be associated with the socket prior
 * to calling @ref EnableReadEvent and @ref EnableWriteEvent.
 */
void FastOS_SocketInterface::EnableWriteEvent (bool enabled) {
    if (_writeEventEnabled == enabled) { return; }
    _writeEventEnabled = enabled;
    if (_socketEvent != NULL) {
        _socketEvent->EnableEvent(this, _readEventEnabled, _writeEventEnabled);
    }
}
