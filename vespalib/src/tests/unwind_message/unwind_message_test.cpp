// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/unwind_message.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <stdexcept>

using vespalib::unwind_msg;
using vespalib::UnwindMessage;
using E = std::invalid_argument;

//-----------------------------------------------------------------------------

struct MyCheck {
    ~MyCheck() {
        EXPECT_EQ(std::uncaught_exceptions(), 2);
    }
};

struct MyObj {
    UnwindMessage msg1 = UnwindMessage("this SHOULD be printed (1/4)");
    UnwindMessage msg2 = UnwindMessage("this should NOT be printed (1)");
    UnwindMessage msg3 = UnwindMessage("this SHOULD be printed (2/4)");
    MyObj();
    ~MyObj();
};

MyObj::MyObj() = default;
MyObj::~MyObj() {
    EXPECT_EQ(std::uncaught_exceptions(), 1);
    auto not_printed_1 = std::move(msg2);
    try {
        MyCheck my_check;
        auto printed_1 = std::move(msg1);
        throw E("next level");
    } catch (const E &) {}
}

TEST(UnwindMessageTest, unwind_messages_are_printed_when_appropriate) {
    auto not_printed_5 = unwind_msg("this should NOT be printed (%d)", 5);
    UNWIND_MSG("this should NOT be printed (%d)", 4);
    EXPECT_THROW(
            {
                EXPECT_EQ(std::uncaught_exceptions(), 0);
                auto printed_4 = unwind_msg("this SHOULD be printed (%d/%d)", 4, 4);
                UNWIND_MSG("this SHOULD be printed (%d/%d)", 3, 4);
                {
                    auto not_printed_3 = unwind_msg("this should NOT be printed (%d)", 3);
                    UNWIND_MSG("this should NOT be printed (%d)", 2);
                }
                MyObj my_obj;
                throw E("just testing");
            }, E);
}

//-----------------------------------------------------------------------------

TEST(UnwindMessageTest, unwind_message_with_location) {
    EXPECT_THROW(
            {
                UNWIND_MSG("%s message with location information", VESPA_STRLOC.c_str());
                throw E("just testing");
            }, E);
}

//-----------------------------------------------------------------------------

void my_bad_call() {
    throw E("just testing");
}

TEST(UnwindMessageTest, unwind_message_from_UNWIND_DO_macro_calling_a_function) {
    EXPECT_THROW(
            {
                UNWIND_DO(my_bad_call());
            }, E);
}

//-----------------------------------------------------------------------------

TEST(UnwindMessageTest, unwind_message_from_UNWIND_DO_macro_with_inline_code) {
    EXPECT_THROW(
            {
                UNWIND_DO(
                        int a = 1;
                        int b = 2;
                        int c = a + b;
                        (void) c;
                        throw E("oops");
                );
            }, E);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
