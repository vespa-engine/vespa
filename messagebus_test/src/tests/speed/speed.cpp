// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("speed_test");
#include <vespa/vespalib/testkit/testapp.h>
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
    { // Make slobrok config
        EXPECT_EQUAL(system("echo slobrok[1] > slobrok.cfg"), 0);
        EXPECT_EQUAL(system(make_string("echo 'slobrok[0].connectionspec tcp/localhost:%d' "
                                      ">> slobrok.cfg", slobrok.port()).c_str()), 0);
    }
    { // CPP SERVER
        { // Make routing config
            EXPECT_EQUAL(system("cat routing-template.cfg | sed 's#session#cpp/session#' > routing.cfg"), 0);
        }
        fprintf(stderr, "STARTING CPP-SERVER\n");
        EXPECT_EQUAL(system("sh ctl.sh start server cpp"), 0);
        fprintf(stderr, "STARTING CPP-CLIENT\n");
        EXPECT_EQUAL(system("./messagebus_test_cpp-client-speed_app"), 0);
        fprintf(stderr, "STARTING JAVA-CLIENT\n");
        EXPECT_EQUAL(system("../../binref/runjava JavaClient"), 0);
        fprintf(stderr, "STOPPING\n");
        EXPECT_EQUAL(system("sh ctl.sh stop server cpp"), 0);
    }
    { // JAVA SERVER
        { // Make routing config
            EXPECT_EQUAL(system("cat routing-template.cfg | sed 's#session#java/session#' > routing.cfg"), 0);
        }
        fprintf(stderr, "STARTING JAVA-SERVER\n");
        EXPECT_EQUAL(system("sh ctl.sh start server java"), 0);
        fprintf(stderr, "STARTING CPP-CLIENT\n");
        EXPECT_EQUAL(system("./messagebus_test_cpp-client-speed_app"), 0);
        fprintf(stderr, "STARTING JAVA-CLIENT\n");
        EXPECT_EQUAL(system("../../binref/runjava JavaClient"), 0);
        fprintf(stderr, "STOPPING\n");
        EXPECT_EQUAL(system("sh ctl.sh stop server java"), 0);
    }
    TEST_DONE();
}
