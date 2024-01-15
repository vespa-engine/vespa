// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

#define TEST_PATH(local_file) vespalib::testkit::test_path(local_file)

namespace vespalib::testkit {

/*
 * Return the path to a test file.
 */
std::string test_path(const std::string& local_file);

}
