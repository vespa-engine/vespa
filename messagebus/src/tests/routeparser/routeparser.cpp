// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/routing/errordirective.h>
#include <vespa/messagebus/routing/policydirective.h>
#include <vespa/messagebus/routing/route.h>
#include <vespa/messagebus/routing/routedirective.h>
#include <vespa/messagebus/routing/tcpdirective.h>
#include <vespa/messagebus/routing/verbatimdirective.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("routeparser_test");

using namespace mbus;

class Test : public vespalib::TestApp {
public:
    int Main() override;
    void testHopParser();
    void testHopParserErrors();
    void testRouteParser();
    void testRouteParserErrors();

private:
    bool testError(const Route &route, const string &msg);
    bool testError(const Hop &hop, const string &msg);
    bool testErrorDirective(const IHopDirective & dir, const string &msg);
    bool testPolicyDirective(const IHopDirective & dir, const string &name, const string &param);
    bool testRouteDirective(const IHopDirective & dir, const string &name);
    bool testTcpDirective(const IHopDirective & dir, const string &host, uint32_t port, const string &session);
    bool testVerbatimDirective(const IHopDirective & dir, const string &image);
};

TEST_APPHOOK(Test);

int
Test::Main()
{
    TEST_INIT("routeparser_test");

    testHopParser();         TEST_FLUSH();
    testHopParserErrors();   TEST_FLUSH();
    testRouteParser();       TEST_FLUSH();
    testRouteParserErrors(); TEST_FLUSH();

    TEST_DONE();
}

bool
Test::testError(const Route &route, const string &msg)
{
    if (!EXPECT_EQUAL(1u, route.getNumHops())) {
        return false;
    }
    if (!testError(route.getHop(0), msg)) {
        return false;
    }
    return true;
}

bool
Test::testError(const Hop &hop, const string &msg)
{
    LOG(info, "%s", hop.toDebugString().c_str());
    if (!EXPECT_EQUAL(1u, hop.getNumDirectives())) {
        return false;
    }
    if (!testErrorDirective(hop.getDirective(0), msg)) {
        return false;
    }
    return true;
}

bool
Test::testErrorDirective(const IHopDirective & dir, const string &msg)
{
    if (!EXPECT_EQUAL(IHopDirective::TYPE_ERROR, dir.getType())) {
        return false;
    }
    if (!EXPECT_EQUAL(msg, static_cast<const ErrorDirective&>(dir).getMessage())) {
        return false;
    }
    return true;
}

bool
Test::testPolicyDirective(const IHopDirective & dir, const string &name, const string &param)
{
    if (!EXPECT_EQUAL(IHopDirective::TYPE_POLICY, dir.getType())) {
        return false;
    }
    if (!EXPECT_EQUAL(name, static_cast<const PolicyDirective&>(dir).getName())) {
        return false;
    }
    if (!EXPECT_EQUAL(param, static_cast<const PolicyDirective&>(dir).getParam())) {
        return false;
    }
    return true;
}

bool
Test::testRouteDirective(const IHopDirective & dir, const string &name)
{
    if (!EXPECT_EQUAL(IHopDirective::TYPE_ROUTE, dir.getType())) {
        return false;
    }
    if (!EXPECT_EQUAL(name, static_cast<const RouteDirective&>(dir).getName())) {
        return false;
    }
    return true;
}

bool
Test::testTcpDirective(const IHopDirective & dir, const string &host, uint32_t port, const string &session)
{
    if (!EXPECT_EQUAL(IHopDirective::TYPE_TCP, dir.getType())) {
        return false;
    }
    if (!EXPECT_EQUAL(host, static_cast<const TcpDirective&>(dir).getHost())) {
        return false;
    }
    if (!EXPECT_EQUAL(port, static_cast<const TcpDirective&>(dir).getPort())) {
        return false;
    }
    if (!EXPECT_EQUAL(session, static_cast<const TcpDirective&>(dir).getSession())) {
        return false;
    }
    return true;
}

bool
Test::testVerbatimDirective(const IHopDirective & dir, const string &image)
{
    if (!EXPECT_EQUAL(IHopDirective::TYPE_VERBATIM, dir.getType())) {
        return false;
    }
    if (!EXPECT_EQUAL(image, static_cast<const VerbatimDirective&>(dir).getImage())) {
        return false;
    }
    return true;
}

