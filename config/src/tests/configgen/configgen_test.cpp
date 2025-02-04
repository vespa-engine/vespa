// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/helper/configgetter.hpp>
#include "config-motd.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>

using namespace config;

TEST(ConfiggenTest, require_that_config_type_can_be_compiled)
{
    std::unique_ptr<MotdConfig> cfg = ConfigGetter<MotdConfig>::getConfig("motd",
                                          FileSpec(TEST_PATH("motd.cfg")));
}

GTEST_MAIN_RUN_ALL_TESTS()
