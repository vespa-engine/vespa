// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/small_vector.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vector>
#include <map>

using namespace vespalib;

template <typename T, size_t N>
void verify(const SmallVector<T,N> &vec, std::vector<uint32_t> expect, size_t expect_capacity = 0) {
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

template <typename T, size_t N, size_t M>
void verify_eq(const SmallVector<T,N> &a, const SmallVector<T,M> &b) {
    EXPECT_TRUE(a == b);
    EXPECT_TRUE(b == a);
}

template <typename T, size_t N, size_t M>
void verify_not_eq(const SmallVector<T,N> &a, const SmallVector<T,M> &b) {
    EXPECT_FALSE(a == b);
    EXPECT_FALSE(b == a);
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

struct MyUInt32 {
    uint32_t value = 42;
    operator uint32_t() const { return value; }
};

TEST(SmallVectorTest, create_with_default_elements) {
    SmallVector<uint32_t,4> vec1(2);
    SmallVector<uint32_t,4> vec2(6);
    SmallVector<MyUInt32,4> vec3(2);
    SmallVector<MyUInt32,4> vec4(6);
    verify(vec1, {0, 0});
    verify(vec2, {0, 0, 0, 0, 0, 0});
    verify(vec3, {42, 42});
    verify(vec4, {42, 42, 42, 42, 42, 42});
}

TEST(SmallVectorTest, create_with_copied_elements) {
    SmallVector<uint32_t,4> vec1(2, 5);
    SmallVector<uint32_t,4> vec2(6, 5);
    SmallVector<MyUInt32,4> vec3(2, MyUInt32{5});
    SmallVector<MyUInt32,4> vec4(6, MyUInt32{5});
    verify(vec1, {5, 5});
    verify(vec2, {5, 5, 5, 5, 5, 5});
    verify(vec3, {5, 5});
    verify(vec4, {5, 5, 5, 5, 5, 5});
}

TEST(SmallVectorTest, create_with_unique_pointers) {
    SmallVector<std::unique_ptr<uint32_t>,2> vec1(1);
    SmallVector<std::unique_ptr<uint32_t>,2> vec2(3);
    EXPECT_EQ(vec1.capacity(), 2);
    EXPECT_EQ(vec2.capacity(), 4);
    ASSERT_EQ(vec1.size(), 1);
    ASSERT_EQ(vec2.size(), 3);
    EXPECT_TRUE(vec1[0].get() == nullptr);
    EXPECT_TRUE(vec2[0].get() == nullptr);
    EXPECT_TRUE(vec2[1].get() == nullptr);
    EXPECT_TRUE(vec2[2].get() == nullptr);
}

TEST(SmallVectorTest, create_with_initializer_list) {
    SmallVector<uint32_t,4> vec1({1, 2});
    SmallVector<uint32_t,4> vec2({3, 4, 5, 6, 7, 8});
    verify(vec1, {1, 2});
    verify(vec2, {3, 4, 5, 6, 7, 8});
}

TEST(SmallVectorTest, create_with_pointer_range) {
    SmallVector<uint32_t,4> vec1({1, 2});
    SmallVector<uint32_t,4> vec2({3, 4, 5, 6, 7, 8});
    SmallVector<uint32_t,4> vec3(&vec1[0], &vec1[0] + vec1.size());
    SmallVector<uint32_t,4> vec4(&vec2[0], &vec2[0] + vec2.size());
    verify(vec3, {1, 2});
    verify(vec4, {3, 4, 5, 6, 7, 8});
}

TEST(SmallVectorTest, create_with_random_access_iterator) {
    std::vector<uint32_t> vec1({1, 2});
    std::vector<uint32_t> vec2({3, 4, 5, 6, 7, 8});
    SmallVector<uint32_t,4> vec3(vec1.begin(), vec1.end());
    SmallVector<uint32_t,4> vec4(vec2.begin(), vec2.end());
    verify(vec3, {1, 2});
    verify(vec4, {3, 4, 5, 6, 7, 8});
}

TEST(SmallVectorTest, create_with_akward_input_iterator_and_value_type) {
    std::map<uint32_t,uint32_t> map;
    map[1] = 2;
    map[3] = 4;
    map[5] = 6;
    SmallVector<std::pair<const uint32_t, uint32_t>,2> vec(map.begin(), map.end());
    ASSERT_EQ(vec.size(), 3);
    EXPECT_EQ(vec[0].first, 1);
    EXPECT_EQ(vec[0].second, 2);
    EXPECT_EQ(vec[1].first, 3);
    EXPECT_EQ(vec[1].second, 4);
    EXPECT_EQ(vec[2].first, 5);
    EXPECT_EQ(vec[2].second, 6);
}

TEST(SmallVectorTest, auto_select_N) {
    SmallVector<uint32_t> vec1;
    SmallVector<uint64_t> vec2;
    SmallVector<MyStruct> vec3;
    EXPECT_EQ(sizeof(vec1), 64);
    EXPECT_EQ(sizeof(vec2), 64);
    EXPECT_EQ(sizeof(vec3), 64);
    EXPECT_EQ(vec1.capacity(), 12);
    EXPECT_EQ(vec2.capacity(), 6);
    EXPECT_EQ(vec3.capacity(), 4);
}

struct EqOnly {
    int value;
    bool operator==(const EqOnly &rhs) const { return (value == rhs.value); }
};

TEST(SmallVectorTest, equal_operator) {
    verify_eq(SmallVector<int,2>(), SmallVector<int,8>());
    verify_eq(SmallVector<int,2>({1,2,3}), SmallVector<int,8>({1,2,3}));
    verify_eq(SmallVector<EqOnly>({EqOnly{1},EqOnly{2},EqOnly{3}}),
              SmallVector<EqOnly>({EqOnly{1},EqOnly{2},EqOnly{3}}));
    verify_not_eq(SmallVector<EqOnly>({EqOnly{1},EqOnly{2},EqOnly{3}}),
                  SmallVector<EqOnly>({EqOnly{1},EqOnly{2}}));
    verify_not_eq(SmallVector<EqOnly>({EqOnly{1},EqOnly{2},EqOnly{3}}),
                  SmallVector<EqOnly>({EqOnly{1},EqOnly{5},EqOnly{3}}));
}

// to check "back() const"
template<size_t N>
int last_value_of(const SmallVector<int,N> &v) {
    return v.back();
}

TEST(SmallVectorTest, check_back_method) {
    SmallVector<int> vec;
    for (int i = 0; i < 1000; ++i) {
        vec.emplace_back(17);
        EXPECT_EQ(vec.back(), 17);
        EXPECT_EQ(last_value_of(vec), 17);
        vec.back() = 42;
        EXPECT_EQ(vec[i], 42);
        vec.back() = i;
        EXPECT_EQ(last_value_of(vec), i);
    }
    EXPECT_EQ(&vec.back(), vec.end() - 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
