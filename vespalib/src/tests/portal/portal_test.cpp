// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/portal/portal.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/sync_crypto_socket.h>
#include <vespa/vespalib/net/tls/tls_crypto_engine.h>
#include <vespa/vespalib/net/tls/maybe_tls_crypto_engine.h>
#include <vespa/vespalib/test/make_tls_options_for_testing.h>
#include <vespa/vespalib/util/latch.h>

using namespace vespalib;
using namespace vespalib::test;

//-----------------------------------------------------------------------------

vespalib::string do_http(int port, CryptoEngine::SP crypto, const vespalib::string &method, const vespalib::string &uri, bool send_host = true) {
    auto socket = SocketSpec::from_port(port).client_address().connect();
    ASSERT_TRUE(socket.valid());
    auto conn = SyncCryptoSocket::create_client(*crypto, std::move(socket), make_local_spec());
    vespalib::string http_req = vespalib::make_string("%s %s HTTP/1.1\r\n"
                                                      "My-Header: my value\r\n"
                                                      "%s"
                                                      "\r\n", method.c_str(), uri.c_str(), send_host ? "Host: HOST:42\r\n" : "");
    ASSERT_EQUAL(conn->write(http_req.data(), http_req.size()), ssize_t(http_req.size()));
    char buf[1024];
    vespalib::string result;
    ssize_t res = conn->read(buf, sizeof(buf));
    while (res > 0) {
        result.append(vespalib::stringref(buf, res));
        res = conn->read(buf, sizeof(buf));
    }
    ASSERT_EQUAL(res, 0);
    return result;
}

vespalib::string fetch(int port, CryptoEngine::SP crypto, const vespalib::string &path, bool send_host = true) {
    return do_http(port, std::move(crypto), "GET", path, send_host);
}

//-----------------------------------------------------------------------------

vespalib::string make_expected_response(const vespalib::string &content_type, const vespalib::string &content) {
    return vespalib::make_string("HTTP/1.1 200 OK\r\n"
                                 "Connection: close\r\n"
                                 "Content-Type: %s\r\n"
                                 "Content-Length: %zu\r\n"
                                 "X-XSS-Protection: 1; mode=block\r\n"
                                 "X-Frame-Options: DENY\r\n"
                                 "Content-Security-Policy: default-src 'none'; frame-ancestors 'none'\r\n"
                                 "X-Content-Type-Options: nosniff\r\n"
                                 "Cache-Control: no-store\r\n"
                                 "Pragma: no-cache\r\n"
                                 "\r\n"
                                 "%s", content_type.c_str(), content.size(), content.c_str());
}

vespalib::string make_expected_error(int code, const vespalib::string &message) {
    return vespalib::make_string("HTTP/1.1 %d %s\r\n"
                                 "Connection: close\r\n"
                                 "\r\n", code, message.c_str());
}

//-----------------------------------------------------------------------------

struct Encryption {
    vespalib::string name;
    CryptoEngine::SP engine;
    ~Encryption();
};
Encryption::~Encryption() = default;

auto null_crypto() { return std::make_shared<NullCryptoEngine>(); }
auto xor_crypto() { return std::make_shared<XorCryptoEngine>(); }
auto tls_crypto() { return std::make_shared<TlsCryptoEngine>(make_tls_options_for_testing()); }
auto maybe_tls_crypto(bool client_tls) { return std::make_shared<MaybeTlsCryptoEngine>(tls_crypto(), client_tls); }

std::vector<Encryption> crypto_list = {{"no encryption", null_crypto()},
                                       {"ad-hoc xor", xor_crypto()},
                                       {"always TLS", tls_crypto()},
                                       {"maybe TLS; yes", maybe_tls_crypto(true)},
                                       {"maybe TLS; no", maybe_tls_crypto(false)}};

//-----------------------------------------------------------------------------

struct MyGetHandler : public Portal::GetHandler {
    std::function<void(Portal::GetRequest)> fun;
    template <typename F>
    MyGetHandler(F &&f) : fun(std::move(f)) {}
    void get(Portal::GetRequest request) override {
        fun(std::move(request));
    }
    ~MyGetHandler();
};
MyGetHandler::~MyGetHandler() = default;

//-----------------------------------------------------------------------------

TEST("require that failed portal listening throws exception") {
    EXPECT_EXCEPTION(Portal::create(null_crypto(), -37), PortListenException, "-37");
}

