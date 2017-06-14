// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/atomic.h>
#include <vespa/fastos/thread.h>
#include <vector>
#include <algorithm>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("atomic_test");

class Test : public vespalib::TestApp
{
public:
    template<typename T, typename U>
    void testAdd();
    template<typename T, typename U>
    void testAddSub();
    template<typename T>
    void testInc();
    template<typename T>
    void testDec();
    template<typename T>
    void testSemantics();
    int Main() override;
};

static const int numadders = 7;
static const int loopcnt = 100000;

int
Test::Main()
{
    TEST_INIT("atomic_test");

    testSemantics<int32_t>();
    testSemantics<int64_t>();
    testAdd<int32_t, uint32_t>();
    testAdd<int64_t, uint64_t>();
    testAddSub<int32_t, uint32_t>();
    testAddSub<int64_t, uint64_t>();
    testInc<uint32_t>();
    testInc<uint64_t>();
    testDec<uint32_t>();
    testDec<uint64_t>();

    TEST_FLUSH();
    TEST_DONE();
}

template<typename T>
void
Test::testSemantics()
{
    using vespalib::Atomic;
    volatile T value(0);
    EXPECT_EQUAL(0, value);
    EXPECT_EQUAL(0, Atomic::postInc(&value));
    EXPECT_EQUAL(1, Atomic::postInc(&value));
    EXPECT_EQUAL(2, value);
    EXPECT_EQUAL(2, Atomic::postDec(&value));
    EXPECT_EQUAL(1, value);
    EXPECT_EQUAL(1, Atomic::postAdd(&value, 17));
    EXPECT_EQUAL(18, value);
    EXPECT_EQUAL(18, Atomic::postAdd(&value, 17));
    EXPECT_EQUAL(35, value);
    EXPECT_EQUAL(35, Atomic::postAdd(&value, -7));
    EXPECT_EQUAL(28, value);
}

class NotAtomic
{
public:
    static inline void add(volatile int *data,          int xdelta) {
        (*data) += xdelta;
    }
    static inline void sub(volatile int *data,          int xdelta) {
        (*data) -= xdelta;
    }
    static inline void add(volatile unsigned int *data, unsigned int xdelta) {
        (*data) += xdelta;
    }
    static inline void sub(volatile unsigned int *data, unsigned int xdelta) {
        (*data) -= xdelta;
    }
    static inline unsigned int postDec(volatile unsigned int *data) {
        return (*data)--;
    }
    static inline unsigned int postInc(volatile unsigned int *data) {
        return (*data)++;
    }
};

template<typename T, typename U>
class Adder : public FastOS_Runnable
{
private:
    int _added;
    const T _toadd;
    const int _times;
    volatile T * const _idata;
    volatile U * const _udata;
public:
    Adder(T toadd, int times, T *i, U *u) :
        _added(0),
        _toadd(toadd),
        _times(times),
        _idata(i),
        _udata(u)
    {}
    void Run(FastOS_ThreadInterface *, void *) override {
        using vespalib::Atomic;
        for (int i = 0; i < _times; ++i) {
            Atomic::add(_idata, _toadd);
            Atomic::add(_udata, _toadd);
            _added += _toadd;
        }
    }
    int getAdded() { return _added; }
};


template<typename T, typename U>
class Subtracter : public FastOS_Runnable
{
private:
    int _subed;
    const T _tosub;
    const int _times;
    volatile T * const _idata;
    volatile U * const _udata;
public:
    Subtracter(T tosub, int times, T *i, U *u) :
        _subed(0),
        _tosub(tosub),
        _times(times),
        _idata(i),
        _udata(u)
    {}
    void Run(FastOS_ThreadInterface *, void *) override {
        using vespalib::Atomic;
        for (int i = 0; i < _times; ++i) {
            Atomic::sub(_idata, _tosub);
            Atomic::sub(_udata, _tosub);
            _subed += _tosub;
        }
    }
    int getSubtracted() { return _subed; }
};

template <typename T>
class Changer : public FastOS_Runnable
{
protected:
    std::vector<T> _counts;
    volatile T * const _idata;
    const int _times;
public:
    Changer(int times, T *data)
        : _counts(), _idata(data), _times(times) {}
    const std::vector<T> & getCounts() const { return _counts; }
};


template <typename T>
class Incrementer : public Changer<T>
{
public:
    Incrementer(int times, T *data) : Changer<T>(times, data) {}
    void Run(FastOS_ThreadInterface *, void *) override {
        using vespalib::Atomic;
        for (int i = 0; i < this->_times; ++i) {
            this->_counts.push_back(Atomic::postInc(this->_idata));
        }
    }
};


template <typename T>
class Decrementer : public Changer<T>
{
public:
    Decrementer(int times, T *data) : Changer<T>(times, data) {}
    void Run(FastOS_ThreadInterface *, void *) override {
        using vespalib::Atomic;
        for (int i = 0; i < this->_times; ++i) {
            this->_counts.push_back(Atomic::postDec(this->_idata));
        }
    }
};


