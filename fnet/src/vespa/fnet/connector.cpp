// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fnet");
#include <vespa/fnet/fnet.h>


FNET_Connector::FNET_Connector(FNET_TransportThread *owner,
                               FNET_IPacketStreamer *streamer,
                               FNET_IServerAdapter *serverAdapter,
                               const char *spec,
                               int port, int backlog,
                               FastOS_SocketFactory *factory,
                               const char *strictBindHostName)
    : FNET_IOComponent(owner, NULL, spec, /* time-out = */ false),
      _streamer(streamer),
      _serverAdapter(serverAdapter),
      _serverSocket(NULL),
      _strict(strictBindHostName != NULL)
{
    _serverSocket  = new FastOS_ServerSocket(port, backlog, factory,
                                             strictBindHostName);
    assert(_serverSocket != NULL);
    _ioc_socket = _serverSocket; // set socket 'manually'
}


FNET_Connector::~FNET_Connector()
{
    assert(_serverSocket->GetSocketEvent() == NULL);
    delete _serverSocket;
}


bool
FNET_Connector::Init()
{
    bool rc = true;

    // check if strict binding went OK.
    if (_strict) {
        rc = _serverSocket->GetValidAddressFlag();
    }

    // configure socket for non-blocked listening
    rc = (rc && (_serverSocket->SetSoBlocking(false))
          && (_serverSocket->Listen()));

    // print some debug output [XXX: remove this later]
    if(rc) {
        LOG(debug, "Connector(%s): TCP listen OK",
            GetSpec());
        EnableReadEvent(true);
    } else {
        LOG(warning, "Connector(%s): TCP listen FAILED",
            GetSpec());
    }
    return rc;
}


void
FNET_Connector::Close()
{
    SetSocketEvent(NULL);
    _serverSocket->Close();
}


bool
FNET_Connector::HandleReadEvent()
{
    FastOS_Socket          *newSocket = NULL;
    FNET_Connection        *conn      = NULL;

    newSocket = _serverSocket->AcceptPlain();
    if (newSocket != NULL) {
        FNET_Transport &transport = Owner()->owner();
        FNET_TransportThread *thread = transport.select_thread(newSocket, sizeof(FastOS_Socket));
        conn = new FNET_Connection(thread, _streamer, _serverAdapter, newSocket, GetSpec());
        if (newSocket->SetSoBlocking(false) && conn->Init()) {
            conn->Owner()->Add(conn, false);
        } else {
            LOG(debug, "Connector(%s): failed to init incoming connection",
                GetSpec());
            delete conn; // this is legal.
        }
    }
    return true;
}


bool
FNET_Connector::HandleWriteEvent()
{
    LOG(debug, "Connector(%s): got write event, ignoring",
        GetSpec());
    EnableWriteEvent(false);
    return true;
}
