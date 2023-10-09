// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/server_socket.h>
#include <vespa/vespalib/net/socket.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/host_name.h>
#include <thread>
#include <functional>
#include <chrono>

using namespace vespalib;

vespalib::string read_msg(SocketHandle &socket) {
    vespalib::string msg;
    for (;;) {
        char c;
        ssize_t ret = socket.read(&c, 1);
        if (ret != 1) {
            fprintf(stderr, "error during read message\n");
            return msg;
        }
        if (c == '\n') {
            return msg;
        }
        msg.append(c);
    }
}

void write_msg(SocketHandle &socket, const vespalib::string &msg) {
    for (size_t i = 0; i < msg.size(); ++i) {
        ssize_t ret = socket.write(&msg[i], 1);
        if (ret != 1) {
            fprintf(stderr, "error during write message\n");
            return;
        }
    }
}

void kill_func() {
    while (!SignalHandler::INT.check()) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    fprintf(stderr, "exiting...\n");    
    kill(getpid(), SIGTERM);
}

int main(int, char **) {
    ServerSocket server(0);
    if (!server.valid()) {
        fprintf(stderr, "listen failed, exiting\n");
        return 1;
    }
    fprintf(stderr, "running socket test server at host %s\n", HostName::get().c_str());
    auto list = SocketAddress::resolve(0);
    if (list.size() > 0) {
        fprintf(stderr, "all local addresses:\n");
        for (const auto &addr: list) {
            fprintf(stderr, "  %s\n", addr.spec().c_str());
        }
    }
    fprintf(stderr, "listening to %s\n", server.address().spec().c_str());
    fprintf(stderr, "client command: ./vespalib_socket_client_app %s %d\n",
            HostName::get().c_str(), server.address().port());
    fprintf(stderr, "use ^C (SIGINT) to exit\n");
    SignalHandler::INT.hook();
    std::thread kill_thread(kill_func);
    for (;;) {
        SocketHandle socket = server.accept();
        if (socket.valid()) {
            fprintf(stderr, "got connection from: %s (local address: %s)\n",
                    SocketAddress::peer_address(socket.get()).spec().c_str(),
                    SocketAddress::address_of(socket.get()).spec().c_str());
            fprintf(stderr, "message from client: '%s'\n", read_msg(socket).c_str());
            write_msg(socket, "hello from server\n");
        } else {
            fprintf(stderr, "(got invalid socket from accept)\n");
        }
    }
    return 0;
}