template<typename T, typename U>
void
Test::testAdd()
{
    Adder<T, U> *threads1[numadders];

    T intcounter = 0;
    U uintcounter = 0;

    FastOS_ThreadPool tpool1(65000, numadders);
    for (int i = 0; i < numadders; i++) {
        threads1[i] = new Adder<T, U>(2+i, loopcnt, &intcounter, &uintcounter);
        tpool1.NewThread(threads1[i]);
    }
    tpool1.Close();
    T intcorrect = 0;
    U uintcorrect = 0;
    for (int i = 0; i < numadders; i++) {
        intcorrect  += threads1[i]->getAdded();
        uintcorrect += threads1[i]->getAdded();
    }
    for (int i = 0; i < numadders; i++) {
        delete threads1[i];
    }
    std::ostringstream os;
    os << "intcounter = " << intcounter << ", intcorrect = " << intcorrect;
    LOG(debug, "%s", os.str().c_str());
    EXPECT_TRUE( intcounter == intcorrect);
    std::ostringstream uos;
    uos << "uintcounter = " << uintcounter << ", uintcorrect = " << uintcorrect;
    LOG(debug, "%s", uos.str().c_str());
    EXPECT_TRUE(uintcounter == uintcorrect);
}


template<typename T, typename U>
void
Test::testAddSub()
{
    FastOS_Runnable *threads2[numadders*2];
    T intcounter = 0;
    U uintcounter = 0;

    FastOS_ThreadPool tpool2(65000, 2*numadders);
    for (int i = 0; i < numadders; i++) {
        threads2[i] = new Adder<T, U>(2+i, loopcnt, &intcounter, &uintcounter);
        threads2[numadders+i] = new Subtracter<T, U>(2+i, loopcnt,
                                               &intcounter, &uintcounter);
    }
    for (int i = 0; i < numadders*2; i++) {
        tpool2.NewThread(threads2[i]);
    }
    tpool2.Close();

    for (int i = 0; i < numadders*2; i++) {
        delete threads2[i];
    }
    std::ostringstream os;
    os << "intcounter = " << intcounter << ", uintcounter = " << uintcounter;
    LOG(debug, "%s", os.str().c_str());
    EXPECT_TRUE( intcounter == 0);
    EXPECT_TRUE(uintcounter == 0);
}


template<typename T>
void
Test::testInc()
{
    Incrementer<T> *threads3[numadders];
    T uintcounter = 0;
    FastOS_ThreadPool tpool3(65000, numadders);
    for (int i = 0; i < numadders; i++) {
        threads3[i] = new Incrementer<T>(loopcnt, &uintcounter);
        tpool3.NewThread(threads3[i]);
    }
    tpool3.Close();
    std::vector<T> all;
    for (int i = 0; i < numadders; i++) {
        const std::vector<T> & cnts = threads3[i]->getCounts();
        typename std::vector<T>::const_iterator it = cnts.begin();
        while (it != cnts.end()) {
            all.push_back(*it);
            ++it;
        }
    }
    for (int i = 0; i < numadders; i++) {
        delete threads3[i];
    }
    std::sort(all.begin(), all.end());
    for (unsigned int n = 0; n < all.size(); ++n) {
        EXPECT_TRUE(all[n] == n);
        if (all[n] != n) {
            std::ostringstream os;
            os << all[n];
            LOG(info, "all[%d] = %s", n, os.str().c_str());
            break;
        }
    }
    TEST_FLUSH();
    EXPECT_TRUE(uintcounter == numadders * loopcnt);
    TEST_FLUSH();
}

template<typename T>
void
Test::testDec()
{
    T uintcounter = numadders * loopcnt;
    Decrementer<T> *threads4[numadders];
    FastOS_ThreadPool tpool4(65000, numadders);
    for (int i = 0; i < numadders; i++) {
        threads4[i] = new Decrementer<T>(loopcnt, &uintcounter);
        tpool4.NewThread(threads4[i]);
    }
    tpool4.Close();
    std::vector<T> all;
    for (int i = 0; i < numadders; i++) {
        const std::vector<T> & cnts = threads4[i]->getCounts();
        typename std::vector<T>::const_iterator it = cnts.begin();
        while (it != cnts.end()) {
            all.push_back(*it);
            ++it;
        }
    }
    for (int i = 0; i < numadders; i++) {
        delete threads4[i];
    }
    std::sort(all.begin(), all.end());
    for (size_t n = 0; n < all.size(); ++n) {
        EXPECT_TRUE(all[n] == n+1);
        if (all[n] != n+1) {
            for (size_t i = n; i < std::min(n+20, all.size()); ++i) {
                std::ostringstream os;
                os << std::dec << "all[" << i << "] = " << std::hex << all[i];
                LOG(warning, "%s", os.str().c_str());
            }
            break;
        }
    }
    TEST_FLUSH();
    EXPECT_TRUE(uintcounter == 0);
}


TEST_APPHOOK(Test)
