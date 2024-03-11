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
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("routeparser_test");

using namespace mbus;

namespace {

bool
testErrorDirective(const IHopDirective & dir, const string &msg)
{
    bool failed = false;
    EXPECT_EQ(IHopDirective::TYPE_ERROR, dir.getType()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(msg, static_cast<const ErrorDirective&>(dir).getMessage()) << (failed = true, "");
    return !failed;
}

bool
testError(const Hop &hop, const string &msg)
{
    LOG(info, "%s", hop.toDebugString().c_str());
    bool failed = false;
    EXPECT_EQ(1u, hop.getNumDirectives()) << (failed = true, "");
    if (failed) {
        return false;
    }
    if (!testErrorDirective(hop.getDirective(0), msg)) {
        return false;
    }
    return true;
}

bool
testError(const Route &route, const string &msg)
{
    bool failed = false;
    EXPECT_EQ(1u, route.getNumHops()) << (failed = true, "");
    if (failed) {
        return false;
    }
    if (!testError(route.getHop(0), msg)) {
        return false;
    }
    return true;
}

bool
testPolicyDirective(const IHopDirective & dir, const string &name, const string &param)
{
    bool failed = false;
    EXPECT_EQ(IHopDirective::TYPE_POLICY, dir.getType()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(name, static_cast<const PolicyDirective&>(dir).getName()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(param, static_cast<const PolicyDirective&>(dir).getParam()) << (failed = true, "");
    return !failed;
}

bool
testRouteDirective(const IHopDirective & dir, const string &name)
{
    bool failed = false;
    EXPECT_EQ(IHopDirective::TYPE_ROUTE, dir.getType()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(name, static_cast<const RouteDirective&>(dir).getName()) << (failed = true, "");
    return !failed;
}

bool
testTcpDirective(const IHopDirective & dir, const string &host, uint32_t port, const string &session)
{
    bool failed = false;
    EXPECT_EQ(IHopDirective::TYPE_TCP, dir.getType()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(host, static_cast<const TcpDirective&>(dir).getHost()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(port, static_cast<const TcpDirective&>(dir).getPort()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(session, static_cast<const TcpDirective&>(dir).getSession()) << (failed = true, "");
    return !failed;
}

bool
testVerbatimDirective(const IHopDirective & dir, const string &image)
{
    bool failed = false;
    EXPECT_EQ(IHopDirective::TYPE_VERBATIM, dir.getType()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(image, static_cast<const VerbatimDirective&>(dir).getImage()) << (failed = true, "");
    return !failed;
}

}

class RouteParserTest : public testing::Test {
protected:
    RouteParserTest();
    ~RouteParserTest() override;
};

RouteParserTest::RouteParserTest() = default;
RouteParserTest::~RouteParserTest() = default;

TEST_F(RouteParserTest, test_hop_parser)
{
    {
        Hop hop = Hop::parse("foo");
        EXPECT_EQ(1u, hop.getNumDirectives());
        EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "foo"));
    }
    {
        Hop hop = Hop::parse("foo/bar");
        EXPECT_EQ(2u, hop.getNumDirectives());
        EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "foo"));
        EXPECT_TRUE(testVerbatimDirective(hop.getDirective(1), "bar"));
    }
    {
        Hop hop = Hop::parse("tcp/foo:666/bar");
        EXPECT_EQ(1u, hop.getNumDirectives());
        EXPECT_TRUE(testTcpDirective(hop.getDirective(0), "foo", 666, "bar"));
    }
    {
        Hop hop = Hop::parse("route:foo");
        EXPECT_EQ(1u, hop.getNumDirectives());
        EXPECT_TRUE(testRouteDirective(hop.getDirective(0), "foo"));
    }
    {
        Hop hop = Hop::parse("[Extern:tcp/localhost:3619;foo/bar]");
        EXPECT_EQ(1u, hop.getNumDirectives());
        EXPECT_TRUE(testPolicyDirective(hop.getDirective(0), "Extern", "tcp/localhost:3619;foo/bar"));
    }
    {
        Hop hop = Hop::parse("[AND:foo bar]");
        EXPECT_EQ(1u, hop.getNumDirectives());
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
        EXPECT_EQ(1u, hop.getNumDirectives());
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
        EXPECT_EQ(1u, hop.getNumDirectives());
        EXPECT_TRUE(testPolicyDirective(hop.getDirective(0), "DocumentRouteSelector",
                                       "raw:route[1]\n"
                                       "route[0].name \"docproc/cluster.foo\"\n"
                                       "route[0].selector \"testdoc\"\n"
                                       "route[0].feed \"myfeed\"\n"));
    }
}

TEST_F(RouteParserTest, test_hop_parser_errors)
{
    EXPECT_TRUE(testError(Hop::parse(""), "Failed to parse empty string."));
    EXPECT_TRUE(testError(Hop::parse("[foo"), "Unexpected token '': syntax error"));
    EXPECT_TRUE(testError(Hop::parse("foo/[bar]]"), "Unexpected token ']': syntax error"));
    EXPECT_TRUE(testError(Hop::parse("foo bar"), "Failed to completely parse 'foo bar'."));
}

TEST_F(RouteParserTest, test_route_parser)
{
    {
        Route route = Route::parse("foo bar/baz");
        EXPECT_EQ(2u, route.getNumHops());
        {
            const Hop &hop = route.getHop(0);
            EXPECT_EQ(1u, hop.getNumDirectives());
            EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "foo"));
        }
        {
            const Hop &hop = route.getHop(1);
            EXPECT_EQ(2u, hop.getNumDirectives());
            EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "bar"));
            EXPECT_TRUE(testVerbatimDirective(hop.getDirective(1), "baz"));
        }
    }
    {
        Route route = Route::parse("[Extern:tcp/localhost:3633;itr/session] default");
        EXPECT_EQ(2u, route.getNumHops());
        {
            const Hop &hop = route.getHop(0);
            EXPECT_EQ(1u, hop.getNumDirectives());
            EXPECT_TRUE(testPolicyDirective(hop.getDirective(0), "Extern", "tcp/localhost:3633;itr/session"));
        }
        {
            const Hop &hop = route.getHop(1);
            EXPECT_EQ(1u, hop.getNumDirectives());
            EXPECT_TRUE(testVerbatimDirective(hop.getDirective(0), "default"));
        }
    }
}

TEST_F(RouteParserTest, test_route_parser_errors)
{
    EXPECT_TRUE(testError(Route::parse(""), "Failed to parse empty string."));
    EXPECT_TRUE(testError(Route::parse("foo [bar"), "Unexpected token '': syntax error"));
    EXPECT_TRUE(testError(Route::parse("foo bar/[baz]]"), "Unexpected token ']': syntax error"));
}

GTEST_MAIN_RUN_ALL_TESTS()
