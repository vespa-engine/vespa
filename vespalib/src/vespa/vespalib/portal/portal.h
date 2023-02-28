// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "listener.h"
#include "reactor.h"
#include "handle_manager.h"

#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/crypto_socket.h>
#include <vespa/vespalib/stllike/string.h>

#include <map>
#include <memory>
#include <mutex>
#include <thread>

namespace vespalib {

namespace portal { class HttpConnection; }
namespace net { class ConnectionAuthContext; }

/**
 * Minimal HTTP server and connection establishment manager.
 **/
class Portal
{
public:
    using SP = std::shared_ptr<Portal>;

    class Token {
        friend class Portal;
    private:
        Portal &_portal;
        uint64_t _handle;
        Token(const Token &) = delete;
        Token &operator=(const Token &) = delete;
        Token(Token &&) = delete;
        Token &operator=(Token &&) = delete;
        Token(Portal &portal, uint64_t handle)
            : _portal(portal), _handle(handle) {}
        uint64_t handle() const { return _handle; }
    public:
        using UP = std::unique_ptr<Token>;
        ~Token();
    };

    class GetRequest {
        friend class Portal;
    private:
        portal::HttpConnection *_conn;
        GetRequest(portal::HttpConnection &conn) : _conn(&conn) {}
    public:
        GetRequest(const GetRequest &rhs) = delete;
        GetRequest &operator=(const GetRequest &rhs) = delete;
        GetRequest &operator=(GetRequest &&rhs) = delete;
        GetRequest(GetRequest &&rhs) noexcept : _conn(rhs._conn) {
            rhs._conn = nullptr;
        }
        bool active() const { return (_conn != nullptr); }
        const vespalib::string &get_header(const vespalib::string &name) const;
        const vespalib::string &get_host() const;
        const vespalib::string &get_uri() const;
        const vespalib::string &get_path() const;
        bool has_param(const vespalib::string &name) const;
        const vespalib::string &get_param(const vespalib::string &name) const;
        std::map<vespalib::string, vespalib::string> export_params() const;
        void respond_with_content(vespalib::stringref content_type,
                                  vespalib::stringref content);
        void respond_with_error(int code, vespalib::stringref msg);
        const net::ConnectionAuthContext &auth_context() const noexcept;
        ~GetRequest();
    };

    struct GetHandler {
        virtual void get(GetRequest request) = 0;
        virtual ~GetHandler();
    };

private:
    struct BindState {
        uint64_t handle;
        vespalib::string prefix;
        GetHandler *handler;
        BindState(uint64_t handle_in, vespalib::string prefix_in, GetHandler &handler_in) noexcept
            : handle(handle_in), prefix(prefix_in), handler(&handler_in) {}
        bool operator<(const BindState &rhs) const {
            if (prefix.size() == rhs.prefix.size()) {
                return (handle > rhs.handle);
            }
            return (prefix.size() > rhs.prefix.size());
        }
    };

    CryptoEngine::SP       _crypto;
    portal::Reactor        _reactor;
    portal::HandleManager  _handle_manager;
    uint64_t               _conn_handle;
    portal::Listener::UP   _listener;
    std::mutex             _lock;
    std::vector<BindState> _bind_list;
    vespalib::string       _my_host;

    Token::UP make_token();
    void cancel_token(Token &token);

    portal::HandleGuard lookup_get_handler(const vespalib::string &uri, GetHandler *&handler);
    void evict_handle(uint64_t handle);

    void handle_accept(portal::HandleGuard guard, SocketHandle socket);
    void handle_http(portal::HttpConnection *conn);

    Portal(CryptoEngine::SP crypto, int port);
public:
    ~Portal();
    static SP create(CryptoEngine::SP crypto, int port);
    int listen_port() const { return _listener->listen_port(); }
    const vespalib::string &my_host() const { return _my_host; }
    Token::UP bind(const vespalib::string &path_prefix, GetHandler &handler);
};

} // namespace vespalib