TEST("require that portal can listen to auto-selected port") {
    auto portal = Portal::create(null_crypto(), 0);
    EXPECT_GREATER(portal->listen_port(), 0);
}

TEST("require that simple GET works with various encryption strategies") {
    vespalib::string path = "/test";
    vespalib::string type = "application/json";
    vespalib::string content = "[1,2,3]";
    MyGetHandler handler([&](Portal::GetRequest request)
                         {
                             EXPECT_EQUAL(request.get_uri(), path);
                             request.respond_with_content(type, content);
                         });
    for (const Encryption &crypto: crypto_list) {
        fprintf(stderr, "... testing simple GET with encryption: '%s'\n", crypto.name.c_str());
        auto portal = Portal::create(crypto.engine, 0);
        auto bound = portal->bind(path, handler);
        auto expect = make_expected_response(type, content);
        auto result = fetch(portal->listen_port(), crypto.engine, path);
        EXPECT_EQUAL(result, expect);
        bound.reset();
        result = fetch(portal->listen_port(), crypto.engine, path);
        expect = make_expected_error(404, "Not Found");
        EXPECT_EQUAL(result, expect);
    }
}

//-----------------------------------------------------------------------------

TEST("require that header values can be inspected") {
    auto portal = Portal::create(null_crypto(), 0);
    MyGetHandler handler([](Portal::GetRequest request)
                         {
                             EXPECT_EQUAL(request.get_header("my-header"), "my value");
                             request.respond_with_content("a", "b");
                         });
    auto bound = portal->bind("/test", handler);
    auto result = fetch(portal->listen_port(), null_crypto(), "/test");
    EXPECT_EQUAL(result, make_expected_response("a", "b"));
}

TEST("require that request authority can be obtained") {
    auto portal = Portal::create(null_crypto(), 0);
    MyGetHandler handler([](Portal::GetRequest request)
                         {
                             EXPECT_EQUAL(request.get_host(), "HOST:42");
                             request.respond_with_content("a", "b");
                         });
    auto bound = portal->bind("/test", handler);
    auto result = fetch(portal->listen_port(), null_crypto(), "/test");
    EXPECT_EQUAL(result, make_expected_response("a", "b"));
}

TEST("require that authority has reasonable fallback") {
    auto portal = Portal::create(null_crypto(), 0);
    auto expect_host = vespalib::make_string("%s:%d", HostName::get().c_str(), portal->listen_port());
    MyGetHandler handler([&expect_host](Portal::GetRequest request)
                         {
                             EXPECT_EQUAL(request.get_host(), expect_host);
                             request.respond_with_content("a", "b");
                         });
    auto bound = portal->bind("/test", handler);
    auto result = fetch(portal->listen_port(), null_crypto(), "/test", false);
    EXPECT_EQUAL(result, make_expected_response("a", "b"));
}

TEST("require that methods other than GET return not implemented error") {
    auto portal = Portal::create(null_crypto(), 0);
    auto expect_get = make_expected_error(404, "Not Found");
    auto expect_other = make_expected_error(501, "Not Implemented");
    for (const auto &method: {"OPTIONS", "GET", "HEAD", "POST", "PUT", "DELETE", "TRACE", "CONNECT"}) {
        auto result = do_http(portal->listen_port(), null_crypto(), method, "/test");
        if (vespalib::string("GET") == method) {
            EXPECT_EQUAL(result, expect_get);
        } else {
            EXPECT_EQUAL(result, expect_other);
        }
    }
}

TEST("require that GET handler can return HTTP error") {
    vespalib::string path = "/test";
    auto portal = Portal::create(null_crypto(), 0);
    auto expect = make_expected_error(123, "My Error");
    MyGetHandler handler([](Portal::GetRequest request)
                         {
                             request.respond_with_error(123, "My Error");
                         });
    auto bound = portal->bind(path, handler);
    auto result = fetch(portal->listen_port(), null_crypto(), path);
    EXPECT_EQUAL(result, expect);
}

TEST("require that get requests dropped on the floor returns HTTP error") {
    vespalib::string path = "/test";
    auto portal = Portal::create(null_crypto(), 0);
    auto expect = make_expected_error(500, "Internal Server Error");
    MyGetHandler handler([](Portal::GetRequest) noexcept {});
    auto bound = portal->bind(path, handler);
    auto result = fetch(portal->listen_port(), null_crypto(), path);
    EXPECT_EQUAL(result, expect);
}

