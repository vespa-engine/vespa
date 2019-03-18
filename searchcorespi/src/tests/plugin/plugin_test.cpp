// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcorespi/plugin/factoryloader.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace searchcorespi;

namespace {
TEST("require that plugins can be loaded.") {
    FactoryLoader fl;
    IIndexManagerFactory::UP f = fl.create("searchcorespi_tplugin");
    ASSERT_TRUE(f.get());
}

TEST("require that non-existent plugin causes failure") {
    FactoryLoader fl;
#ifdef __APPLE__
    EXPECT_EXCEPTION(fl.create("no-such-plugin"),
                     vespalib::IllegalArgumentException,
                     "image not found");
#else
    EXPECT_EXCEPTION(fl.create("no-such-plugin"),
                     vespalib::IllegalArgumentException,
                     "cannot open shared object file");
#endif
}

TEST("require that missing factory function causes failure") {
    FactoryLoader fl;
    EXPECT_EXCEPTION(fl.create("searchcorespi_illegal-plugin"),
                     vespalib::IllegalArgumentException,
                     "Failed locating symbol 'createIndexManagerFactory'");
}
}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
