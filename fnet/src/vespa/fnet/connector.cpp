// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "connector.h"

#include "connection.h"
#include "transport.h"
#include "transport_thread.h"

#include <vespa/log/log.h>
LOG_SETUP(".fnet");

using vespalib::SocketHandle;

FNET_Connector::FNET_Connector(
    FNET_TransportThread* owner, FNET_IPacketStreamer* streamer, FNET_IServerAdapter* serverAdapter, const char* spec,
    vespalib::ServerSocket server_socket)
    : FNET_IOComponent(owner, server_socket.get_fd(), spec, /* time-out = */ false),
      _streamer(streamer),
      _serverAdapter(serverAdapter),
      _server_socket(std::move(server_socket)),
      _cached_port(_server_socket.address().port()) {}

uint32_t FNET_Connector::GetPortNumber() const { return _cached_port; }

FNET_IServerAdapter* FNET_Connector::server_adapter() { return _serverAdapter; }

void FNET_Connector::Close() {
    detach_selector();
    _ioc_socket_fd = -1;
    _server_socket = vespalib::ServerSocket();
}

bool FNET_Connector::HandleReadEvent() {
    SocketHandle handle = _server_socket.accept();
    if (handle.valid()) {
        FNET_Transport&       transport = Owner()->owner();
        FNET_TransportThread* thread = transport.select_thread(&handle, sizeof(handle));
        if (thread->tune(handle)) {
            std::unique_ptr<FNET_Connection> conn =
                std::make_unique<FNET_Connection>(thread, _streamer, _serverAdapter, std::move(handle), GetSpec());
            if (conn->Init()) {
                thread->Add(conn.release(), /*needRef = */ false);
            } else {
                LOG(debug, "Connector(%s): failed to init incoming connection", GetSpec());
            }
        }
    }
    return true;
}

bool FNET_Connector::HandleWriteEvent() {
    LOG(debug, "Connector(%s): got write event, ignoring", GetSpec());
    EnableWriteEvent(false);
    return true;
}