TEST("require that bogus request returns HTTP error") {
    auto portal = Portal::create(null_crypto(), 0);
    auto expect = make_expected_error(400, "Bad Request");
    auto result = do_http(portal->listen_port(), null_crypto(), "this request is", " totally bogus\r\n");
    EXPECT_EQUAL(result, expect);
}

TEST("require that the handler with the longest matching prefix is selected") {
    auto portal = Portal::create(null_crypto(), 0);
    MyGetHandler handler1([](Portal::GetRequest request){ request.respond_with_content("text/plain", "handler1"); });
    MyGetHandler handler2([](Portal::GetRequest request){ request.respond_with_content("text/plain", "handler2"); });
    MyGetHandler handler3([](Portal::GetRequest request){ request.respond_with_content("text/plain", "handler3"); });
    auto bound1 = portal->bind("/foo", handler1);
    auto bound3 = portal->bind("/foo/bar/baz", handler3);
    auto bound2 = portal->bind("/foo/bar", handler2);
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo"), make_expected_response("text/plain", "handler1"));
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo/bar"), make_expected_response("text/plain", "handler2"));
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo/bar/baz"), make_expected_response("text/plain", "handler3"));
    bound3.reset();
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo/bar/baz"), make_expected_response("text/plain", "handler2"));
    bound2.reset();
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo/bar/baz"), make_expected_response("text/plain", "handler1"));
}

TEST("require that newer handlers with the same prefix shadows older ones") {
    auto portal = Portal::create(null_crypto(), 0);
    MyGetHandler handler1([](Portal::GetRequest request){ request.respond_with_content("text/plain", "handler1"); });
    MyGetHandler handler2([](Portal::GetRequest request){ request.respond_with_content("text/plain", "handler2"); });
    MyGetHandler handler3([](Portal::GetRequest request){ request.respond_with_content("text/plain", "handler3"); });
    auto bound1 = portal->bind("/foo", handler1);
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo"), make_expected_response("text/plain", "handler1"));
    auto bound2 = portal->bind("/foo", handler2);
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo"), make_expected_response("text/plain", "handler2"));
    auto bound3 = portal->bind("/foo", handler3);
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo"), make_expected_response("text/plain", "handler3"));
    bound3.reset();
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo"), make_expected_response("text/plain", "handler2"));
    bound2.reset();
    EXPECT_EQUAL(fetch(portal->listen_port(), null_crypto(), "/foo"), make_expected_response("text/plain", "handler1"));
}

TEST("require that connection errors do not block shutdown by leaking resources") {
    MyGetHandler handler([](Portal::GetRequest request)
                         {
                             std::this_thread::sleep_for(std::chrono::milliseconds(5));
                             request.respond_with_content("application/json", "[1,2,3]");
                         });
    for (const Encryption &crypto: crypto_list) {
        fprintf(stderr, "... testing connection errors with encryption: '%s'\n", crypto.name.c_str());
        auto portal = Portal::create(crypto.engine, 0);
        auto bound = portal->bind("/test", handler);
        { // close before sending anything
            auto socket = SocketSpec::from_port(portal->listen_port()).client_address().connect();
            auto conn = SyncCryptoSocket::create_client(*crypto.engine, std::move(socket), make_local_spec());
        }
        { // send partial request then close connection
            auto socket = SocketSpec::from_port(portal->listen_port()).client_address().connect();
            auto conn = SyncCryptoSocket::create_client(*crypto.engine, std::move(socket), make_local_spec());
            vespalib::string req = "GET /test HTTP/1.1\r\n"
                                   "Host: local";
            ASSERT_EQUAL(conn->write(req.data(), req.size()), ssize_t(req.size()));
        }
        { // send request then close without reading response
            auto socket = SocketSpec::from_port(portal->listen_port()).client_address().connect();
            auto conn = SyncCryptoSocket::create_client(*crypto.engine, std::move(socket), make_local_spec());
            vespalib::string req = "GET /test HTTP/1.1\r\n"
                                   "Host: localhost\r\n"
                                   "\r\n";
            ASSERT_EQUAL(conn->write(req.data(), req.size()), ssize_t(req.size()));
        }
    }
}

