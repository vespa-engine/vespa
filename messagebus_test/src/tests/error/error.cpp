// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP("error_test");

using namespace mbus;
using vespalib::make_string;


TEST("error_test") {
    Slobrok slobrok;
    const std::string routing_template = TEST_PATH("routing-template.cfg");
    const std::string ctl_script = TEST_PATH("ctl.sh");
    
    { // Make slobrok config
        EXPECT_EQUAL(0, system("echo slobrok[1] > slobrok.cfg"));
        EXPECT_EQUAL(0, system(make_string("echo 'slobrok[0].connectionspec tcp/localhost:%d' "
                                      ">> slobrok.cfg", slobrok.port()).c_str()));
    }
    { // CPP SERVER
        { // Make routing config
            EXPECT_EQUAL(0, system(("cat " + routing_template + " | sed 's#session#cpp/session#' > routing.cfg").c_str()));
        }
        fprintf(stderr, "STARTING CPP-SERVER\n");
        EXPECT_EQUAL(0, system((ctl_script + " start server cpp").c_str()));
        EXPECT_EQUAL(0, system("./messagebus_test_cpp-client-error_app"));
        EXPECT_EQUAL(0, system("../../binref/runjava JavaClient"));
        EXPECT_EQUAL(0, system((ctl_script + " stop server cpp").c_str()));
    }
    { // JAVA SERVER
        { // Make routing config
            EXPECT_EQUAL(0, system(("cat " + routing_template + " | sed 's#session#java/session#' > routing.cfg").c_str()));
        }
        fprintf(stderr, "STARTING JAVA-SERVER\n");
        EXPECT_EQUAL(0, system((ctl_script + " start server java").c_str()));
        EXPECT_EQUAL(0, system("./messagebus_test_cpp-client-error_app"));
        EXPECT_EQUAL(0, system("../../binref/runjava JavaClient"));
        EXPECT_EQUAL(0, system((ctl_script + " stop server java").c_str()));
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
