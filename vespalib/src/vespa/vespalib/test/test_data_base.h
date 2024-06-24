// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string_view>

namespace vespalib { class nbostream; }

namespace vespalib::test {

// Utility base class for accessing test data used by unit tests.
class TestDataBase {
public:
    static bool equiv_buffers(const nbostream& lhs, const nbostream& rhs);
    static nbostream read_buffer_from_file(const std::string& path);
    static void write_buffer_to_file(const nbostream& buf, const std::string& path);
    static void write_buffer_to_file(std::string_view buf, const std::string& path);
};

}
