// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "test_data_base.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <filesystem>

namespace vespalib::test {

// Utility class for accessing test data used by unit tests.
template <class Derived>
class TestData : public TestDataBase {
protected:
    static std::string _source_testdata;
    static std::string _build_testdata;
public:
    static void setup_test_data(const std::string& source_testdata_in, const std::string& build_testdata_in);
    static void tear_down_test_data();
    static const std::string& source_testdata() noexcept { return _source_testdata; }
    static const std::string& build_testdata() noexcept { return _build_testdata; }
    void remove_unchanged_build_testdata_file_or_fail(const nbostream& buf, const std::string& file_name);
};

template <class Derived>
std::string TestData<Derived>::_source_testdata;

template <class Derived>
std::string TestData<Derived>::_build_testdata;

template <class Derived>
void
TestData<Derived>::setup_test_data(const std::string& source_testdata_in, const std::string& build_testdata_in)
{
    _source_testdata = source_testdata_in;
    _build_testdata = build_testdata_in;
    std::filesystem::create_directory(build_testdata());
}

template <class Derived>
void
TestData<Derived>::tear_down_test_data()
{
    std::filesystem::remove(build_testdata());
}

template <class Derived>
void
TestData<Derived>::remove_unchanged_build_testdata_file_or_fail(const nbostream& buf, const std::string& file_name)
{
    auto act_path = build_testdata() + "/" + file_name;
    auto exp_path = source_testdata() + "/" + file_name;
    ASSERT_TRUE(std::filesystem::exists(exp_path)) << "Missing expected contents file " << exp_path;
    auto exp_buf = read_buffer_from_file(exp_path);
    ASSERT_TRUE(equiv_buffers(exp_buf, buf)) << "Files " << exp_path << " and  " << act_path <<
                                             " have diferent contents";
    std::filesystem::remove(act_path);
}

}
