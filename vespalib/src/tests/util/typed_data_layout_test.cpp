// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/typed_data_layout.h>

#include <string>
#include <type_traits>
#include <vector>

using namespace vespalib::tdl;

namespace {

struct MyObj {
    int               value;
    std::vector<int>* ext_list = nullptr;
    static int        live_cnt;
    MyObj() : value(++live_cnt) {}
    ~MyObj() {
        --live_cnt;
        if (ext_list != nullptr) {
            ext_list->push_back(value);
        }
    }
};
int MyObj::live_cnt = 0;

struct alignas(32) BigObj {
    int        value;
    static int live_cnt;
    BigObj() : value(++live_cnt) {}
    ~BigObj() { --live_cnt; }
};
int BigObj::live_cnt = 0;

struct Size {
    size_t value;
    template <typename Data> explicit Size(const Data& d) : value(sizeof(d)) {}
    template <typename T> Size& add(size_t n, bool skip_empty = true) {
        if (n > 0 || !skip_empty) {
            value = detail::align_up<alignof(T)>(value);
            value += n * sizeof(T);
        }
        return *this;
    }
};

template <typename T> bool is_aligned(const T* ptr) {
    return (reinterpret_cast<uintptr_t>(ptr) % alignof(T)) == 0;
}

} // namespace

TEST(TypedDataLayoutTest, default_handle_is_invalid) {
    Handle h;
    EXPECT_FALSE(h.valid());
}

TEST(TypedDataLayoutTest, default_array_handle_is_invalid) {
    ArrayHandle ah;
    EXPECT_FALSE(ah.valid());
}

TEST(TypedDataLayoutTest, empty_layout_can_create_data) {
    Layout<Domain<int>> layout;
    auto                data = layout.create_data();
    EXPECT_TRUE(data);
    EXPECT_EQ(data->allocated(), Size(*data).value);
}

TEST(TypedDataLayoutTest, reserve_and_resolve) {
    Layout<Domain<int>> layout;
    Handle              handle = layout.reserve<int>();
    EXPECT_TRUE(handle.valid());

    auto data = layout.create_data();
    EXPECT_EQ(data->allocated(), Size(*data).add<int>(1).value);
    EXPECT_TRUE((std::is_same_v<decltype(data->resolve<int>(handle)), int&>));
    EXPECT_EQ(data->resolve<int>(handle), 0); // value constructed
    data->resolve<int>(handle) = 42;

    const auto& cdata = *data;
    EXPECT_TRUE((std::is_same_v<decltype(cdata.resolve<int>(handle)), const int&>));
    EXPECT_EQ(cdata.resolve<int>(handle), 42);
}

TEST(TypedDataLayoutTest, reserve_and_resolve_array) {
    Layout<Domain<int>> layout;
    ArrayHandle         handle = layout.reserve_array<int>(3);
    EXPECT_TRUE(handle.valid());

    auto data = layout.create_data();
    EXPECT_EQ(data->allocated(), Size(*data).add<int>(3).value);
    EXPECT_TRUE((std::is_same_v<decltype(data->resolve_array<int>(handle)), std::span<int>>));
    auto span = data->resolve_array<int>(handle);
    ASSERT_EQ(span.size(), 3);
    for (size_t i = 0; i < 3; ++i) {
        EXPECT_EQ(span[i], 0); // value constructed
        span[i] = 42 + i;
    }

    const auto& cdata = *data;
    EXPECT_TRUE((std::is_same_v<decltype(cdata.resolve_array<int>(handle)), std::span<const int>>));
    auto cspan = cdata.resolve_array<int>(handle);
    ASSERT_EQ(cspan.size(), 3);
    ASSERT_EQ(handle.size(), 3);
    for (size_t i = 0; i < 3; ++i) {
        EXPECT_EQ(cspan[i], 42 + i);
        EXPECT_EQ(cdata.resolve<int>(handle.at(i)), 42 + i);
    }
}

