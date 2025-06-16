// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/data/slime/slime.h>

using namespace vespalib::slime::convenience;
using vespalib::slime::ExternalMemory;

struct MyMem : ExternalMemory {
    const std::vector<char> space;
    MyMem(Memory memory)
        : space(memory.data, memory.data + memory.size) {}
    Memory get() const override {
        return Memory(&space[0], space.size());
    }
    static UP create(Memory memory) {
        return std::make_unique<MyMem>(memory);
    }
};

void verify_data(const Inspector &pos, Memory expect) {
    EXPECT_TRUE(pos.valid());
    EXPECT_EQ(vespalib::slime::DATA::ID, pos.type().getId());
    EXPECT_EQ(pos.asString(), Memory());
    EXPECT_EQ(pos.asData(), expect);
}

TEST(ExternalDataTest, require_that_external_memory_can_be_used_for_data_values) {
    Slime slime;
    GTEST_DO(verify_data(slime.setData(MyMem::create("foo")), Memory("foo")));
    GTEST_DO(verify_data(slime.get(), Memory("foo")));
}

TEST(ExternalDataTest, require_that_nullptr_external_memory_gives_empty_data_value) {
    Slime slime;
    GTEST_DO(verify_data(slime.setData(ExternalMemory::UP(nullptr)), Memory("")));
    GTEST_DO(verify_data(slime.get(), Memory("")));
}

TEST(ExternalDataTest, require_that_external_memory_can_be_used_with_array_data_values) {
    Slime slime;
    GTEST_DO(verify_data(slime.setArray().addData(MyMem::create("foo")), Memory("foo")));
    GTEST_DO(verify_data(slime.get()[0], Memory("foo")));
}

TEST(ExternalDataTest, require_that_external_memory_can_be_used_with_object_data_values__name) {
    Slime slime;
    GTEST_DO(verify_data(slime.setObject().setData("field", MyMem::create("foo")), Memory("foo")));
    GTEST_DO(verify_data(slime.get()["field"], Memory("foo")));
}

TEST(ExternalDataTest, require_that_external_memory_can_be_used_with_object_data_values__symbol) {
    Slime slime;
    GTEST_DO(verify_data(slime.setObject().setData(Symbol(5), MyMem::create("foo")), Memory("foo")));
    GTEST_DO(verify_data(slime.get()[Symbol(5)], Memory("foo")));
}

TEST(ExternalDataTest, require_that_external_memory_can_be_used_with_slime_inserter) {
    Slime slime;
    SlimeInserter inserter(slime);
    GTEST_DO(verify_data(inserter.insertData(MyMem::create("foo")), Memory("foo")));
    GTEST_DO(verify_data(slime.get(), Memory("foo")));
}

TEST(ExternalDataTest, require_that_external_memory_can_be_used_with_array_inserter) {
    Slime slime;
    ArrayInserter inserter(slime.setArray());
    GTEST_DO(verify_data(inserter.insertData(MyMem::create("foo")), Memory("foo")));
    GTEST_DO(verify_data(slime.get()[0], Memory("foo")));
}

TEST(ExternalDataTest, require_that_external_memory_can_be_used_with_object_inserter) {
    Slime slime;
    ObjectInserter inserter(slime.setObject(), "field");
    GTEST_DO(verify_data(inserter.insertData(MyMem::create("foo")), Memory("foo")));
    GTEST_DO(verify_data(slime.get()["field"], Memory("foo")));
}

TEST(ExternalDataTest, require_that_external_memory_can_be_used_with_object_symbol_inserter) {
    Slime slime;
    ObjectSymbolInserter inserter(slime.setObject(), Symbol(5));
    GTEST_DO(verify_data(inserter.insertData(MyMem::create("foo")), Memory("foo")));
    GTEST_DO(verify_data(slime.get()[Symbol(5)], Memory("foo")));
}

GTEST_MAIN_RUN_ALL_TESTS()