void
Test::testHopParser()
{
    {
        Hop hop = Hop::parse("foo");
        EXPECT_EQUAL(1u, hop.getNumDirectives());
        EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "foo"));
    }
    {
        Hop hop = Hop::parse("foo/bar");
        EXPECT_EQUAL(2u, hop.getNumDirectives());
        EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "foo"));
        EXPECT_TRUE(testVerbatimDirective(hop.getDirective(1), "bar"));
    }
    {
        Hop hop = Hop::parse("tcp/foo:666/bar");
        EXPECT_EQUAL(1u, hop.getNumDirectives());
        EXPECT_TRUE(testTcpDirective(hop.getDirective(0), "foo", 666, "bar"));
    }
    {
        Hop hop = Hop::parse("route:foo");
        EXPECT_EQUAL(1u, hop.getNumDirectives());
        EXPECT_TRUE(testRouteDirective(hop.getDirective(0), "foo"));
    }
    {
        Hop hop = Hop::parse("[Extern:tcp/localhost:3619;foo/bar]");
        EXPECT_EQUAL(1u, hop.getNumDirectives());
        EXPECT_TRUE(testPolicyDirective(hop.getDirective(0), "Extern", "tcp/localhost:3619;foo/bar"));
    }
    {
        Hop hop = Hop::parse("[AND:foo bar]");
        EXPECT_EQUAL(1u, hop.getNumDirectives());
        EXPECT_TRUE(testPolicyDirective(hop.getDirective(0), "AND", "foo bar"));
    }
    {
        Hop hop = Hop::parse("[DocumentRouteSelector:raw:route[2]\n"
                             "route[0].name \"foo\"\n"
                             "route[0].selector \"testdoc\"\n"
                             "route[0].feed \"myfeed\"\n"
                             "route[1].name \"bar\"\n"
                             "route[1].selector \"other\"\n"
                             "route[1].feed \"myfeed\"\n"
                             "]");
        EXPECT_EQUAL(1u, hop.getNumDirectives());
        EXPECT_TRUE(testPolicyDirective(hop.getDirective(0), "DocumentRouteSelector",
                                       "raw:route[2]\n"
                                       "route[0].name \"foo\"\n"
                                       "route[0].selector \"testdoc\"\n"
                                       "route[0].feed \"myfeed\"\n"
                                       "route[1].name \"bar\"\n"
                                       "route[1].selector \"other\"\n"
                                       "route[1].feed \"myfeed\"\n"));
    }
    {
        Hop hop = Hop::parse("[DocumentRouteSelector:raw:route[1]\n"
                             "route[0].name \"docproc/cluster.foo\"\n"
                             "route[0].selector \"testdoc\"\n"
                             "route[0].feed \"myfeed\"\n"
                             "]");
        EXPECT_EQUAL(1u, hop.getNumDirectives());
        EXPECT_TRUE(testPolicyDirective(hop.getDirective(0), "DocumentRouteSelector",
                                       "raw:route[1]\n"
                                       "route[0].name \"docproc/cluster.foo\"\n"
                                       "route[0].selector \"testdoc\"\n"
                                       "route[0].feed \"myfeed\"\n"));
    }
}

void
Test::testHopParserErrors()
{
    EXPECT_TRUE(testError(Hop::parse(""), "Failed to parse empty string."));
    EXPECT_TRUE(testError(Hop::parse("[foo"), "Unexpected token '': syntax error"));
    EXPECT_TRUE(testError(Hop::parse("foo/[bar]]"), "Unexpected token ']': syntax error"));
    EXPECT_TRUE(testError(Hop::parse("foo bar"), "Failed to completely parse 'foo bar'."));
}

void
Test::testRouteParser()
{
    {
        Route route = Route::parse("foo bar/baz");
        EXPECT_EQUAL(2u, route.getNumHops());
        {
            const Hop &hop = route.getHop(0);
            EXPECT_EQUAL(1u, hop.getNumDirectives());
            EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "foo"));
        }
        {
            const Hop &hop = route.getHop(1);
            EXPECT_EQUAL(2u, hop.getNumDirectives());
            EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "bar"));
            EXPECT_TRUE(testVerbatimDirective(hop.getDirective(1), "baz"));
        }
    }
    {
        Route route = Route::parse("[Extern:tcp/localhost:3633;itr/session] default");
        EXPECT_EQUAL(2u, route.getNumHops());
        {
            const Hop &hop = route.getHop(0);
            EXPECT_EQUAL(1u, hop.getNumDirectives());
            EXPECT_TRUE(testPolicyDirective(hop.getDirective(0), "Extern", "tcp/localhost:3633;itr/session"));
        }
        {
            const Hop &hop = route.getHop(1);
            EXPECT_EQUAL(1u, hop.getNumDirectives());
            EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "default"));
        }
    }
}

void
Test::testRouteParserErrors()
{
    EXPECT_TRUE(testError(Route::parse(""), "Failed to parse empty string."));
    EXPECT_TRUE(testError(Route::parse("foo [bar"), "Unexpected token '': syntax error"));
    EXPECT_TRUE(testError(Route::parse("foo bar/[baz]]"), "Unexpected token ']': syntax error"));
}
