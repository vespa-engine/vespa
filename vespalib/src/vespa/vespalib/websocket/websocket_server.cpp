// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "websocket_server.h"
#include "connection.h"
#include "request.h"
#include "key.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/host_name.h>

namespace vespalib::ws {

namespace {

vespalib::string magic = "[SELF]";

void respond_static(Connection &conn, const WebsocketServer::StaticPage &page, const vespalib::string &self) {
    conn.printf("HTTP/1.1 200 OK\r\n");
    conn.printf("Connection: close\r\n");
    conn.printf("Content-Type: %s\r\n", page.content_type.c_str());
    conn.printf("\r\n");
    size_t pos = 0;
    while (pos < page.content.size()) {
        size_t next = page.content.find(magic, pos);
        if (next == vespalib::string::npos) {
            conn.write(page.content.data() + pos, page.content.size() - pos);
            pos = page.content.size();
        } else {
            conn.write(page.content.data() + pos, next - pos);
            conn.write(self.data(), self.size());
            pos = next + magic.size();
        }
    }
    conn.flush();
}

void respond_error(Connection &conn, int code, const vespalib::string &message) {
    conn.printf("HTTP/1.1 %d %s\r\n", code, message.c_str());
    conn.printf("Connection: close\r\n");
    conn.printf("Content-Type: text/html\r\n");
    conn.printf("\r\n");
    conn.printf("<html><body><h2>%d %s</h2></body></html>",
                code, message.c_str());
    conn.flush();
}

void respond_upgrade(Connection &conn, const vespalib::string &accept_token) {
    conn.printf("HTTP/1.1 101 Switching Protocols\r\n");
    conn.printf("Upgrade: websocket\r\n");
    conn.printf("Connection: Upgrade\r\n");
    conn.printf("Sec-WebSocket-Accept: %s\r\n", accept_token.c_str());
    conn.printf("\r\n");
    conn.flush();
}

void respond_upgrade_failed(Connection &conn) {
    conn.printf("HTTP/1.1 400 Upgrade Failed\r\n");
    conn.printf("Connection: close\r\n");
    conn.printf("Sec-WebSocket-Version: 13\r\n");
    conn.printf("\r\n");
    conn.flush();
}

const char *name_from_type(Frame::Type type) {
    switch (type) {
    case Frame::Type::NONE:  return "NONE";
    case Frame::Type::TEXT:  return "TEXT";
    case Frame::Type::DATA:  return "DATA";
    case Frame::Type::PING:  return "PING";
    case Frame::Type::PONG:  return "PONG";
    case Frame::Type::CLOSE: return "CLOSE";
    default:                 return "INVALID";
    }
}

void handle_echo(Connection &conn) {
    fprintf(stderr, "server: got ws connection\n");
    Frame frame;
    bool done = false;
    while (!done && conn.read_frame(frame)) {
        fprintf(stderr, "server: got frame of type %s with size %zu\n",
                name_from_type(frame.type), frame.payload.used());
        if (frame.type == Frame::Type::TEXT) {
            fprintf(stderr, "server: got text: %s\n",
                    vespalib::string(frame.payload.obtain(),
                                     frame.payload.used()).c_str());
        }
        if (frame.type == Frame::Type::INVALID) {
            break;
        }
        if (frame.type == Frame::Type::PONG) {
            continue;
        }
        if (frame.type == Frame::Type::PING) {
            frame.type = Frame::Type::PONG;
        }
        if (frame.type == Frame::Type::CLOSE) {
            done = true;
        }
        conn.write_frame(frame);
        conn.flush();
    }
    fprintf(stderr, "server: closing ws connection\n");
}

void handle_upgrade(Connection &conn, Request &req) {
    const vespalib::string version = req.get_header("sec-websocket-version");
    vespalib::string accept_token = Key::accept(req.get_header("sec-websocket-key"));
    if (version == "13") {
        respond_upgrade(conn, accept_token);
        handle_echo(conn);
    } else {
        respond_upgrade_failed(conn);
    }
}

} // namespace vespalib::ws::<unnamed>

WebsocketServer::StaticPage::StaticPage(const vespalib::string & type, const vespalib::string & content_in)
    : content_type(type),
      content(content_in)
{}
WebsocketServer::StaticPage::StaticPage(const StaticPage &) = default;
WebsocketServer::StaticPage & WebsocketServer::StaticPage::operator = (const StaticPage &) = default;
WebsocketServer::StaticPage::~StaticPage() = default;

WebsocketServer::WebsocketServer(int port_in, StaticRepo &&repo)
    : _acceptor(port_in, *this),
      _static_repo(std::move(repo)),
      _self(make_string("%s:%d", HostName::get().c_str(), _acceptor.port()))
{
}

WebsocketServer::~WebsocketServer() = default;

void
WebsocketServer::handle(std::unique_ptr<Socket> socket)
{
    Connection conn(std::move(socket));
    Request req;
    if (!req.read_header(conn)) {
        respond_error(conn, 400, "Bad Request");
        return;
    }
    if (!req.is_get()) {
        respond_error(conn, 501, "Not Implemented");
        return;        
    }
    if (req.is_ws_upgrade()) {
        if (req.uri() == "/echo") {
            handle_upgrade(conn, req);
        } else {
            respond_error(conn, 404, "Not Found");
        }
    } else {
        auto page = _static_repo.find(req.uri());
        if (page != _static_repo.end()) {
            respond_static(conn, page->second, _self);
        } else {
            respond_error(conn, 404, "Not Found");
        }
    }
}

}
