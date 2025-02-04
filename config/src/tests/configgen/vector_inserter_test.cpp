// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/common/types.h>
#include <vespa/config/configgen/vector_inserter.hpp>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace config;
using namespace config::internal;
using namespace vespalib;
using namespace vespalib::slime;

struct MyType{
    MyType() : foo(0), bar(0) {}
    MyType(const ConfigPayload & payload)
    {
        foo = payload.get()["foo"].asLong();
        bar = payload.get()["bar"].asLong();
    }
    int foo;
    int bar;
};

TEST(VectorInserterTest, require_that_vector_of_ints_can_be_inserted)
{
    std::vector<int32_t> vector;
    Slime slime;
    Cursor & root = slime.setArray();
    root.addLong(3);
    root.addLong(2);
    root.addLong(6);
    VectorInserter inserter(vector);
    root.traverse(inserter);
    ASSERT_EQ(3u, vector.size());
    ASSERT_EQ(3, vector[0]);
    ASSERT_EQ(2, vector[1]);
    ASSERT_EQ(6, vector[2]);
}

TEST(VectorInserterTest, require_that_vector_of_struct_can_be_inserted)
{
    std::vector<MyType> typeVector;
    Slime slime;
    Cursor & root = slime.setArray();
    Cursor & one = root.addObject();
    one.setLong("foo", 3);
    one.setLong("bar", 4);
    Cursor & two = root.addObject();
    two.setLong("foo", 1);
    two.setLong("bar", 6);
    VectorInserter inserter(typeVector);
    root.traverse(inserter);
    ASSERT_EQ(2u, typeVector.size());
    ASSERT_EQ(3, typeVector[0].foo);
    ASSERT_EQ(4, typeVector[0].bar);
    ASSERT_EQ(1, typeVector[1].foo);
    ASSERT_EQ(6, typeVector[1].bar);
}

TEST(VectorInserterTest, require_that_vector_of_long_can_be_inserted)
{
    std::vector<int64_t> vector;
    Slime slime;
    Cursor & root = slime.setArray();
    root.addLong(3);
    root.addLong(2);
    root.addLong(6);
    VectorInserter inserter(vector);
    root.traverse(inserter);
    ASSERT_EQ(3u, vector.size());
    ASSERT_EQ(3, vector[0]);
    ASSERT_EQ(2, vector[1]);
    ASSERT_EQ(6, vector[2]);
}

TEST(VectorInserterTest, require_that_vector_of_double_can_be_inserted)
{
    std::vector<double> vector;
    Slime slime;
    Cursor & root = slime.setArray();
    root.addDouble(3.1);
    root.addDouble(2.4);
    root.addDouble(6.6);
    VectorInserter inserter(vector);
    root.traverse(inserter);
    ASSERT_EQ(3u, vector.size());
    ASSERT_EQ(3.1, vector[0]);
    ASSERT_EQ(2.4, vector[1]);
    ASSERT_EQ(6.6, vector[2]);
}

TEST(VectorInserterTest, require_that_vector_of_bool_can_be_inserted)
{
    std::vector<bool> vector;
    Slime slime;
    Cursor & root = slime.setArray();
    root.addBool(true);
    root.addBool(false);
    root.addBool(true);
    VectorInserter inserter(vector);
    root.traverse(inserter);
    ASSERT_EQ(3u, vector.size());
    ASSERT_TRUE(vector[0]);
    ASSERT_FALSE(vector[1]);
    ASSERT_TRUE(vector[2]);
}

template<typename V>
void
verify_vector_strings_can_be_inserted(V vector) {
    Slime slime;
    Cursor & root = slime.setArray();
    root.addString("foo");
    root.addString("bar");
    root.addString("baz");
    VectorInserter inserter(vector);
    root.traverse(inserter);
    ASSERT_EQ(3u, vector.size());
    ASSERT_EQ("foo", vector[0]);
    ASSERT_EQ("bar", vector[1]);
    ASSERT_EQ("baz", vector[2]);
}

TEST(VectorInserterTest, require_that_different_vectors_of_strings_can_be_inserted)
{
    verify_vector_strings_can_be_inserted(std::vector<std::string>());
    verify_vector_strings_can_be_inserted(StringVector());
}

GTEST_MAIN_RUN_ALL_TESTS()