TEST(TypedDataLayoutTest, object_construction_and_destruction_order) {
    ASSERT_EQ(MyObj::live_cnt, 0);
    std::vector<int> list;
    {
        Layout<Domain<MyObj>> layout;
        auto                  h1 = layout.reserve<MyObj>();
        auto                  h2 = layout.reserve<MyObj>();
        auto                  h3 = layout.reserve<MyObj>();
        auto                  data = layout.create_data();
        EXPECT_EQ(data->allocated(), Size(*data).add<MyObj>(3).value);
        EXPECT_EQ(data->resolve<MyObj>(h1).value, 1);
        data->resolve<MyObj>(h1).ext_list = &list;
        EXPECT_EQ(data->resolve<MyObj>(h2).value, 2);
        data->resolve<MyObj>(h2).ext_list = &list;
        EXPECT_EQ(data->resolve<MyObj>(h3).value, 3);
        data->resolve<MyObj>(h3).ext_list = &list;
        EXPECT_EQ(MyObj::live_cnt, 3);
    }
    EXPECT_EQ(MyObj::live_cnt, 0);
    // Note: objects are destructed in construction order
    std::vector<int> expected = {1, 2, 3};
    EXPECT_EQ(list, expected);
}

TEST(TypedDataLayoutTest, array_handle_iteration) {
    Layout<Domain<int>> layout;
    ArrayHandle         handle = layout.reserve_array<int>(4);
    auto                data = layout.create_data();
    EXPECT_EQ(data->allocated(), Size(*data).add<int>(4).value);
    auto span = data->resolve_array<int>(handle);
    ASSERT_EQ(span.size(), 4);
    for (size_t i = 0; i < 4; ++i) {
        span[i] = 10 + i;
    }
    std::vector<int> values;
    for (Handle h : handle) {
        values.push_back(data->resolve<int>(h));
    }
    EXPECT_EQ(values, (std::vector<int>{10, 11, 12, 13}));
}

TEST(TypedDataLayoutTest, mixed_reserve_and_reserve_array) {
    Layout<Domain<int>> layout;
    Handle              h1 = layout.reserve<int>();
    ArrayHandle         empty = layout.reserve_array<int>(0);
    EXPECT_TRUE(empty.valid());
    EXPECT_TRUE(empty.empty());
    ArrayHandle ah = layout.reserve_array<int>(3);
    Handle      h2 = layout.reserve<int>();
    auto        data = layout.create_data();
    EXPECT_EQ(data->allocated(), Size(*data).add<int>(5).value);
    ASSERT_EQ(data->all_of<int>().size(), 5);
    ASSERT_EQ(data->resolve_array<int>(empty).size(), 0);
    data->resolve<int>(h1) = 1;
    auto span = data->resolve_array<int>(ah);
    ASSERT_EQ(span.size(), 3);
    for (size_t i = 0; i < 3; ++i) {
        span[i] = 10 + i;
    }
    data->resolve<int>(h2) = 2;
    EXPECT_EQ(data->resolve<int>(h1), 1);
    EXPECT_EQ(data->resolve<int>(h2), 2);
    EXPECT_EQ(span[0], 10);
    EXPECT_EQ(span[1], 11);
    EXPECT_EQ(span[2], 12);
}

