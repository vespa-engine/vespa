// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "http_connection.h"
#include <vespa/vespalib/data/output_writer.h>
#include <vespa/vespalib/net/connection_auth_context.h>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>

namespace vespalib::portal {

namespace {

constexpr size_t CHUNK_SIZE = 4_Ki;

enum class ReadRes { OK, END, FAIL };
enum class WriteRes { OK, BLOCKED, FAIL };

bool is_blocked(int res) {
    return ((res == -1) && ((errno == EWOULDBLOCK) || (errno == EAGAIN)));
}

ReadRes drain(CryptoSocket &socket, SmartBuffer &buffer) {
    size_t chunk_size = std::max(CHUNK_SIZE, socket.min_read_buffer_size());
    for (;;) {
        auto chunk = buffer.reserve(chunk_size);
        auto res = socket.drain(chunk.data, chunk.size);
        if (res > 0) {
            buffer.commit(res);
        } else if (res == 0) {
            return ReadRes::OK;
        } else {
            return ReadRes::FAIL;
        }
    }
}

ReadRes read(CryptoSocket &socket, SmartBuffer &buffer) {
    size_t chunk_size = std::max(CHUNK_SIZE, socket.min_read_buffer_size());
    auto chunk = buffer.reserve(chunk_size);
    auto res = socket.read(chunk.data, chunk.size);
    if (res > 0) {
        buffer.commit(res);
    } else if (res == 0) {
        return ReadRes::END;
    } else if (is_blocked(res)) {
        return ReadRes::OK;
    } else {
        return ReadRes::FAIL;
    }
    return drain(socket, buffer);
}

WriteRes flush(CryptoSocket &socket) {
    for (;;) {
        auto res = socket.flush();
        if (res > 0) {
            // flush more
        } else if (res == 0) {
            return WriteRes::OK;
        } else if (is_blocked(res)) {
            return WriteRes::BLOCKED;
        } else {
            return WriteRes::FAIL;
        }
    }
}

WriteRes write(CryptoSocket &socket, SmartBuffer &buffer) {
    auto chunk = buffer.obtain();
    if (chunk.size > 0) {
        auto res = socket.write(chunk.data, chunk.size);
        if (res > 0) {
            buffer.evict(res);
        } else if (is_blocked(res)) {
            return WriteRes::BLOCKED;
        } else {
            assert(res < 0);
            return WriteRes::FAIL;
        }
    }
    return flush(socket);
}

WriteRes half_close(CryptoSocket &socket) {
    auto res = socket.half_close();
    if (res == 0) {
        return WriteRes::OK;
    } else if (is_blocked(res)) {
        return WriteRes::BLOCKED;
    } else {
        return WriteRes::FAIL;
    }
}

/**
 * Emit a basic set of HTTP security headers meant to minimize any impact
 * in the case of unsanitized/unescaped data making its way to an internal
 * status page.
 */
void emit_http_security_headers(OutputWriter &dst) {
    // Reject detected cross-site scripting attacks
    dst.printf("X-XSS-Protection: 1; mode=block\r\n");
    // Do not allow embedding via iframe (clickjacking prevention)
    dst.printf("X-Frame-Options: DENY\r\n");
    // Do not allow _anything_ to be externally loaded, nor inline scripts
    // etc. to be executed.
    // "frame-ancestors: none" is analogous to X-Frame-Options: DENY.
    dst.printf("Content-Security-Policy: default-src 'none'; frame-ancestors 'none'\r\n");
    // No heuristic auto-inference of content-type based on payload.
    dst.printf("X-Content-Type-Options: nosniff\r\n");
    // Don't store any potentially sensitive data in any caches.
    dst.printf("Cache-Control: no-store\r\n");
    dst.printf("Pragma: no-cache\r\n");
}

} // namespace vespalib::portal::<unnamed>

void
HttpConnection::set_state(State state, bool read, bool write)
{
    _token->update(read, write);
    _state = state;
}

void
HttpConnection::complete_handshake()
{
    _auth_ctx = _socket->make_auth_context();
    set_state(State::READ_REQUEST, true, false);
}

void
HttpConnection::do_handshake()
{
    for (;;) {
        switch (_socket->handshake()) {
        case vespalib::CryptoSocket::HandshakeResult::FAIL:       return set_state(State::NOTIFY,    false, false);
        case vespalib::CryptoSocket::HandshakeResult::DONE:       return complete_handshake();
        case vespalib::CryptoSocket::HandshakeResult::NEED_READ:  return set_state(State::HANDSHAKE,  true, false);
        case vespalib::CryptoSocket::HandshakeResult::NEED_WRITE: return set_state(State::HANDSHAKE, false,  true);
        case vespalib::CryptoSocket::HandshakeResult::NEED_WORK:  _socket->do_handshake_work();
        }
    }
}

void
HttpConnection::do_read_request()
{
    if (read(*_socket, _input) != ReadRes::OK) {
        return set_state(State::NOTIFY, false, false);
    }
    auto data = _input.obtain();
    auto consumed = _request.handle_data(data.data, data.size);
    _input.evict(consumed);
    if (!_request.need_more_data()) {
        set_state(State::DISPATCH, false, false);
    }
}

void
HttpConnection::do_dispatch()
{
    set_state(State::WAIT, false, false);
    return _handler(this); // callback is final touch
}

void
HttpConnection::do_wait()
{
    if (_reply_ready.load(std::memory_order_acquire)) {
        set_state(State::WRITE_REPLY, false, true);
    }
}

void
HttpConnection::do_write_reply()
{
    if (write(*_socket, _output) == WriteRes::FAIL) {
        return set_state(State::NOTIFY, false, false);
    }
    if (_output.obtain().size == 0) {
        set_state(State::CLOSE, false, true);
    }
}

void
HttpConnection::do_close()
{
    if (half_close(*_socket) != WriteRes::BLOCKED) {
        set_state(State::NOTIFY, false, false);
    }
}

void
HttpConnection::do_notify()
{
    set_state(State::END, false, false);
    return _handler(this); // callback is final touch
}

HttpConnection::HttpConnection(HandleGuard guard, Reactor &reactor, CryptoSocket::UP socket, handler_fun_t handler)
    : _guard(std::move(guard)),
      _state(State::HANDSHAKE),
      _socket(std::move(socket)),
      _auth_ctx(),
      _input(CHUNK_SIZE * 2),
      _output(CHUNK_SIZE * 2),
      _request(),
      _handler(std::move(handler)),
      _reply_ready(false),
      _token()
{
    _token = reactor.attach(*this, _socket->get_fd(), true, true);
}

HttpConnection::~HttpConnection()
{
    _token.reset();
}

void
HttpConnection::handle_event(bool, bool)
{
    if (_state == State::HANDSHAKE) {
        do_handshake();
    }
    if (_state == State::READ_REQUEST) {
        do_read_request();
    }
    if (_state == State::DISPATCH) {
        return do_dispatch(); // callback is final touch
    }
    if (_state == State::WAIT) {
        do_wait();
    }
    if (_state == State::WRITE_REPLY) { 
        do_write_reply();
    }
    if (_state == State::CLOSE) {
        do_close();
    }
    if (_state == State::NOTIFY) {
        return do_notify(); // callback is final touch
    }
}

void
HttpConnection::respond_with_content(const vespalib::string &content_type,
                                     const vespalib::string &content)
{
    {
        OutputWriter dst(_output, CHUNK_SIZE);
        dst.printf("HTTP/1.1 200 OK\r\n");
        dst.printf("Connection: close\r\n");
        dst.printf("Content-Type: %s\r\n", content_type.c_str());
        dst.printf("Content-Length: %zu\r\n", content.size());
        emit_http_security_headers(dst);
        dst.printf("\r\n");
        dst.write(content.data(), content.size());
    }
    _token->update(false, true);
    _reply_ready.store(true, std::memory_order_release);
}

void
HttpConnection::respond_with_error(int code, const vespalib::string &msg)
{
    {
        OutputWriter dst(_output, CHUNK_SIZE);
        dst.printf("HTTP/1.1 %d %s\r\n", code, msg.c_str());
        dst.printf("Connection: close\r\n");
        dst.printf("\r\n");
    }
    _token->update(false, true);
    _reply_ready.store(true, std::memory_order_release);
}

} // namespace vespalib::portal
