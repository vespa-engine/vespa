// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/small_vector.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

template <typename uint32_t, size_t N>
void verify(const SmallVector<uint32_t,N> &vec, std::vector<uint32_t> expect, size_t expect_capacity = 0) {
    if (expect_capacity == 0) {
        expect_capacity = (expect.size() <= N) ? N : roundUp2inN(expect.size());
    }
    ASSERT_EQ(vec.size(), expect.size());
    EXPECT_EQ((vec.size() == 0), vec.empty());
    EXPECT_EQ(vec.capacity(), expect_capacity);
    EXPECT_EQ((vec.capacity() <= N), vec.is_local());
    auto pos = vec.begin();
    auto end = vec.end();
    for (size_t i = 0; i < vec.size(); ++i) {
        EXPECT_EQ(vec[i], expect[i]);
        ASSERT_TRUE(pos != end);
        EXPECT_EQ(*pos, expect[i]);
        ++pos;
    }
    EXPECT_EQ(pos, end);
}

TEST(SmallVectorTest, basic_usage) {
    SmallVector<uint32_t,4> vec;
    EXPECT_EQ(sizeof(vec), 32);
    EXPECT_EQ(vec.capacity(), 4);
    verify(vec, {});
    vec.emplace_back(3);
    verify(vec, {3});
    vec.emplace_back(5);
    verify(vec, {3,5});
    vec.emplace_back(7);
    verify(vec, {3,5,7});
    vec.emplace_back(11);
    verify(vec, {3,5,7,11});
    vec.emplace_back(13);
    verify(vec, {3,5,7,11,13});
    vec.emplace_back(17);
    verify(vec, {3,5,7,11,13,17});
    vec.clear();
    verify(vec, {}, 8);
}

// not 2^n size struct
struct MyStruct {
    uint32_t a;
    uint32_t b;
    uint32_t c;
};

TEST(SmallVectorTest, reserve) {
    SmallVector<uint32_t,4> vec1;
    SmallVector<MyStruct,4> vec2;
    EXPECT_EQ(vec1.capacity(), 4);
    EXPECT_EQ(vec2.capacity(), 4);
    vec1.reserve(3);
    vec2.reserve(3);
    EXPECT_EQ(vec1.capacity(), 4);
    EXPECT_EQ(vec2.capacity(), 4);
    vec1.reserve(6);
    vec2.reserve(6);
    EXPECT_EQ(vec1.capacity(), 8);
    EXPECT_EQ(vec2.capacity(), 10);
}

TEST(SmallVectorTest, copy_and_assign) {
    SmallVector<uint32_t,4> vec1;
    vec1.add(3).add(5).add(7).add(11);
    SmallVector<uint32_t,4> vec2(vec1);
    SmallVector<uint32_t,4> vec3;
    for (size_t i = 0; i < 64; ++i) {
        vec3.add(123);
    }
    vec3 = vec2;
    verify(vec1, {3,5,7,11});
    verify(vec2, {3,5,7,11});
    verify(vec3, {3,5,7,11}, 64);
}

TEST(SmallVectorTest, unique_pointers_resize_and_move) {
    SmallVector<std::unique_ptr<uint32_t>,4> vec1;
    for (size_t i = 0; i < 128; ++i) {
        vec1.emplace_back(std::make_unique<uint32_t>(i));
    }
    ASSERT_EQ(vec1.size(), 128);
    SmallVector<std::unique_ptr<uint32_t>,4> vec2(std::move(vec1));
    ASSERT_EQ(vec2.size(), 128);
    SmallVector<std::unique_ptr<uint32_t>,4> vec3;
    for (size_t i = 0; i < 256; ++i) {
        vec3.emplace_back(std::make_unique<uint32_t>(i));
    }
    ASSERT_EQ(vec3.size(), 256);
    vec3 = std::move(vec2);
    ASSERT_EQ(vec3.size(), 128);
    auto pos = vec3.begin();
    auto end = vec3.end();
    for (size_t i = 0; i < 128; ++i) {
        EXPECT_EQ(*vec3[i], i);
        ASSERT_TRUE(pos != end);
        EXPECT_EQ(**pos, i);
        ++pos;
    }
    EXPECT_EQ(pos, end);
}

TEST(SmallVectorTest, inplace_edit) {
    SmallVector<uint32_t,4> vec;
    vec.add(3).add(5).add(7).add(11);
    verify(vec, {3,5,7,11});
    for (auto &x: vec) {
        x += 1;
    }
    verify(vec, {4,6,8,12});
    vec[1] = 10;
    vec[3] = 20;
    verify(vec, {4,10,8,20});
}

GTEST_MAIN_RUN_ALL_TESTS()
