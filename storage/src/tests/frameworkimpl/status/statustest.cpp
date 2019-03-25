// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/defaultimplementation/component/componentregisterimpl.h>
#include <vespa/storage/frameworkimpl/status/statuswebserver.h>
#include <vespa/storageframework/defaultimplementation/thread/threadpoolimpl.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageframework/generic/status/xmlstatusreporter.h>
#include <tests/common/teststorageapp.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/net/crypto_engine.h>
#include <vespa/vespalib/net/socket_spec.h>
#include <vespa/vespalib/net/sync_crypto_socket.h>

vespalib::string fetch(int port, const vespalib::string &path) {
    auto crypto = vespalib::CryptoEngine::get_default();
    auto socket = vespalib::SocketSpec::from_port(port).client_address().connect();
    CPPUNIT_ASSERT(socket.valid());
    auto conn = vespalib::SyncCryptoSocket::create(*crypto, std::move(socket), false);
    vespalib::string http_req = vespalib::make_string("GET %s HTTP/1.1\r\n"
                                                      "Host: localhost:%d\r\n"
                                                      "\r\n", path.c_str(), port);
    CPPUNIT_ASSERT_EQUAL(conn->write(http_req.data(), http_req.size()), ssize_t(http_req.size()));
    char buf[1024];
    vespalib::string result;
    ssize_t res = conn->read(buf, sizeof(buf));
    while (res > 0) {
        result.append(vespalib::stringref(buf, res));
        res = conn->read(buf, sizeof(buf));
    }
    CPPUNIT_ASSERT_EQUAL(res, ssize_t(0));
    return result;
}

namespace storage {

struct StatusTest : public CppUnit::TestFixture {
    std::unique_ptr<TestServiceLayerApp> _node;

    void setUp() override;

    void testIndexStatusPage();
    void testHtmlStatus();
    void testXmlStatus();
    void test404();
    void requireThatServerSpecIsConstructedCorrectly();

    CPPUNIT_TEST_SUITE(StatusTest);
    CPPUNIT_TEST(testIndexStatusPage);
    CPPUNIT_TEST(testHtmlStatus);
    CPPUNIT_TEST(testXmlStatus);
    CPPUNIT_TEST(test404);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(StatusTest);

namespace {
    struct HtmlStatusReporter : public framework::HtmlStatusReporter {
        std::string _headerAddition;
        std::string _content;

        HtmlStatusReporter(const std::string& id, const std::string& name,
                           const std::string& content,
                           const std::string& headerAddition = "")
            : framework::HtmlStatusReporter(id, name),
              _headerAddition(headerAddition),
              _content(content)
        {}

        void reportHtmlHeaderAdditions(std::ostream& out, const framework::HttpUrlPath&) const override {
            out << _headerAddition;
        }

        void reportHtmlStatus(std::ostream& out, const framework::HttpUrlPath&) const override {
            out << _content;
        }
    };

    struct XmlStatusReporter : public framework::XmlStatusReporter {
        XmlStatusReporter(const std::string& id, const std::string& name)
            : framework::XmlStatusReporter(id, name) {}

        vespalib::string reportXmlStatus(vespalib::xml::XmlOutputStream& xos,
                                         const framework::HttpUrlPath&) const override
        {
            xos << vespalib::xml::XmlTag("mytag")
                << vespalib::xml::XmlAttribute("foo", "bar")
                << vespalib::xml::XmlContent("content")
                << vespalib::xml::XmlEndTag();
            return "";
        }
    };

    struct StatusComponent : public framework::Component {
        framework::StatusReporter* _reporter;

