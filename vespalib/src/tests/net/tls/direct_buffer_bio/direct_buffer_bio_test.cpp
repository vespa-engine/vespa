// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/net/tls/impl/direct_buffer_bio.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cassert>
#include <string>

using namespace vespalib;
using namespace vespalib::crypto;
using namespace vespalib::net::tls::impl;

struct Fixture {
    BioPtr mutable_bio;
    BioPtr const_bio;
    std::string tmp_buf;

    Fixture()
        : mutable_bio(new_mutable_direct_buffer_bio()),
          const_bio(new_const_direct_buffer_bio()),
          tmp_buf('X', 64)
    {
        assert(mutable_bio && const_bio);
    }
    ~Fixture();
};
Fixture::~Fixture() = default;

TEST(DirectBufferBIOTest, test_BIOs_without_associated_buffers_return_zero_pending) {
    Fixture f;
    EXPECT_EQ(0, BIO_pending(f.mutable_bio.get()));
    EXPECT_EQ(0, BIO_pending(f.const_bio.get()));
}

TEST(DirectBufferBIOTest, const_BIO_has_initial_pending_equal_to_size_of_associated_buffer) {
    Fixture f;
    std::string to_read = "I sure love me some data";
    ConstBufferViewGuard g(*f.const_bio, &to_read[0], to_read.size());
    EXPECT_EQ(static_cast<int>(to_read.size()), BIO_pending(f.const_bio.get()));
}

TEST(DirectBufferBIOTest, mutable_BIO_has_initial_pending_of_0_with_associated_buffer__pending_eq_written_bytes) {
    Fixture f;
    MutableBufferViewGuard g(*f.mutable_bio, &f.tmp_buf[0], f.tmp_buf.size());
    EXPECT_EQ(0, BIO_pending(f.mutable_bio.get()));
}

TEST(DirectBufferBIOTest, mutable_BIO_write_writes_to_associated_buffer) {
    Fixture f;
    MutableBufferViewGuard g(*f.mutable_bio, &f.tmp_buf[0], f.tmp_buf.size());
    std::string to_write = "hello world!";
    int ret = ::BIO_write(f.mutable_bio.get(), to_write.data(), static_cast<int>(to_write.size()));
    EXPECT_EQ(static_cast<int>(to_write.size()), ret);
    EXPECT_EQ(to_write, std::string_view(f.tmp_buf.data(), to_write.size()));
    EXPECT_EQ(static_cast<int>(to_write.size()), BIO_pending(f.mutable_bio.get()));
}

TEST(DirectBufferBIOTest, mutable_BIO_write_moves_write_cursor_per_invocation) {
    Fixture f;
    MutableBufferViewGuard g(*f.mutable_bio, &f.tmp_buf[0], f.tmp_buf.size());
    std::string to_write = "hello world!";

    int ret = ::BIO_write(f.mutable_bio.get(), to_write.data(), 3); // 'hel'
    ASSERT_EQ(3, ret);
    EXPECT_EQ(3, BIO_pending(f.mutable_bio.get()));
    ret = ::BIO_write(f.mutable_bio.get(), to_write.data() + 3, 5); // 'lo wo'
    ASSERT_EQ(5, ret);
    EXPECT_EQ(8, BIO_pending(f.mutable_bio.get()));
    ret = ::BIO_write(f.mutable_bio.get(), to_write.data() + 8, 4); // 'rld!'
    ASSERT_EQ(4, ret);
    EXPECT_EQ(12, BIO_pending(f.mutable_bio.get()));

    EXPECT_EQ(to_write, std::string_view(f.tmp_buf.data(), to_write.size()));
}

TEST(DirectBufferBIOTest, const_BIO_read_reads_from_associated_buffer) {
    Fixture f;
    std::string to_read = "look at this fancy data!";
    ConstBufferViewGuard g(*f.const_bio, &to_read[0], to_read.size());

    int ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[0], static_cast<int>(f.tmp_buf.size()));
    EXPECT_EQ(static_cast<int>(to_read.size()), ret);
    EXPECT_EQ(ret, static_cast<int>(to_read.size()));

    EXPECT_EQ(to_read, std::string_view(f.tmp_buf.data(), to_read.size()));
}

TEST(DirectBufferBIOTest, const_BIO_read_moves_read_cursor_per_invocation) {
    Fixture f;
    std::string to_read = "look at this fancy data!";
    ConstBufferViewGuard g(*f.const_bio, &to_read[0], to_read.size());

    EXPECT_EQ(24, BIO_pending(f.const_bio.get()));
    int ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[0], 8); // 'look at '
    ASSERT_EQ(8, ret);
    EXPECT_EQ(16, BIO_pending(f.const_bio.get()));
    ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[8], 10); // 'this fancy'
    ASSERT_EQ(10, ret);
    EXPECT_EQ(6, BIO_pending(f.const_bio.get()));
    ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[18], 20); // ' data!' (with extra destination space available)
    ASSERT_EQ(6, ret);
    EXPECT_EQ(0, BIO_pending(f.const_bio.get()));

    EXPECT_EQ(to_read, std::string_view(f.tmp_buf.data(), to_read.size()));
}

TEST(DirectBufferBIOTest, const_BIO_read_EOF_returns_minus_1_by_default_and_sets_BIO_retry_flag) {
    Fixture f;
    ConstBufferViewGuard g(*f.const_bio, "", 0);
    int ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[0], static_cast<int>(f.tmp_buf.size()));
    EXPECT_EQ(-1, ret);
    EXPECT_NE(0, BIO_should_retry(f.const_bio.get()));
}

TEST(DirectBufferBIOTest, Can_invoke_BIO__set_or_get__close) {
    Fixture f;
    (void)BIO_set_close(f.mutable_bio.get(), 0);
    EXPECT_EQ(0, BIO_get_close(f.mutable_bio.get()));
    (void)BIO_set_close(f.mutable_bio.get(), 1);
    EXPECT_EQ(1, BIO_get_close(f.mutable_bio.get()));
}

TEST(DirectBufferBIOTest, test_BIO_write_on_const_BIO_returns_failure) {
    Fixture f;
    std::string data = "safe and cozy data :3";
    std::string to_read = data;
    ConstBufferViewGuard g(*f.const_bio, &to_read[0], to_read.size());

    int ret = ::BIO_write(f.const_bio.get(), "unsafe", 6);
    EXPECT_EQ(-1, ret);
    EXPECT_EQ(0, BIO_should_retry(f.mutable_bio.get()));
    EXPECT_EQ(data, to_read);
}

TEST(DirectBufferBIOTest, test_BIO_read_on_mutable_BIO_returns_failure) {
    Fixture f;
    MutableBufferViewGuard g(*f.mutable_bio, &f.tmp_buf[0], f.tmp_buf.size());

    std::string dummy_buf;
    dummy_buf.reserve(128);
    int ret = ::BIO_read(f.mutable_bio.get(), &dummy_buf[0], static_cast<int>(dummy_buf.size()));
    EXPECT_EQ(-1, ret);
    EXPECT_EQ(0, BIO_should_retry(f.mutable_bio.get()));
}

TEST(DirectBufferBIOTest, can_do_read_on_zero_length_nullptr_const_buffer) {
    Fixture f;
    ConstBufferViewGuard g(*f.const_bio, nullptr, 0);
    int ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[0], static_cast<int>(f.tmp_buf.size()));
    EXPECT_EQ(-1, ret);
    EXPECT_NE(0, BIO_should_retry(f.const_bio.get()));
}

GTEST_MAIN_RUN_ALL_TESTS()
