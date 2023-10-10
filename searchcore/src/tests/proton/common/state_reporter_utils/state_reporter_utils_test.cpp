// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("state_reporter_utils_test");

#include <vespa/searchcore/proton/common/state_reporter_utils.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace proton;
using namespace vespalib::slime;
using vespalib::Slime;

vespalib::string
toString(const StatusReport &statusReport)
{
    Slime slime;
    StateReporterUtils::convertToSlime(statusReport, SlimeInserter(slime));
    return slime.toString();
}

TEST("require that simple status report is correctly converted to slime")
{
    EXPECT_EQUAL(
            "{\n"
            "    \"state\": \"ONLINE\"\n"
            "}\n",
            toString(StatusReport(StatusReport::Params("").
                    internalState("ONLINE"))));
}

TEST("require that advanced status report is correctly converted to slime")
{
    EXPECT_EQUAL(
            "{\n"
            "    \"state\": \"REPLAY\",\n"
            "    \"progress\": 65.5,\n"
            "    \"configState\": \"OK\",\n"
            "    \"message\": \"foo\"\n"
            "}\n",
            toString(StatusReport(StatusReport::Params("").
                    internalState("REPLAY").
                    progress(65.5).
                    internalConfigState("OK").
                    message("foo"))));
}

TEST_MAIN() { TEST_RUN_ALL(); }