        StatusComponent(framework::ComponentRegister& reg, const char* name,
                        framework::StatusReporter* reporter)
            : framework::Component(reg, name),
              _reporter(reporter)
        {
            registerStatusPage(*_reporter);
        }
        ~StatusComponent() { delete _reporter; }
    };

}

void
StatusTest::setUp()
{
    _node.reset(new TestServiceLayerApp);
}

void
StatusTest::testIndexStatusPage()
{
    StatusComponent rep1(_node->getComponentRegister(), "foo",
                         new HtmlStatusReporter(
                            "fooid", "Foo impl", "<p>info</p>"));
    StatusComponent rep2(_node->getComponentRegister(), "bar",
                         new HtmlStatusReporter(
                            "barid", "Bar impl", "<p>info</p>"));
    StatusWebServer webServer(_node->getComponentRegister(),
                              _node->getComponentRegister(),
                              "raw:httpport 0");
    auto actual = fetch(webServer.getListenPort(), "/");
    std::string expected(
            "HTTP\\/1.1 200 OK\r\n"
            "Connection: close\r\n"
            "Content-Type: text\\/html\r\n"
            "Content-Length: [0-9]+\r\n"
            "\r\n"
            "<html>\n"
            "<head>\n"
            "  <title>Index page</title>\n"
            "<\\/head>\n"
            "<body>\n"
            "  <h1>Index page</h1>\n"
            "<p><b>Binary version of Vespa:<\\/b> [0-9.]+<\\/p>\n"
            "<a href=\"fooid\">Foo impl<\\/a><br>\n"
            "<a href=\"barid\">Bar impl<\\/a><br>\n"
            "<\\/body>\n"
            "<\\/html>\n"
    );
    CPPUNIT_ASSERT_MATCH_REGEX(expected, actual);
}

void
StatusTest::testHtmlStatus()
{
    StatusComponent rep1(_node->getComponentRegister(), "foo",
            new HtmlStatusReporter(
                "fooid", "Foo impl", "<p>info</p>", "<!-- script -->"));
    StatusWebServer webServer(_node->getComponentRegister(),
                              _node->getComponentRegister(),
                              "raw:httpport 0");
    auto actual = fetch(webServer.getListenPort(), "/fooid?unusedParam");
    std::string expected(
            "HTTP/1.1 200 OK\r\n"
            "Connection: close\r\n"
            "Content-Type: text/html\r\n"
            "Content-Length: [0-9]+\r\n"
            "\r\n"
            "<html>\n"
            "<head>\n"
            "  <title>Foo impl</title>\n"
            "<!-- script --></head>\n"
            "<body>\n"
            "  <h1>Foo impl</h1>\n"
            "<p>info</p></body>\n"
            "</html>\n"
    );
    CPPUNIT_ASSERT_EQUAL(expected, std::string(actual));
}

void
StatusTest::testXmlStatus()
{
    StatusComponent rep1(_node->getComponentRegister(), "foo",
            new XmlStatusReporter(
                "fooid", "Foo impl"));
    StatusWebServer webServer(_node->getComponentRegister(),
                              _node->getComponentRegister(),
                              "raw:httpport 0");
    auto actual = fetch(webServer.getListenPort(), "/fooid?unusedParam");
    std::string expected(
            "HTTP/1.1 200 OK\r\n"
            "Connection: close\r\n"
            "Content-Type: application/xml\r\n"
            "Content-Length: [0-9]+\r\n"
            "\r\n"
            "<?xml version=\"1.0\"?>\n"
            "<status id=\"fooid\" name=\"Foo impl\">\n"
            "<mytag foo=\"bar\">content</mytag>\n"
            "</status>"
    );
    CPPUNIT_ASSERT_EQUAL(expected, std::string(actual));
}

void
StatusTest::test404()
{
    StatusWebServer webServer(_node->getComponentRegister(),
                              _node->getComponentRegister(),
                              "raw:httpport 0");
    auto actual = fetch(webServer.getListenPort(), "/fooid?unusedParam");
    std::string expected(
            "HTTP/1.1 404 Not Found\r\n"
            "Connection: close\r\n"
            "\r\n"
    );
    CPPUNIT_ASSERT_EQUAL_ESCAPED(expected, std::string(actual));
}

} // storage
