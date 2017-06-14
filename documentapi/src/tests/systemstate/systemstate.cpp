// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/documentapi/messagebus/systemstate/systemstate.h>
#include <vespa/documentapi/messagebus/systemstate/nodestate.h>
#include <vespa/documentapi/messagebus/systemstate/systemstatehandle.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("systemstate_test");

using namespace documentapi;

class Test : public vespalib::TestApp {
public:
    int Main() override;
    void testParser();
    void testPathing();
    void testState();
    void testEncoding();
    void testHandle();
    void testCompact();

private:
    void assertParser(const string &state, const string &expected = "");
};

TEST_APPHOOK(Test);

int
Test::Main()
{
    TEST_INIT("systemstate_test");

    testParser();   TEST_FLUSH();
    testPathing();  TEST_FLUSH();
    testState();    TEST_FLUSH();
    testEncoding(); TEST_FLUSH();
    testHandle();   TEST_FLUSH();
    testCompact();  TEST_FLUSH();

    TEST_DONE();
    return 0;
}

void
Test::testParser()
{
    assertParser("storage");
    assertParser("storage?", "ERROR");
    assertParser("storage?a", "ERROR");
    assertParser("storage?a=", "ERROR");
    assertParser("storage?a=1");
    assertParser("storage?a=1&", "ERROR");
    assertParser("storage?a=1&b", "ERROR");
    assertParser("storage?a=1&b=2");
    assertParser("storage?a=1&b=2 search");
    assertParser("storage?a=1&b=2 search?", "ERROR");
    assertParser("storage?a=1&b=2 search?a", "ERROR");
    assertParser("storage?a=1&b=2 search?a=", "ERROR");
    assertParser("storage?a=1&b=2 search?a=1");
    assertParser("storage?a=1&b=2 search?a=1&", "ERROR");
    assertParser("storage?a=1&b=2 search?a=1&b", "ERROR");
    assertParser("storage?a=1&b=2 search?a=1&b=", "ERROR");
    assertParser("storage?a=1&b=2 search?a=1&b=2");

    assertParser("storage");
    assertParser("storage/");
    assertParser("storage/?", "ERROR");
    assertParser("storage/?a", "ERROR");
    assertParser("storage/?a=", "ERROR");
    assertParser("storage/?a=1");
    assertParser("storage/cluster.storage");
    assertParser("storage/cluster.storage/");

    assertParser("storage?a=1");
    assertParser("storage/?a=1");
    assertParser("storage/.?a=1");
    assertParser("storage/./?a=1");
    assertParser("storage/./cluster.storage?a=1");
    assertParser("storage/./cluster.storage/?a=1");
    assertParser("storage/./cluster.storage/..?a=1");
    assertParser("storage/./cluster.storage/../?a=1");
    assertParser("storage/./cluster.storage/../storage?a=1");
    assertParser("storage/./cluster.storage/../storage/?a=1");
}

void
Test::testPathing()
{
    assertParser("storage?a=1", "storage?a=1");
    assertParser("storage/?a=1", "storage?a=1");
    assertParser("storage/.?a=1", "storage?a=1");
    assertParser("storage/./?a=1", "storage?a=1");
    assertParser("storage/./cluster.storage?a=1", "storage/cluster.storage?a=1");
    assertParser("storage/./cluster.storage/?a=1", "storage/cluster.storage?a=1");
    assertParser("storage/./cluster.storage/..?a=1", "storage?a=1");
    assertParser("storage/./cluster.storage/../?a=1", "storage?a=1");
    assertParser("storage/./cluster.storage/../storage?a=1", "storage/storage?a=1");
    assertParser("storage/./cluster.storage/../storage/?a=1", "storage/storage?a=1");

    assertParser("a?p1=1 a/b?p2=2 a/b/c?p3=3", "a?p1=1 a/b?p2=2 a/b/c?p3=3");
    assertParser("a .?p1=1 ./b?p2=2 ./b/c?p3=3", "a?p1=1 a/b?p2=2 a/b/c?p3=3");
    assertParser("a .?p1=1 ./../a/b/ .?p2=2 c?p3=3", "a?p1=1 a/b?p2=2 a/b/c?p3=3");
    assertParser("a/./ .?p1=1 ../a/b/c/.. .?p2=2 ./c/../c?p3=3", "a?p1=1 a/b?p2=2 a/b/c?p3=3");
    assertParser("a/b/c/d/ ../../ ../ ../a .?p1=1 ./b?p2=2 ./ ../a/b/c?p3=3", "a?p1=1 a/b?p2=2 a/b/c?p3=3");

    assertParser("a/b/c/d?p1=1 a?p2=2", "a?p2=2 a/b/c/d?p1=1");
    assertParser("a/b/c/d/?p1=1 /a?p2=2", "a?p2=2 a/b/c/d?p1=1");
    assertParser("/a/b/c/d/?p1=1 /a?p2=2", "a?p2=2 a/b/c/d?p1=1");

    assertParser("a .?p1=1", "a?p1=1");
    assertParser("a/b .?p1=1", "a/b?p1=1");
    assertParser("a/b c?p1=1 d?p2=2", "a/b/c?p1=1 a/b/d?p2=2");
}

