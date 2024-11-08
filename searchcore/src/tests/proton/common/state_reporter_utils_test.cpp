// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/state_reporter_utils.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace proton;
using namespace vespalib::slime;
using vespalib::Slime;

namespace {

std::string
toString(const StatusReport &statusReport)
{
    Slime slime;
    StateReporterUtils::convertToSlime(statusReport, SlimeInserter(slime));
    return slime.toString();
}

}

TEST(StateReporterUtilsTest, require_that_simple_status_report_is_correctly_converted_to_slime)
{
    EXPECT_EQ(
            "{\n"
            "    \"state\": \"ONLINE\"\n"
            "}\n",
            toString(StatusReport(StatusReport::Params("").
                    internalState("ONLINE"))));
}

TEST(StateReporterUtilsTest, require_that_advanced_status_report_is_correctly_converted_to_slime)
{
    EXPECT_EQ(
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

