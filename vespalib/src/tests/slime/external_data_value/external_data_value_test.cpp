// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
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
    EXPECT_EQUAL(vespalib::slime::DATA::ID, pos.type().getId());
    EXPECT_EQUAL(pos.asString(), Memory());
    EXPECT_EQUAL(pos.asData(), expect);
}

TEST("require that external memory can be used for data values") {
    Slime slime;
    TEST_DO(verify_data(slime.setData(MyMem::create("foo")), Memory("foo")));
    TEST_DO(verify_data(slime.get(), Memory("foo")));
}

TEST("require that nullptr external memory gives empty data value") {
    Slime slime;
    TEST_DO(verify_data(slime.setData(ExternalMemory::UP(nullptr)), Memory("")));
    TEST_DO(verify_data(slime.get(), Memory("")));
}

TEST("require that external memory can be used with array data values") {
    Slime slime;
    TEST_DO(verify_data(slime.setArray().addData(MyMem::create("foo")), Memory("foo")));
    TEST_DO(verify_data(slime.get()[0], Memory("foo")));
}

TEST("require that external memory can be used with object data values (name)") {
    Slime slime;
    TEST_DO(verify_data(slime.setObject().setData("field", MyMem::create("foo")), Memory("foo")));
    TEST_DO(verify_data(slime.get()["field"], Memory("foo")));
}

TEST("require that external memory can be used with object data values (symbol)") {
    Slime slime;
    TEST_DO(verify_data(slime.setObject().setData(Symbol(5), MyMem::create("foo")), Memory("foo")));
    TEST_DO(verify_data(slime.get()[Symbol(5)], Memory("foo")));
}

TEST("require that external memory can be used with slime inserter") {
    Slime slime;
    SlimeInserter inserter(slime);
    TEST_DO(verify_data(inserter.insertData(MyMem::create("foo")), Memory("foo")));
    TEST_DO(verify_data(slime.get(), Memory("foo")));
}

TEST("require that external memory can be used with array inserter") {
    Slime slime;
    ArrayInserter inserter(slime.setArray());
    TEST_DO(verify_data(inserter.insertData(MyMem::create("foo")), Memory("foo")));
    TEST_DO(verify_data(slime.get()[0], Memory("foo")));
}

TEST("require that external memory can be used with object inserter") {
    Slime slime;
    ObjectInserter inserter(slime.setObject(), "field");
    TEST_DO(verify_data(inserter.insertData(MyMem::create("foo")), Memory("foo")));
    TEST_DO(verify_data(slime.get()["field"], Memory("foo")));
}

TEST("require that external memory can be used with object symbol inserter") {
    Slime slime;
    ObjectSymbolInserter inserter(slime.setObject(), Symbol(5));
    TEST_DO(verify_data(inserter.insertData(MyMem::create("foo")), Memory("foo")));
    TEST_DO(verify_data(slime.get()[Symbol(5)], Memory("foo")));
}

TEST_MAIN() { TEST_RUN_ALL(); }