TEST(TypedDataLayoutTest, multi_reserve_and_resolve) {
    using MyLayout = Layout<Domain<int, double, std::string>>;
    MyLayout            layout;
    std::vector<Handle> ints;
    std::vector<Handle> doubles;
    std::vector<Handle> strings;

    size_t cnt = 32;
    for (size_t i = 0; i < cnt; ++i) {
        {
            Handle h = layout.reserve<int>();
            EXPECT_EQ(h.type(), 0);
            EXPECT_EQ(h.offset(), ints.size());
            ints.push_back(h);
        }
        {
            Handle h = layout.reserve<double>();
            EXPECT_EQ(h.type(), 1);
            EXPECT_EQ(h.offset(), doubles.size());
            doubles.push_back(h);
        }
        {
            Handle h = layout.reserve<std::string>();
            EXPECT_EQ(h.type(), 2);
            EXPECT_EQ(h.offset(), strings.size());
            strings.push_back(h);
        }
    }
    ASSERT_EQ(layout.all_of<int>().size(), cnt);
    ASSERT_EQ(layout.all_of<double>().size(), cnt);
    ASSERT_EQ(layout.all_of<std::string>().size(), cnt);
    for (size_t i = 0; i < cnt; ++i) {
        EXPECT_EQ(ints[i], layout.all_of<int>().at(i));
        EXPECT_EQ(doubles[i], layout.all_of<double>().at(i));
        EXPECT_EQ(strings[i], layout.all_of<std::string>().at(i));
    }

    MyLayout::DataUP data = layout.create_data();
    EXPECT_EQ(data->allocated(), Size(*data).add<int>(cnt).add<double>(cnt).add<std::string>(cnt).value);

    ASSERT_EQ(data->all_of<int>().size(), cnt);
    ASSERT_EQ(data->all_of<double>().size(), cnt);
    ASSERT_EQ(data->all_of<std::string>().size(), cnt);
    for (size_t i = 0; i < cnt; ++i) {
        EXPECT_EQ(&data->resolve<int>(ints[i]), &data->all_of<int>()[i]);
        EXPECT_EQ(&data->resolve<double>(doubles[i]), &data->all_of<double>()[i]);
        EXPECT_EQ(&data->resolve<std::string>(strings[i]), &data->all_of<std::string>()[i]);
    }
}

TEST(TypedDataLayoutTest, base_class) {
    ASSERT_EQ(MyObj::live_cnt, 0);
    {
        Layout<Domain<int, double>, MyObj> layout;
        auto                               hi = layout.reserve<int>();
        auto                               hd = layout.reserve<double>();
        auto                               data = layout.create_data();
        EXPECT_EQ(MyObj::live_cnt, 1);
        EXPECT_EQ(data->allocated(), Size(*data).add<int>(1).add<double>(1).value);
        data->value = 42;
        data->resolve<int>(hi) = 10;
        data->resolve<double>(hd) = 3.14;
        EXPECT_EQ(data->value, 42);
        EXPECT_EQ(data->resolve<int>(hi), 10);
        EXPECT_EQ(data->resolve<double>(hd), 3.14);
    }
    EXPECT_EQ(MyObj::live_cnt, 0);
}

TEST(TypedDataLayoutTest, unused_types_do_not_affect_size) {
    Layout<Domain<char, BigObj, int>> layout;
    layout.reserve_array<char>(4);
    layout.reserve<int>();
    auto data = layout.create_data();
    EXPECT_EQ(Size(*data).add<char>(4).add<BigObj>(0, false).add<int>(1).value, 68);
    EXPECT_EQ(Size(*data).add<char>(4).add<BigObj>(0).add<int>(1).value, 40);
    EXPECT_EQ(data->allocated(), 40);
    EXPECT_EQ(data->all_of<BigObj>().size(), 0);
}

TEST(TypedDataLayoutTest, alignment) {
    ASSERT_EQ(BigObj::live_cnt, 0);
    {
        Layout<Domain<char, int, BigObj>, BigObj> layout;
        auto                                      hc = layout.reserve<char>();
        auto                                      hi = layout.reserve<int>();
        auto                                      hb = layout.reserve<BigObj>();
        auto                                      data = layout.create_data();
        EXPECT_EQ(BigObj::live_cnt, 2);
        EXPECT_EQ(data->allocated(), Size(*data).add<char>(1).add<int>(1).add<BigObj>(1).value);
        EXPECT_TRUE(is_aligned(data.get()));
        EXPECT_TRUE(is_aligned(&data->resolve<char>(hc)));
        EXPECT_TRUE(is_aligned(&data->resolve<int>(hi)));
        EXPECT_TRUE(is_aligned(&data->resolve<BigObj>(hb)));
    }
    EXPECT_EQ(BigObj::live_cnt, 0);
}
