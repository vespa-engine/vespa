// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/net/tls/impl/direct_buffer_bio.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <cassert>

using namespace vespalib;
using namespace vespalib::crypto;
using namespace vespalib::net::tls::impl;

struct Fixture {
    BioPtr mutable_bio;
    BioPtr const_bio;
    vespalib::string tmp_buf;

    Fixture()
        : mutable_bio(new_mutable_direct_buffer_bio()),
          const_bio(new_const_direct_buffer_bio()),
          tmp_buf('X', 64)
    {
        ASSERT_TRUE(mutable_bio && const_bio);
    }
};

TEST_F("BIOs without associated buffers return zero pending", Fixture) {
    EXPECT_EQUAL(0, BIO_pending(f.mutable_bio.get()));
    EXPECT_EQUAL(0, BIO_pending(f.const_bio.get()));
}

TEST_F("Const BIO has initial pending equal to size of associated buffer", Fixture) {
    vespalib::string to_read = "I sure love me some data";
    ConstBufferViewGuard g(*f.const_bio, &to_read[0], to_read.size());
    EXPECT_EQUAL(static_cast<int>(to_read.size()), BIO_pending(f.const_bio.get()));
}

TEST_F("Mutable BIO has initial pending of 0 with associated buffer (pending == written bytes)", Fixture) {
    MutableBufferViewGuard g(*f.mutable_bio, &f.tmp_buf[0], f.tmp_buf.size());
    EXPECT_EQUAL(0, BIO_pending(f.mutable_bio.get()));
}

TEST_F("Mutable BIO_write writes to associated buffer", Fixture) {
    MutableBufferViewGuard g(*f.mutable_bio, &f.tmp_buf[0], f.tmp_buf.size());
    vespalib::string to_write = "hello world!";
    int ret = ::BIO_write(f.mutable_bio.get(), to_write.data(), static_cast<int>(to_write.size()));
    EXPECT_EQUAL(static_cast<int>(to_write.size()), ret);
    EXPECT_EQUAL(to_write, vespalib::stringref(f.tmp_buf.data(), to_write.size()));
    EXPECT_EQUAL(static_cast<int>(to_write.size()), BIO_pending(f.mutable_bio.get()));
}

TEST_F("Mutable BIO_write moves write cursor per invocation", Fixture) {
    MutableBufferViewGuard g(*f.mutable_bio, &f.tmp_buf[0], f.tmp_buf.size());
    vespalib::string to_write = "hello world!";

    int ret = ::BIO_write(f.mutable_bio.get(), to_write.data(), 3); // 'hel'
    ASSERT_EQUAL(3, ret);
    EXPECT_EQUAL(3, BIO_pending(f.mutable_bio.get()));
    ret = ::BIO_write(f.mutable_bio.get(), to_write.data() + 3, 5); // 'lo wo'
    ASSERT_EQUAL(5, ret);
    EXPECT_EQUAL(8, BIO_pending(f.mutable_bio.get()));
    ret = ::BIO_write(f.mutable_bio.get(), to_write.data() + 8, 4); // 'rld!'
    ASSERT_EQUAL(4, ret);
    EXPECT_EQUAL(12, BIO_pending(f.mutable_bio.get()));

    EXPECT_EQUAL(to_write, vespalib::stringref(f.tmp_buf.data(), to_write.size()));
}

TEST_F("Const BIO_read reads from associated buffer", Fixture) {
    vespalib::string to_read = "look at this fancy data!";
    ConstBufferViewGuard g(*f.const_bio, &to_read[0], to_read.size());

    int ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[0], static_cast<int>(f.tmp_buf.size()));
    EXPECT_EQUAL(static_cast<int>(to_read.size()), ret);
    EXPECT_EQUAL(ret, static_cast<int>(to_read.size()));

    EXPECT_EQUAL(to_read, vespalib::stringref(f.tmp_buf.data(), to_read.size()));
}

TEST_F("Const BIO_read moves read cursor per invocation", Fixture) {
    vespalib::string to_read = "look at this fancy data!";
    ConstBufferViewGuard g(*f.const_bio, &to_read[0], to_read.size());

    EXPECT_EQUAL(24, BIO_pending(f.const_bio.get()));
    int ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[0], 8); // 'look at '
    ASSERT_EQUAL(8, ret);
    EXPECT_EQUAL(16, BIO_pending(f.const_bio.get()));
    ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[8], 10); // 'this fancy'
    ASSERT_EQUAL(10, ret);
    EXPECT_EQUAL(6, BIO_pending(f.const_bio.get()));
    ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[18], 20); // ' data!' (with extra destination space available)
    ASSERT_EQUAL(6, ret);
    EXPECT_EQUAL(0, BIO_pending(f.const_bio.get()));

    EXPECT_EQUAL(to_read, vespalib::stringref(f.tmp_buf.data(), to_read.size()));
}

TEST_F("Const BIO read EOF returns -1 by default and sets BIO retry flag", Fixture) {
    ConstBufferViewGuard g(*f.const_bio, "", 0);
    int ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[0], static_cast<int>(f.tmp_buf.size()));
    EXPECT_EQUAL(-1, ret);
    EXPECT_NOT_EQUAL(0, BIO_should_retry(f.const_bio.get()));
}

TEST_F("Can invoke BIO_(set|get)_close", Fixture) {
    (void)BIO_set_close(f.mutable_bio.get(), 0);
    EXPECT_EQUAL(0, BIO_get_close(f.mutable_bio.get()));
    (void)BIO_set_close(f.mutable_bio.get(), 1);
    EXPECT_EQUAL(1, BIO_get_close(f.mutable_bio.get()));
}

TEST_F("BIO_write on const BIO returns failure", Fixture) {
    vespalib::string data = "safe and cozy data :3";
    vespalib::string to_read = data;
    ConstBufferViewGuard g(*f.const_bio, &to_read[0], to_read.size());

    int ret = ::BIO_write(f.const_bio.get(), "unsafe", 6);
    EXPECT_EQUAL(-1, ret);
    EXPECT_EQUAL(0, BIO_should_retry(f.mutable_bio.get()));
    EXPECT_EQUAL(data, to_read);
}

TEST_F("BIO_read on mutable BIO returns failure", Fixture) {
    MutableBufferViewGuard g(*f.mutable_bio, &f.tmp_buf[0], f.tmp_buf.size());

    vespalib::string dummy_buf;
    dummy_buf.reserve(128);
    int ret = ::BIO_read(f.mutable_bio.get(), &dummy_buf[0], static_cast<int>(dummy_buf.size()));
    EXPECT_EQUAL(-1, ret);
    EXPECT_EQUAL(0, BIO_should_retry(f.mutable_bio.get()));
}

TEST_F("Can do read on zero-length nullptr const buffer", Fixture) {
    ConstBufferViewGuard g(*f.const_bio, nullptr, 0);
    int ret = ::BIO_read(f.const_bio.get(), &f.tmp_buf[0], static_cast<int>(f.tmp_buf.size()));
    EXPECT_EQUAL(-1, ret);
    EXPECT_NOT_EQUAL(0, BIO_should_retry(f.const_bio.get()));
}

TEST_MAIN() { TEST_RUN_ALL(); }

