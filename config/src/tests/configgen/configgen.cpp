// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/helper/configgetter.hpp>
#include "config-motd.h"

using namespace config;

TEST("require that config type can be compiled") {
    std::unique_ptr<MotdConfig> cfg = ConfigGetter<MotdConfig>::getConfig("motd",
                                          FileSpec(TEST_PATH("motd.cfg")));
}

TEST_MAIN() { TEST_RUN_ALL(); }
