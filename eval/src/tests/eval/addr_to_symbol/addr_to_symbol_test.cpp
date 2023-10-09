// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/llvm/addr_to_symbol.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;

TEST(AddrToSymbol, null_ptr) {
    auto sym = addr_to_symbol(nullptr);
    EXPECT_EQ(sym, "<nullptr>");
}

TEST(AddrToSymbol, global_symbol) {
    auto sym = addr_to_symbol((const void *)addr_to_symbol);
    fprintf(stderr, "global symbol: %s\n", sym.c_str());
    EXPECT_TRUE(sym.find("addr_to_symbol") < sym.size());
}

TEST(AddrToSymbol, local_symbol) {
    auto sym = addr_to_symbol(get_addr_of_local_test_symbol());
    fprintf(stderr, "local symbol: %s\n", sym.c_str());
    EXPECT_TRUE(sym.find("my_local_test_symbol") < sym.size());
}

GTEST_MAIN_RUN_ALL_TESTS()