struct LatchedFixture {
    Portal::SP portal;
    MyGetHandler handler;
    Portal::Token::UP bound;
    Gate enter_callback;
    Latch<Portal::GetRequest> latch;
    Gate exit_callback;
    LatchedFixture() : portal(Portal::create(null_crypto(), 0)),
                       handler([this](Portal::GetRequest request)
                               {
                                   enter_callback.countDown();
                                   latch.write(std::move(request));
                                   exit_callback.await();
                               }),
                       bound(portal->bind("/test", handler)),
                       enter_callback(), latch(), exit_callback() {}
};

TEST_MT_FF("require that GET requests can be completed in another thread", 2,
           LatchedFixture(), TimeBomb(60))
{
    if (thread_id == 0) {
        Portal::GetRequest req = f1.latch.read();
        f1.exit_callback.countDown();
        std::this_thread::sleep_for(5ms);
        req.respond_with_content("text/plain", "hello");
    } else {
        auto result = fetch(f1.portal->listen_port(), null_crypto(), "/test");
        EXPECT_EQUAL(result, make_expected_response("text/plain", "hello"));
    }
}

TEST_MT_FFF("require that bind token destruction waits for active callbacks", 3,
            LatchedFixture(), Gate(), TimeBomb(60))
{
    if (thread_id == 0) {
        Portal::GetRequest req = f1.latch.read();
        EXPECT_TRUE(!f2.await(20ms));
        f1.exit_callback.countDown();
        EXPECT_TRUE(f2.await(60s));
        req.respond_with_content("application/json", "[1,2,3]");
    } else if (thread_id == 1) {
        f1.enter_callback.await();
        f1.bound.reset();
        f2.countDown();
    } else {
        auto result = fetch(f1.portal->listen_port(), null_crypto(), "/test");
        EXPECT_EQUAL(result, make_expected_response("application/json", "[1,2,3]"));
    }
}

TEST_MT_FFF("require that portal destruction waits for request completion", 3,
            LatchedFixture(), Gate(), TimeBomb(60))
{
    if (thread_id == 0) {
        Portal::GetRequest req = f1.latch.read();
        f1.exit_callback.countDown();
        EXPECT_TRUE(!f2.await(20ms));
        req.respond_with_content("application/json", "[1,2,3]");
        EXPECT_TRUE(f2.await(60s));
    } else if (thread_id == 1) {
        f1.enter_callback.await();
        f1.bound.reset();
        f1.portal.reset();
        f2.countDown();
    } else {
        auto result = fetch(f1.portal->listen_port(), null_crypto(), "/test");
        EXPECT_EQUAL(result, make_expected_response("application/json", "[1,2,3]"));
    }
}

//-----------------------------------------------------------------------------

TEST("require that query parameters can be inspected") {
    auto portal = Portal::create(null_crypto(), 0);
    MyGetHandler handler([](Portal::GetRequest request)
                         {
                             EXPECT_EQUAL(request.get_uri(), "/test?a=b&x=y");
                             EXPECT_EQUAL(request.get_path(), "/test");
                             EXPECT_TRUE(request.has_param("a"));
                             EXPECT_TRUE(request.has_param("x"));
                             EXPECT_TRUE(!request.has_param("b"));
                             EXPECT_EQUAL(request.get_param("a"), "b");
                             EXPECT_EQUAL(request.get_param("x"), "y");
                             EXPECT_EQUAL(request.get_param("b"), "");
                             auto params = request.export_params();
                             EXPECT_EQUAL(params.size(), 2u);
                             EXPECT_EQUAL(params["a"], "b");
                             EXPECT_EQUAL(params["x"], "y");
                             request.respond_with_content("a", "b");
                         });
    auto bound = portal->bind("/test", handler);
    auto result = fetch(portal->listen_port(), null_crypto(), "/test?a=b&x=y");
    EXPECT_EQUAL(result, make_expected_response("a", "b"));
}

TEST("require that request path is dequoted before handler dispatching") {
    auto portal = Portal::create(null_crypto(), 0);
    MyGetHandler handler([](Portal::GetRequest request)
                         {
                             EXPECT_EQUAL(request.get_uri(), "/%5btest%5D");
                             EXPECT_EQUAL(request.get_path(), "/[test]");
                             request.respond_with_content("a", "b");
                         });
    auto bound = portal->bind("/[test]", handler);
    auto result = fetch(portal->listen_port(), null_crypto(), "/%5btest%5D");
    EXPECT_EQUAL(result, make_expected_response("a", "b"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
