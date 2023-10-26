// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("speed_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace mbus;
using vespalib::make_string;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("speed_test");
    Slobrok slobrok;

    const std::string routing_template = TEST_PATH("routing-template.cfg");
    const std::string ctl_script = TEST_PATH("ctl.sh");
    
    { // Make slobrok config
        EXPECT_EQUAL(system("echo slobrok[1] > slobrok.cfg"), 0);
        EXPECT_EQUAL(system(make_string("echo 'slobrok[0].connectionspec tcp/localhost:%d' "
                                      ">> slobrok.cfg", slobrok.port()).c_str()), 0);
    }
    { // CPP SERVER
        { // Make routing config
            EXPECT_EQUAL(system(("cat " + routing_template + " | sed 's#session#cpp/session#' > routing.cfg").c_str()), 0);
        }
        fprintf(stderr, "STARTING CPP-SERVER\n");
        EXPECT_EQUAL(system((ctl_script + " start server cpp").c_str()), 0);
        fprintf(stderr, "STARTING CPP-CLIENT\n");
        EXPECT_EQUAL(system("./messagebus_test_cpp-client-speed_app"), 0);
        fprintf(stderr, "STARTING JAVA-CLIENT\n");
        EXPECT_EQUAL(system("../../binref/runjava JavaClient"), 0);
        fprintf(stderr, "STOPPING\n");
        EXPECT_EQUAL(system((ctl_script + " stop server cpp").c_str()), 0);
    }
    { // JAVA SERVER
        { // Make routing config
            EXPECT_EQUAL(system(("cat " + routing_template + " | sed 's#session#java/session#' > routing.cfg").c_str()), 0);
        }
        fprintf(stderr, "STARTING JAVA-SERVER\n");
        EXPECT_EQUAL(system((ctl_script + " start server java").c_str()), 0);
        fprintf(stderr, "STARTING CPP-CLIENT\n");
        EXPECT_EQUAL(system("./messagebus_test_cpp-client-speed_app"), 0);
        fprintf(stderr, "STARTING JAVA-CLIENT\n");
        EXPECT_EQUAL(system("../../binref/runjava JavaClient"), 0);
        fprintf(stderr, "STOPPING\n");
        EXPECT_EQUAL(system((ctl_script + " stop server java").c_str()), 0);
    }
    TEST_DONE();
}
