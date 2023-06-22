// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/dynamic_array_buffer_type.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <ostream>

using vespalib::datastore::ArrayStoreConfig;
using vespalib::datastore::BufferTypeBase;
using vespalib::datastore::DynamicArrayBufferType;
using vespalib::datastore::EntryCount;

namespace {

struct CleanContextBase
{
    std::atomic<size_t> _extra_used_bytes;
    std::atomic<size_t> _extra_hold_bytes;
    CleanContextBase()
        : _extra_used_bytes(0),
          _extra_hold_bytes(0)
    {
    }
};

struct MyCleanContext : public CleanContextBase,
                        public BufferTypeBase::CleanContext
{
    MyCleanContext()
        : CleanContextBase(),
          BufferTypeBase::CleanContext(_extra_used_bytes, _extra_hold_bytes)
    {
    }
};

struct Counts {
    uint32_t _def_constructs;
    uint32_t _value_constructs;
    uint32_t _copy_constructs;
    uint32_t _destructs;
    uint32_t _assigns;

    Counts(uint32_t def_constructs, uint32_t value_constructs, uint32_t copy_constructs, uint32_t destructs, uint32_t assigns)
        : _def_constructs(def_constructs),
          _value_constructs(value_constructs),
          _copy_constructs(copy_constructs),
          _destructs(destructs),
          _assigns(assigns)
    {
    }

    Counts()
        : Counts(0, 0, 0, 0, 0)
    {
    }
    bool operator==(const Counts &rhs) const {
        return _def_constructs == rhs._def_constructs &&
            _value_constructs == rhs._value_constructs &&
            _copy_constructs == rhs._copy_constructs &&
            _destructs == rhs._destructs &&
            _assigns == rhs._assigns;
    }
};

Counts counts;

std::ostream& operator<<(std::ostream& os, const Counts& c) {
    os << "{def_constructs=" << c._def_constructs <<
        ", value_constructs=" << c._value_constructs <<
        ", copy_constructs=" << c._copy_constructs <<
        ", destructs=" << c._destructs <<
        ", assigns=" << c._assigns << "}";
    return os;
}

struct WrapInt32 {
    int32_t _v;

    WrapInt32()
        : _v(0)
    {
        ++counts._def_constructs;
    }
    WrapInt32(int v)
        : _v(v)
    {
        ++counts._value_constructs;
    }
    WrapInt32(const WrapInt32& rhs)
        : _v(rhs._v)
    {
        ++counts._copy_constructs;
    }
    WrapInt32& operator=(const WrapInt32& rhs) {
        _v = rhs._v;
        ++counts._assigns;
        return *this;
    }
    ~WrapInt32() {
        ++counts._destructs;
    }
};

}

class DynamicArrayBufferTypeTest : public testing::Test
{
protected:
    DynamicArrayBufferTypeTest();
    ~DynamicArrayBufferTypeTest() override;

    using BufferType = DynamicArrayBufferType<WrapInt32>;

    template <typename ElemT>
    uint32_t get_entry_size(uint32_t array_size);

    std::vector<int> get_vector(const void *buffer, uint32_t offset, uint32_t array_size);
    std::vector<int> get_vector(const void *buffer, uint32_t offset);
    std::vector<int> get_max_vector(const void *buffer, uint32_t offset);
    void write_entry1();

    BufferType              _buffer_type;
    size_t                  _entry_size;
    size_t                  _buffer_underflow_size;
    size_t                  _buf_size;
    std::unique_ptr<char[]> _buf_alloc;
    char*                   _buf;
};

DynamicArrayBufferTypeTest::DynamicArrayBufferTypeTest()
    : testing::Test(),
      _buffer_type(3, ArrayStoreConfig::AllocSpec(0, 10, 0, 0.2), {}),
      _entry_size(_buffer_type.entry_size()),
      _buffer_underflow_size(_buffer_type.buffer_underflow_size()),
      _buf_size(2 * _entry_size),
      _buf_alloc(std::make_unique<char[]>(_buf_size + _buffer_underflow_size)),
      _buf(_buf_alloc.get() + _buffer_underflow_size)
{
    // Call initialize_reserved_entries to force construction of empty element
    _buffer_type.initialize_reserved_entries(_buf, 1);
    // Reset counts after empty element has been constructed
    counts = Counts();
}

DynamicArrayBufferTypeTest::~DynamicArrayBufferTypeTest() = default;

template <typename ElemT>
uint32_t
DynamicArrayBufferTypeTest::get_entry_size(uint32_t array_size)
{
    DynamicArrayBufferType<ElemT> my_buffer_type(array_size, ArrayStoreConfig::AllocSpec(0, 10, 0, 0.2), {});
    return my_buffer_type.entry_size();
}

std::vector<int>
DynamicArrayBufferTypeTest::get_vector(const void* buffer, uint32_t offset, uint32_t array_size)
{
    auto e = BufferType::get_entry(buffer, offset, _entry_size);
    std::vector<int> result;
    for (uint32_t i = 0; i < array_size; ++i) {
        result.emplace_back(e[i]._v);
    }
    return result;
}

