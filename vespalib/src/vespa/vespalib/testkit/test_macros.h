// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "test_master.h"
#include "test_comparators.h"

#define TEST_STR(str) #str
#define TEST_CAT_IMPL(a, b) a ## b
#define TEST_CAT(a, b) TEST_CAT_IMPL(a, b)
#define TEST_MASTER vespalib::TestMaster::master
#define TEST_PATH(local_file) TEST_MASTER.get_path(local_file)
#define TEST_DEBUG(lhsFile, rhsFile) TEST_MASTER.openDebugFiles(lhsFile, rhsFile)
#define TEST_STATE(msg) vespalib::TestStateGuard TEST_CAT(testStateGuard, __LINE__) (__FILE__, __LINE__, msg)
#define TEST_DO(doit) do { TEST_STATE(TEST_STR(doit)); doit; } while(false)
#define TEST_FLUSH() TEST_MASTER.flush(__FILE__, __LINE__)
#define TEST_TRACE() TEST_MASTER.trace(__FILE__, __LINE__)
#define TEST_THREAD(name) TEST_MASTER.setThreadName(name)
#define TEST_BARRIER() TEST_MASTER.awaitThreadBarrier(__FILE__, __LINE__)
#define TEST_MAIN()                        \
  void test_kit_main();                    \
  int main(int, char **)                   \
  {                                        \
      TEST_MASTER.init(__FILE__);          \
      test_kit_main();                     \
      return (TEST_MASTER.fini() ? 0 : 1); \
  }                                        \
  void test_kit_main()

//-----------------------------------------------------------------------------
#include "generated_fixture_macros.h"
//-----------------------------------------------------------------------------

#define TEST_RUN_ALL() vespalib::TestHook::runAll()

#define TEST_EXCEPTION_IMPL(statement, exception_type, msg_substr, fatal)      \
    try {                                                                      \
        statement;                                                             \
        TEST_MASTER.check(false, __FILE__, __LINE__,                           \
                          #statement " didn't throw " #exception_type, fatal); \
    } catch (exception_type &e) {                                              \
        if (!TEST_MASTER.check(std::string(e.what()).find(msg_substr)          \
                               != std::string::npos, __FILE__, __LINE__,       \
                               (#msg_substr " should be a substring of \"" +   \
                                       std::string(e.what()) + "\"").c_str(),  \
                               fatal)) throw;                                  \
    } catch (...) {                                                            \
        TEST_MASTER.check(false, __FILE__, __LINE__,                           \
                          #statement " threw an unexpected exception", fatal); \
        throw;                                                                 \
    }

#define EXPECT_TRUE(rc) TEST_MASTER.check(bool(rc), __FILE__, __LINE__, TEST_STR(rc), false)
#define EXPECT_FALSE(rc) TEST_MASTER.check(!(bool(rc)), __FILE__, __LINE__, TEST_STR(rc), false)
#define EXPECT_APPROX(a, b, eps) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " ~= ", a, b, vespalib::TestComparators::approx(eps), false)
#define EXPECT_NOT_APPROX(a, b, eps) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " !~= ", a, b, vespalib::TestComparators::not_approx(eps), false)
#define EXPECT_EQUAL(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " == ", a, b, vespalib::TestComparators::equal(), false)
#define EXPECT_NOT_EQUAL(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " != ", a, b, vespalib::TestComparators::not_equal(), false)
#define EXPECT_LESS(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " < ", a, b, vespalib::TestComparators::less(), false)
#define EXPECT_LESS_EQUAL(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " <= ", a, b, vespalib::TestComparators::less_equal(), false)
#define EXPECT_GREATER(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " > ", a, b, vespalib::TestComparators::greater(), false)
#define EXPECT_GREATER_EQUAL(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " >= ", a, b, vespalib::TestComparators::greater_equal(), false)
#define EXPECT_EXCEPTION(statement, exception_type, msg_substr) TEST_EXCEPTION_IMPL(statement, exception_type, msg_substr, false)

#define TEST_ERROR(msg) TEST_MASTER.check(false, __FILE__, __LINE__, msg, false)


#define ASSERT_TRUE(rc) TEST_MASTER.check(bool(rc), __FILE__, __LINE__, TEST_STR(rc), true)
#define ASSERT_FALSE(rc) TEST_MASTER.check(!(bool(rc)), __FILE__, __LINE__, TEST_STR(rc), true)
#define ASSERT_APPROX(a, b, eps) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " ~= ", a, b, vespalib::TestComparators::approx(eps), true)
#define ASSERT_NOT_APPROX(a, b, eps) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " !~= ", a, b, vespalib::TestComparators::not_approx(eps), true)
#define ASSERT_EQUAL(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " == ", a, b, vespalib::TestComparators::equal(), true)
#define ASSERT_NOT_EQUAL(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " != ", a, b, vespalib::TestComparators::not_equal(), true)
#define ASSERT_LESS(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " < ", a, b, vespalib::TestComparators::less(), true)
#define ASSERT_LESS_EQUAL(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " <= ", a, b, vespalib::TestComparators::less_equal(), true)
#define ASSERT_GREATER(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " > ", a, b, vespalib::TestComparators::greater(), true)
#define ASSERT_GREATER_EQUAL(a, b) TEST_MASTER.compare(__FILE__, __LINE__, TEST_STR(a), TEST_STR(b), " >= ", a, b, vespalib::TestComparators::greater_equal(), true)
#define ASSERT_EXCEPTION(statement, exception_type, msg_substr) TEST_EXCEPTION_IMPL(statement, exception_type, msg_substr, true)

#define TEST_FATAL(msg) TEST_MASTER.check(false, __FILE__, __LINE__, msg, true)

