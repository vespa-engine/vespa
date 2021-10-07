// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/rusage.h>
#include <vespa/vespalib/util/array.hpp>
#include <csignal>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP("sort_benchmark");

using namespace vespalib;

class Test : public TestApp
{
public:
private:
    template<typename T>
    vespalib::Array<T> create(size_t count);
    template<typename T>
    void sortDirect(size_t count);
    template<typename T>
    void sortInDirect(size_t count);
    int Main() override;
};

template<size_t N>
class TT
{
public:
    TT(uint64_t v) : _v(v) { }
    bool operator < (const TT & rhs) const { return _v < rhs._v; }
private:
    uint64_t _v;
    uint8_t  _payLoad[N - sizeof(uint64_t)];
};

template <typename T>
class I
{
public:
    I(const T * p) : _p(p) { }
    bool operator < (const I & rhs) const { return *_p < *rhs._p; }
private:
    const T * _p;
};

template<typename T>
vespalib::Array<T>
Test::create(size_t count)
{
    vespalib::Array<T> v;
    v.reserve(count);
    srand(0);
    for (size_t i(0); i < count; i++) {
        v.push_back(rand());
    }
    return v;
}

template<typename T>
void Test::sortDirect(size_t count)
{
    vespalib::Array<T> v(create<T>(count));
    LOG(info, "Running sortDirect with %ld count and payload of %ld", v.size(), sizeof(T));
    for (size_t j=0; j < 10; j++) {
        vespalib::Array<T> t(v);
        std::sort(t.begin(), t.end());
    }
}

template<typename T>
void Test::sortInDirect(size_t count)
{
    vespalib::Array<T> k(create<T>(count));
    LOG(info, "Running sortInDirect with %ld count and payload of %ld", k.size(), sizeof(T));
    vespalib::Array< I<T> > v;
    v.reserve(k.size());
    for (size_t i(0), m(k.size()); i < m; i++) {
        v.push_back(&k[i]);
    }
    for (size_t j=0; j < 10; j++) {
        vespalib::Array< I<T> > t(v);
        std::sort(t.begin(), t.end());
    }
}

int
Test::Main()
{
    std::string type("sortdirect");
    size_t count = 1000000;
    size_t payLoad = 0;
    if (_argc > 1) {
        type = _argv[1];
    }
    if (_argc > 2) {
        count = strtol(_argv[2], NULL, 0);
    }
    if (_argc > 3) {
        payLoad = strtol(_argv[3], NULL, 0);
    }
    TEST_INIT("sort_benchmark");
    steady_time start(steady_clock::now());
    if (payLoad < 8) {
        typedef TT<8> T;
        if (type == "sortdirect") {
            sortDirect<T>(count);
        } else if (type == "sortindirect") {
            sortInDirect<T>(count);
        } else {
            LOG(warning, "type '%s' is unknown", type.c_str());
        }
    } else if (payLoad < 16) {
        typedef TT<16> T;
        if (type == "sortdirect") {
            sortDirect<T>(count);
        } else if (type == "sortindirect") {
            sortInDirect<T>(count);
        } else {
            LOG(warning, "type '%s' is unknown", type.c_str());
        }
    } else if (payLoad < 32) {
        typedef TT<32> T;
        if (type == "sortdirect") {
            sortDirect<T>(count);
        } else if (type == "sortindirect") {
            sortInDirect<T>(count);
        } else {
            LOG(warning, "type '%s' is unknown", type.c_str());
        }
    } else if (payLoad < 64) {
        typedef TT<64> T;
        if (type == "sortdirect") {
            sortDirect<T>(count);
        } else if (type == "sortindirect") {
            sortInDirect<T>(count);
        } else {
            LOG(warning, "type '%s' is unknown", type.c_str());
        }
    } else if (payLoad < 128) {
        typedef TT<128> T;
        if (type == "sortdirect") {
            sortDirect<T>(count);
        } else if (type == "sortindirect") {
            sortInDirect<T>(count);
        } else {
            LOG(warning, "type '%s' is unknown", type.c_str());
        }
    } else if (payLoad < 256) {
        typedef TT<256> T;
        if (type == "sortdirect") {
            sortDirect<T>(count);
        } else if (type == "sortindirect") {
            sortInDirect<T>(count);
        } else {
            LOG(warning, "type '%s' is unknown", type.c_str());
        }
    } else if (payLoad < 512) {
        typedef TT<512> T;
        if (type == "sortdirect") {
            sortDirect<T>(count);
        } else if (type == "sortindirect") {
            sortInDirect<T>(count);
        } else {
            LOG(warning, "type '%s' is unknown", type.c_str());
        }
    } else {
        typedef TT<1024> T;
        LOG(info, "Payload %ld is too big to make any sense. Using %ld.", payLoad, sizeof(T));
        if (type == "sortdirect") {
            sortDirect<T>(count);
        } else if (type == "sortindirect") {
            sortInDirect<T>(count);
        } else {
            LOG(warning, "type '%s' is unknown", type.c_str());
        }
    }
    LOG(info, "rusage = {\n%s\n}", vespalib::RUsage::createSelf(start).toString().c_str());
    ASSERT_EQUAL(0, kill(0, SIGPROF));
    TEST_DONE();
}

TEST_APPHOOK(Test);