std::vector<int>
DynamicArrayBufferTypeTest::get_vector(const void* buffer, uint32_t offset)
{
    auto e = BufferType::get_entry(buffer, offset, _entry_size);
    auto array_size = BufferType::get_dynamic_array_size(e);
    EXPECT_GE(_buffer_type.getArraySize(), array_size);
    return get_vector(buffer, offset, array_size);
}

std::vector<int>
DynamicArrayBufferTypeTest::get_max_vector(const void* buffer, uint32_t offset)
{
    auto array_size = _buffer_type.getArraySize();
    return get_vector(buffer, offset, array_size);
}

void
DynamicArrayBufferTypeTest::write_entry1()
{
    auto e1 = BufferType::get_entry(_buf, 1, _entry_size);
    BufferType::set_dynamic_array_size(e1, 2);
    new (static_cast<void *>(e1)) WrapInt32(42);
    new (static_cast<void *>(e1 + 1)) WrapInt32(47);
    new (static_cast<void *>(e1 + 2)) WrapInt32(49); // Not cleaned by clean_hold
}

TEST_F(DynamicArrayBufferTypeTest, entry_size_is_calculated)
{
    EXPECT_EQ(8, get_entry_size<char>(1));
    EXPECT_EQ(8, get_entry_size<char>(2));
    EXPECT_EQ(8, get_entry_size<char>(3));
    EXPECT_EQ(8, get_entry_size<char>(4));
    EXPECT_EQ(12, get_entry_size<char>(5));
    EXPECT_EQ(8, get_entry_size<int16_t>(1));
    EXPECT_EQ(8, get_entry_size<int16_t>(2));
    EXPECT_EQ(12, get_entry_size<int16_t>(3));
    EXPECT_EQ(8, get_entry_size<int32_t>(1));
    EXPECT_EQ(12, get_entry_size<int32_t>(2));
    EXPECT_EQ(16, get_entry_size<int64_t>(1));
    EXPECT_EQ(24, get_entry_size<int64_t>(2));
    EXPECT_EQ(20, get_entry_size<WrapInt32>(4));
}

TEST_F(DynamicArrayBufferTypeTest, initialize_reserved_entries)
{
    _buffer_type.initialize_reserved_entries(_buf, 2);
    EXPECT_EQ((std::vector<int>{}), get_vector(_buf, 0));
    EXPECT_EQ((std::vector<int>{}), get_vector(_buf, 1));
    EXPECT_EQ((std::vector<int>{0, 0, 0}), get_max_vector(_buf, 0));
    EXPECT_EQ((std::vector<int>{0, 0, 0}), get_max_vector(_buf, 1));
    EXPECT_EQ(Counts(0, 0, 6, 0, 0), counts);
}

TEST_F(DynamicArrayBufferTypeTest, fallback_copy)
{
    _buffer_type.initialize_reserved_entries(_buf, 1);
    write_entry1();
    EXPECT_EQ(Counts(0, 3, 3, 0, 0), counts);
    auto buf2_alloc = std::make_unique<char[]>(_buf_size + _buffer_underflow_size);
    char* buf2 = buf2_alloc.get() + _buffer_underflow_size;
    _buffer_type.fallback_copy(buf2, _buf, 2);
    EXPECT_EQ((std::vector<int>{}), get_vector(buf2, 0));
    EXPECT_EQ((std::vector<int>{42, 47}), get_vector(buf2, 1));
    EXPECT_EQ((std::vector<int>{0, 0, 0}), get_max_vector(buf2, 0));
    EXPECT_EQ((std::vector<int>{42, 47, 49}), get_max_vector(buf2, 1));
    EXPECT_EQ(Counts(0, 3, 9, 0, 0), counts);
}

TEST_F(DynamicArrayBufferTypeTest, destroy_entries)
{
    _buffer_type.initialize_reserved_entries(_buf, 2);
    write_entry1();
    _buffer_type.destroy_entries(_buf, 2);
    EXPECT_EQ(Counts(0, 3, 6, 6, 0), counts);
}

TEST_F(DynamicArrayBufferTypeTest, clean_hold)
{
    _buffer_type.initialize_reserved_entries(_buf, 1);
    write_entry1();
    MyCleanContext clean_context;
    _buffer_type.clean_hold(_buf, 1, 1, clean_context);
    EXPECT_EQ((std::vector<int>{0, 0}), get_vector(_buf, 1));
    EXPECT_EQ((std::vector<int>{0, 0, 49}), get_max_vector(_buf, 1));
    EXPECT_EQ(Counts(0, 3, 3, 0, 2), counts);
    _buffer_type.clean_hold(_buf, 0, 2, clean_context);
    EXPECT_EQ(Counts(0, 3, 3, 0, 4), counts);
}

GTEST_MAIN_RUN_ALL_TESTS()
