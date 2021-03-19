// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/unwind_message.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <stdexcept>

using vespalib::unwind_msg;
using vespalib::UnwindMessage;

//-----------------------------------------------------------------------------

struct MyObj {
    UnwindMessage msg1 = UnwindMessage("this SHOULD be printed (1/2)");
    UnwindMessage msg2 = UnwindMessage("this should NOT be printed (1)");
    ~MyObj() {
        EXPECT_EQ(std::uncaught_exceptions(), 1);
        auto not_printed_1 = std::move(msg2);
        auto not_printed_2 = unwind_msg("this should NOT be printed (2)");
    }
};

TEST(UnwindMessageTest, unwind_messages_are_printed_when_appropriate) {
    using E = std::invalid_argument;
    auto not_printed_3 = unwind_msg("this should NOT be printed (3)");
    EXPECT_THROW(
            {
                EXPECT_EQ(std::uncaught_exceptions(), 0);
                auto printed = unwind_msg("this SHOULD be printed (2/2)");
                { auto not_printed_4 = unwind_msg("this should NOT be printed (4)"); }
                MyObj my_obj;
                throw E("just testing");
            }, E);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
