// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "reactor.h"
#include "http_request.h"
#include "handle_manager.h"
#include <vespa/vespalib/net/crypto_socket.h>
#include <vespa/vespalib/data/smart_buffer.h>
#include <functional>
#include <atomic>

namespace vespalib::portal {

class HttpConnection : public Reactor::EventHandler
{
public:
    enum class State { HANDSHAKE, READ_REQUEST, DISPATCH, WAIT, WRITE_REPLY, CLOSE, NOTIFY, END };
private:
    using handler_fun_t = std::function<void(HttpConnection*)>;
    using AuthCtxPtr    = std::unique_ptr<net::ConnectionAuthContext>;

    HandleGuard        _guard;
    State              _state;
    CryptoSocket::UP   _socket;
    AuthCtxPtr         _auth_ctx;
    SmartBuffer        _input;
    SmartBuffer        _output;
    HttpRequest        _request;
    handler_fun_t      _handler;
    std::atomic<bool>  _reply_ready;
    Reactor::Token::UP _token;

    void set_state(State state, bool read, bool write);

    void complete_handshake();
    void do_handshake();
    void do_read_request();
    void do_dispatch();
    void do_wait();
    void do_write_reply();
    void do_close();
    void do_notify();

public:
    using UP = std::unique_ptr<HttpConnection>;
    HttpConnection(HandleGuard guard, Reactor &reactor, CryptoSocket::UP socket, handler_fun_t handler);
    ~HttpConnection();
    void handle_event(bool read, bool write) override;
    State get_state() const { return _state; }
    void resolve_host(const vespalib::string &my_host) { _request.resolve_host(my_host); }
    const HttpRequest &get_request() const { return _request; }
    // Precondition: handshake must have been completed
    const net::ConnectionAuthContext &auth_context() const noexcept { return *_auth_ctx; }

    void respond_with_content(vespalib::stringref content_type,
                              vespalib::stringref content);
    void respond_with_error(int code, const vespalib::stringref msg);
};

} // namespace vespalib::portal
