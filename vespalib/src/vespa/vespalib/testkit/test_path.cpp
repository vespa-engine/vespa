// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "test_path.h"
#include <cstdlib>

namespace vespalib::testkit {

namespace {

std::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}

std::string path_prefix = get_source_dir() + "/";

}

std::string
test_path(const std::string &local_file)
{
    return path_prefix + local_file;
}

}