void
Test::testState()
{
    NodeState state;
    state
        .addChild("distributor", NodeState()
                  .setState("n", "27"))
        .addChild("storage", NodeState()
                  .setState("n", "170")
                  .addChild("2", NodeState()
                            .setState("s", "d"))
                  .addChild("13", NodeState()
                            .setState("s", "r")
                            .setState("c", "0.0")));

    EXPECT_EQUAL("27", state.getState("distributor/n"));
    EXPECT_EQUAL("170", state.getState("storage/n"));
    EXPECT_EQUAL("d", state.getState("storage/2/s"));
    EXPECT_EQUAL("r", state.getState("storage/13/s"));
    EXPECT_EQUAL("0.0", state.getState("storage/13/c"));

    EXPECT_EQUAL("27", state.getChild("distributor")->getState("n"));
    EXPECT_EQUAL("170", state.getChild("storage")->getState("n"));
    EXPECT_EQUAL("d", state.getChild("storage")->getChild("2")->getState("s"));
    EXPECT_EQUAL("r", state.getChild("storage")->getChild("13")->getState("s"));
    EXPECT_EQUAL("0.0", state.getChild("storage")->getChild("13")->getState("c"));
}

void
Test::testEncoding()
{
    NodeState state;
    state.setState("foo", "http://search.yahoo.com/?query=bar");
    LOG(info, "'%s'", state.toString().c_str());
    EXPECT_EQUAL(".?foo=http%3A%2F%2Fsearch.yahoo.com%2F%3Fquery%3Dbar", state.toString());
    assertParser(state.toString(), state.toString());

    state = NodeState()
        .addChild("foo:bar", NodeState()
                  .setState("foo", "http://search.yahoo.com/?query=bar"));
    LOG(info, "'%s'", state.toString().c_str());
    EXPECT_EQUAL("foo%3Abar?foo=http%3A%2F%2Fsearch.yahoo.com%2F%3Fquery%3Dbar", state.toString());
    assertParser(state.toString(), state.toString());

    state = NodeState()
        .addChild("foo/bar", NodeState()
                  .setState("foo", "http://search.yahoo.com/?query=bar"));
    LOG(info, "'%s'", state.toString().c_str());
    EXPECT_EQUAL("foo/bar?foo=http%3A%2F%2Fsearch.yahoo.com%2F%3Fquery%3Dbar", state.toString());
    assertParser(state.toString(), state.toString());
}

void
Test::testHandle()
{
    SystemState::UP state(SystemState::newInstance(""));
    ASSERT_TRUE(state.get() != NULL);

    SystemStateHandle handle(*state);
    ASSERT_TRUE(handle.isValid());

    SystemStateHandle hoe(handle);
    ASSERT_TRUE(!handle.isValid());
    ASSERT_TRUE(hoe.isValid());
}

void
Test::testCompact()
{
    NodeState state;
    state
        .setState("a/b0/s", "d")
        .setState("a/b0/c0/s", "d")
        .setState("a/b0/c1/s", "d")
        .setState("a/b1/s", "d")
        .setState("a/b1/c0/s", "d")
        .setState("a/b1/c1/s", "d");
    EXPECT_EQUAL("a/b0?s=d a/b0/c0?s=d a/b0/c1?s=d a/b1?s=d a/b1/c0?s=d a/b1/c1?s=d", state.toString());

    state.removeChild("a/b0/c0");
    EXPECT_EQUAL("a/b0?s=d a/b0/c1?s=d a/b1?s=d a/b1/c0?s=d a/b1/c1?s=d", state.toString());

    state.removeState("a/b0/c1/s");
    EXPECT_EQUAL("a/b0?s=d a/b1?s=d a/b1/c0?s=d a/b1/c1?s=d", state.toString());

    state.setState("a/b1/c0/s", "");
    EXPECT_EQUAL("a/b0?s=d a/b1?s=d a/b1/c1?s=d", state.toString());

    state.removeChild("a/b1");
    EXPECT_EQUAL("a/b0?s=d", state.toString());

    state.removeChild("a");
    EXPECT_EQUAL("", state.toString());
}

void
Test::assertParser(const string &state, const string &expected)
{
    SystemState::UP obj = SystemState::newInstance(state);
    if (obj.get() == NULL) {
        EXPECT_EQUAL("ERROR", expected);
    }
    else {
        SystemStateHandle handle(*obj);
        LOG(info, "'%s' => '%s'", state.c_str(), handle.getRoot().toString().c_str());
        if (!expected.empty()) {
            EXPECT_EQUAL(expected, handle.getRoot().toString());
        }
    }
}

