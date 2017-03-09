// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "http_server.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/host_name.h>
#include <algorithm>

namespace vespalib {

namespace {

void respond_not_found(Fast_HTTPConnection &conn) {
    conn.Output(conn.GetHTTPVersion().c_str());
    conn.Output(" 404 Not Found\r\n");
    conn.Output("Connection: close\r\n\r\n");
}

void write_json_header(Fast_HTTPConnection &conn) {
    conn.Output(conn.GetHTTPVersion().c_str());
    conn.Output(" 200 OK\r\n");
    conn.Output("Connection: close\r\n");
    conn.Output("Content-Type: application/json\r\n\r\n");
}

} // namespace vespalib::<unnamed>

void
HttpServer::handle_get(const string &url, const string &host_in,
                       Fast_HTTPConnection &conn) const
{
    std::map<vespalib::string,vespalib::string> params;
    vespalib::string json_result = _handler_repo.get(host_in.empty() ? _my_host : host_in, url, params);
    if (json_result.empty()) {
        respond_not_found(conn);
    } else {
        write_json_header(conn);
        conn.OutputData(json_result.data(), json_result.size());
    }
}

//-----------------------------------------------------------------------------

HttpServer::HttpServer(int port_in)
    : _requested_port(port_in),
      _started(false),
      _actual_port(0),
      _my_host(),
      _handler_repo(),
      _server(new Server(port_in, *this))
{
    _server->SetKeepAlive(false);
}

HttpServer::~HttpServer() { }

void
HttpServer::start()
{
    if (_started) {
        return;
    }
    int ret_code = _server->Start();
    if (ret_code != FASTLIB_SUCCESS &&
        ret_code != FASTLIB_HTTPSERVER_ALREADYSTARTED)
    {
        if (ret_code == FASTLIB_HTTPSERVER_BADLISTEN) {
            throw PortListenException(_requested_port, "HTTP");
        } else {
            throw FatalException("failed to start vespalib HTTP server");
        }
    }
    _actual_port = _server->getListenPort();
    _my_host = make_string("%s:%d", HostName::get().c_str(), _actual_port);
    _started = true;
}

void
HttpServer::stop()
{
    _server.reset(nullptr);
}

} // namespace vespalib
