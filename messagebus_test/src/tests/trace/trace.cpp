// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/messagebus/sourcesession.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/messagebus/intermediatesession.h>
#include <vespa/messagebus/destinationsession.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/messagebus/testlib/testserver.h>
#include <vespa/messagebus/routing/routingspec.h>
#include <vespa/messagebus/testlib/receptor.h>
#include <vespa/messagebus/sourcesessionparams.h>
#include <vespa/messagebus/testlib/simplemessage.h>
#include <vespa/messagebus/testlib/simplereply.h>
#include <vespa/messagebus/testlib/simpleprotocol.h>
#include <iostream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("trace_test");

using namespace mbus;
using vespalib::make_string;

TEST_SETUP(Test);

bool
waitSlobrok(RPCMessageBus &mbus, const std::string &pattern)
{
    for (int i = 0; i < 30000; i++) {
        slobrok::api::IMirrorAPI::SpecList res = mbus.getRPCNetwork().getMirror().lookup(pattern);
        if (res.size() > 0) {
            return true;
        }
        std::this_thread::sleep_for(10ms);
    }
    return false;
}

int
Test::Main()
{
    TEST_INIT("trace_test");
    Slobrok slobrok;
    const std::string routing_template = TEST_PATH("routing-template.cfg");
    const std::string ctl_script = TEST_PATH("ctl.sh");
    
    { // Make slobrok config
        EXPECT_TRUE(system("echo slobrok[1] > slobrok.cfg") == 0);
        EXPECT_TRUE(system(make_string("echo 'slobrok[0].connectionspec tcp/localhost:%d' "
                                      ">> slobrok.cfg", slobrok.port()).c_str()) == 0);
    }
    { // Make routing config
        EXPECT_TRUE(system(("cat " + routing_template + " > routing.cfg").c_str()) == 0);
    }
    
    EXPECT_TRUE(system((ctl_script + " start all").c_str()) == 0);
    RPCMessageBus mb(ProtocolSet().add(std::make_shared<SimpleProtocol>()),
                     RPCNetworkParams("file:slobrok.cfg"),
                     "file:routing.cfg");
    EXPECT_TRUE(waitSlobrok(mb, "server/cpp/1/A/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/cpp/2/A/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/cpp/2/B/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/cpp/3/A/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/cpp/3/B/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/cpp/3/C/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/cpp/3/D/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/java/1/A/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/java/2/A/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/java/2/B/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/java/3/A/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/java/3/B/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/java/3/C/session"));
    EXPECT_TRUE(waitSlobrok(mb, "server/java/3/D/session"));

    TraceNode e3 = TraceNode()
        .addChild(TraceNode().addChild("server/cpp/3/A (message)").addChild("server/cpp/3/A (reply)"))
        .addChild(TraceNode().addChild("server/cpp/3/B (message)").addChild("server/cpp/3/B (reply)"))
        .addChild(TraceNode().addChild("server/cpp/3/C (message)").addChild("server/cpp/3/C (reply)"))
        .addChild(TraceNode().addChild("server/cpp/3/D (message)").addChild("server/cpp/3/D (reply)"))
        .addChild(TraceNode().addChild("server/java/3/A (message)").addChild("server/java/3/A (reply)"))
        .addChild(TraceNode().addChild("server/java/3/B (message)").addChild("server/java/3/B (reply)"))
        .addChild(TraceNode().addChild("server/java/3/C (message)").addChild("server/java/3/C (reply)"))
        .addChild(TraceNode().addChild("server/java/3/D (message)").addChild("server/java/3/D (reply)")).setStrict(false);
    TraceNode e2 = TraceNode()
        .addChild(TraceNode().addChild("server/cpp/2/A (message)").addChild(e3).addChild("server/cpp/2/A (reply)"))
        .addChild(TraceNode().addChild("server/cpp/2/B (message)").addChild(e3).addChild("server/cpp/2/B (reply)"))
        .addChild(TraceNode().addChild("server/java/2/A (message)").addChild(e3).addChild("server/java/2/A (reply)"))
        .addChild(TraceNode().addChild("server/java/2/B (message)").addChild(e3).addChild("server/java/2/B (reply)")).setStrict(false);
    TraceNode expect = TraceNode()
        .addChild(TraceNode().addChild("server/cpp/1/A (message)").addChild(e2).addChild("server/cpp/1/A (reply)"))
        .addChild(TraceNode().addChild("server/java/1/A (message)").addChild(e2).addChild("server/java/1/A (reply)")).setStrict(false);
    expect.normalize();

    Receptor src;
    Reply::UP reply;
    SourceSession::UP ss = mb.getMessageBus().createSourceSession(src, SourceSessionParams());
    for (int i = 0; i < 50; ++i) {
        auto msg = std::make_unique<SimpleMessage>("test");
        msg->getTrace().setLevel(1);
        ss->send(std::move(msg), "test");
        reply = src.getReply(10s);
        if (reply) {
            reply->getTrace().normalize();
            // resending breaks the trace, so retry until it has expected form
            if (!reply->hasErrors() && reply->getTrace().encode() == expect.encode()) {
                break;
            }
        }
        std::cout << "Attempt " << i << " got errors, retrying in 1 second.." << std::endl;
        std::this_thread::sleep_for(1s);
    }

    EXPECT_TRUE(!reply->hasErrors());
    EXPECT_EQUAL(reply->getTrace().encode(), expect.encode());
    EXPECT_TRUE(system((ctl_script + " stop all").c_str()) == 0);
    TEST_DONE();
}
