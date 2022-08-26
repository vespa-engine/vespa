// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "portal.h"
#include "http_connection.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/host_name.h>
#include <cassert>

namespace vespalib {

namespace {

template <typename T>
void remove_handle(T &collection, uint64_t handle) {
    collection.erase(std::remove_if(collection.begin(), collection.end(),
                                    [handle](const typename T::value_type &item)
                                    { return (item.handle == handle); }),
                     collection.end());
}

} // namespace vespalib::<unnamed>

using HttpConnection = portal::HttpConnection;

Portal::Token::~Token()
{
    _portal.cancel_token(*this);
}

const vespalib::string &
Portal::GetRequest::get_header(const vespalib::string &name) const
{
    assert(active());
    return _conn->get_request().get_header(name);
}

const vespalib::string &
Portal::GetRequest::get_host() const
{
    assert(active());
    return _conn->get_request().get_host();
}

const vespalib::string &
Portal::GetRequest::get_uri() const
{
    assert(active());
    return _conn->get_request().get_uri();
}

const vespalib::string &
Portal::GetRequest::get_path() const
{
    assert(active());
    return _conn->get_request().get_path();
}

bool
Portal::GetRequest::has_param(const vespalib::string &name) const
{
    assert(active());
    return _conn->get_request().has_param(name);
}

const vespalib::string &
Portal::GetRequest::get_param(const vespalib::string &name) const
{
    assert(active());
    return _conn->get_request().get_param(name);
}

std::map<vespalib::string, vespalib::string>
Portal::GetRequest::export_params() const
{
    assert(active());
    return _conn->get_request().export_params();
}

void
Portal::GetRequest::respond_with_content(const vespalib::string &content_type,
                                 const vespalib::string &content)
{
    assert(active());
    _conn->respond_with_content(content_type, content);
    _conn = nullptr;
}

void
Portal::GetRequest::respond_with_error(int code, const vespalib::string &msg)
{
    assert(active());
    _conn->respond_with_error(code, msg);
    _conn = nullptr;
}

const net::ConnectionAuthContext&
Portal::GetRequest::auth_context() const noexcept
{
    assert(active());
    return _conn->auth_context();
}

Portal::GetRequest::~GetRequest()
{
    if (active()) {
        respond_with_error(500, "Internal Server Error");
    }
}

Portal::GetHandler::~GetHandler() = default;

Portal::Token::UP
Portal::make_token()
{
    return Token::UP(new Token(*this, _handle_manager.create()));
}

void
Portal::cancel_token(Token &token)
{
    _handle_manager.destroy(token._handle);
    evict_handle(token._handle);
}

portal::HandleGuard
Portal::lookup_get_handler(const vespalib::string &uri, GetHandler *&handler)
{
    std::lock_guard guard(_lock);
    for (const auto &entry: _bind_list) {
        if (starts_with(uri, entry.prefix)) {
            auto handle_guard = _handle_manager.lock(entry.handle);
            if (handle_guard.valid()) {
                handler = entry.handler;
                return handle_guard;
            }
        }
    }
    return portal::HandleGuard();
}

void
Portal::evict_handle(uint64_t handle)
{
    std::lock_guard guard(_lock);
    remove_handle(_bind_list, handle);
}

void
Portal::handle_accept(portal::HandleGuard guard, SocketHandle socket)
{
    socket.set_blocking(false);
    socket.set_keepalive(true);
    new HttpConnection(std::move(guard), _reactor, _crypto->create_server_crypto_socket(std::move(socket)),
                       [this](HttpConnection *conn)
                       {
                           handle_http(conn);
                       });
}

void
Portal::handle_http(portal::HttpConnection *conn)
{
    if (conn->get_state() == HttpConnection::State::WAIT) {
        if (!conn->get_request().valid()) {
            conn->respond_with_error(400, "Bad Request");
        } else if (!conn->get_request().is_get()) {
            conn->respond_with_error(501, "Not Implemented");
        } else {
            GetHandler *get_handler = nullptr;
            auto guard = lookup_get_handler(conn->get_request().get_path(), get_handler);
            if (guard.valid()) {
                assert(get_handler != nullptr);
                conn->resolve_host(_my_host);
                get_handler->get(GetRequest(*conn));
            } else {
                conn->respond_with_error(404, "Not Found");
            }
        }
    } else {
        assert(conn->get_state() == HttpConnection::State::END);
        delete(conn);
    }
}

Portal::Portal(CryptoEngine::SP crypto, int port)
    : _crypto(std::move(crypto)),
      _reactor(),
      _handle_manager(),
      _conn_handle(_handle_manager.create()),
      _listener(),
      _lock(),
      _bind_list(),
      _my_host()
{
    _listener = std::make_unique<portal::Listener>(_reactor, port, 
                                                   [this](SocketHandle socket)
                                                   {
                                                       auto guard = _handle_manager.lock(_conn_handle);
                                                       if (guard.valid()) {
                                                           handle_accept(std::move(guard), std::move(socket));
                                                       }
                                                   });
    _my_host = vespalib::make_string("%s:%d", HostName::get().c_str(), listen_port());
}

Portal::~Portal()
{
    _listener.reset();
    _handle_manager.destroy(_conn_handle);
    assert(_handle_manager.empty());
    assert(_bind_list.empty());
}

Portal::SP
Portal::create(CryptoEngine::SP crypto, int port)
{
    return Portal::SP(new Portal(std::move(crypto), port));
}

Portal::Token::UP
Portal::bind(const vespalib::string &path_prefix, GetHandler &handler)
{
    auto token = make_token();    
    std::lock_guard guard(_lock);
    _bind_list.emplace_back(token->_handle, path_prefix, handler);
    std::sort(_bind_list.begin(), _bind_list.end());
    return token;
}

} // namespace vespalib
