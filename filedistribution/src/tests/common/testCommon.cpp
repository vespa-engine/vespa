// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#define BOOST_TEST_DYN_LINK
#define BOOST_TEST_MAIN

#include <vespa/filedistribution/common/buffer.h>

#include <boost/test/unit_test.hpp>
#include <string>

namespace fd = filedistribution;

const size_t bufferCapacity = 10;

fd::Buffer
getBuffer() {
    const char* test = "test";
    fd::Buffer buffer(test, test + strlen(test));
    buffer.reserve(bufferCapacity);
    buffer.push_back(0);
    return buffer;
}

BOOST_AUTO_TEST_CASE(bufferTest) {
    fd::Buffer buffer(getBuffer());
    BOOST_CHECK(buffer.begin() != 0);
    BOOST_CHECK_EQUAL(bufferCapacity, buffer.capacity());
    BOOST_CHECK_EQUAL(5u, buffer.size());
    BOOST_CHECK_EQUAL(std::string("test"), buffer.begin());
}

struct Callback {
    bool* _called;
    Callback(bool *called)
        :_called(called)
    {}

    void operator()(const std::string& str) {
        BOOST_CHECK_EQUAL("abcd", str);
        *_called = true;
    }
};
