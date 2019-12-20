// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/left_right_heap.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/inline.h>

using vespalib::RightArrayHeap;
using vespalib::RightHeap;
using vespalib::LeftArrayHeap;
using vespalib::LeftHeap;
using vespalib::LeftStdHeap;
using vespalib::make_string;

template <typename H> struct IsRight { enum { VALUE = 0 }; };
template <> struct IsRight<RightHeap> { enum { VALUE = 1 }; };
template <> struct IsRight<RightArrayHeap> { enum { VALUE = 1 }; };

template <typename H> struct Name { static const char *value() { return "<unknown>"; } };
template <> struct Name<LeftHeap> { static const char *value() { return "LeftHeap"; } };
template <> struct Name<RightHeap> { static const char *value() { return "RightHeap"; } };
template <> struct Name<LeftArrayHeap> { static const char *value() { return "LeftArrayHeap"; } };
template <> struct Name<RightArrayHeap> { static const char *value() { return "RightArrayHeap"; } };
template <> struct Name<LeftStdHeap> { static const char *value() { return "LeftStdHeap"; } };

struct MyCmp {
    const uint32_t *values;
    MyCmp(uint32_t *v) : values(v) {}
    bool operator()(const uint16_t &a, const uint16_t &b) const {
        return (values[a] < values[b]);
    }
};

struct MyInvCmp {
    const uint32_t *values;
    MyInvCmp(uint32_t *v) : values(v) {}
    bool operator()(const uint16_t &a, const uint16_t &b) const {
        return (values[b] < values[a]);
    }
};

struct Timer {
    double minTime;
    vespalib::Timer timer;
    Timer() : minTime(1.0e10), timer() {}
    void start() { timer = vespalib::Timer(); }
    void stop() {
        double ms = vespalib::count_ms(timer.elapsed());
        minTime = std::min(minTime, ms);
    }
};

struct Data16 {
    std::less<uint16_t> cmp;
    size_t size;
    std::vector<uint16_t> data;
    Data16(size_t s) : cmp(), size(s), data() {}
    static const char *name() { return "uint16_t"; }
    void init(bool inv) {
        data.resize(size);
        srandom(42);
        for (size_t i = 0; i < size; ++i) {
            if (inv) {
                data[size - i - 1] = random();
            } else {
                data[i] = random();
            }
        }
        ASSERT_EQUAL(size, data.size());
    }
};

struct Data32p {
    MyCmp cmp;
    size_t size;
    std::vector<uint32_t> values;
    std::vector<uint16_t> data;
    Data32p(size_t s);
    ~Data32p();
    static const char *name() { return "uint32_t[uint16_t]"; }
    void init(bool inv);
};

Data32p::Data32p(size_t s) : cmp(0), size(s), values(), data() {}
Data32p::~Data32p() {}
void
Data32p::init(bool inv) {
    values.resize(size);
    data.resize(size);
    srandom(42);
    for (size_t i = 0; i < size; ++i) {
        if (inv) {
            values[size - i - 1] = random();
            data[size - i - 1] = (size - i - 1);
        } else {
            values[i] = random();
            data[i] = i;
        }
    }
    ASSERT_EQUAL(size, values.size());
    ASSERT_EQUAL(size, data.size());
    cmp = MyCmp(&values[0]);
}

template <typename T, typename C>
bool verifyOrder(T *begin, T *end, const C &cmp, bool inv) {
    size_t len = (end - begin);
    for (size_t i = 0; i < len; ++i) {
        if ((i + 1) < len) {
            bool failed = inv
                          ? cmp(begin[i], begin[i + 1])
                          : cmp(begin[i + 1], begin[i]);
            if (failed) {
                return false;
            }
        }
    }
    return true;
}

//-----------------------------------------------------------------------------

template <typename T, typename C> void std_push_loop(T *begin, T *end, C cmp) noinline__;
template <typename T, typename C> void std_push_loop(T *begin, T *end, C cmp) {
    for (T *pos = begin; pos != end; ++pos) {
        std::push_heap(begin, (pos + 1), cmp);
    }
}

template <typename T, typename C> void std_pop_loop(T *begin, T *end, C cmp) noinline__;
template <typename T, typename C> void std_pop_loop(T *begin, T *end, C cmp) {
    for (T *pos = end; pos != begin; --pos) {
        std::pop_heap(begin, pos, cmp);
    }
}

//-----------------------------------------------------------------------------

template <typename H>
struct Loops {
    template <typename T, typename C> static void push(T *begin, T *end, C cmp) noinline__;
    template <typename T, typename C> static void pop(T *begin, T *end, C cmp) noinline__;
    template <typename T, typename C, bool ADJUST> static void fiddle(T *begin, T *end, C cmp, T *first, T *last) noinline__;
    template <typename T, typename C> static void fiddle(T *begin, T *end, C cmp, T *first, T *last, bool adjust) {
        if (adjust) {
            fiddle<T,C,true>(begin, end, cmp, first, last);
        } else {
            fiddle<T,C,false>(begin, end, cmp, first, last);
        }
    }
};

template <typename H>
template <typename T, typename C>
void Loops<H>::push(T *begin, T *end, C cmp) {
    if (IsRight<H>::VALUE) {
        for (T *pos = end; pos != begin; --pos) {
            H::template push((pos - 1), end, cmp);
        }
    } else {
        for (T *pos = begin; pos != end; ++pos) {
            H::template push(begin, (pos + 1), cmp);
        }
    }
}

template <typename H>
template <typename T, typename C>
void Loops<H>::pop(T *begin, T *end, C cmp) {
    if (IsRight<H>::VALUE) {
        for (T *pos = begin; pos != end; ++pos) {
            H::template pop(pos, end, cmp);
        }
    } else {
        for (T *pos = end; pos != begin; --pos) {
            H::template pop(begin, pos, cmp);
        }
    }
}

template <typename H>
template <typename T, typename C, bool ADJUST>
void Loops<H>::fiddle(T *begin, T *end, C cmp, T *first, T *last) {
    while (first != last) {
        if (ADJUST) {
            H::template front(begin, end) = *first++;
            H::template adjust(begin, end, cmp);
        } else {
            H::template pop(begin, end, cmp);
            if (IsRight<H>::VALUE) {
                *begin = *first++;
            } else {
                *(end - 1) = *first++;
            }
            H::template push(begin, end, cmp);
        }
    }
}

//-----------------------------------------------------------------------------

struct Benchmark {
    typedef std::unique_ptr<Benchmark> UP;
    virtual ~Benchmark() {}
    virtual std::string legend() const = 0;
    virtual double fiddle(size_t heapSize, size_t cnt, size_t loop, bool adjust) = 0;
    virtual std::pair<double, double> sort(size_t maxHeapSize, size_t loop) = 0;
    virtual void runSortBench(size_t maxHeapSize, size_t loop) = 0;
    virtual void runFiddleBench(size_t heapSize, size_t cnt, size_t loop, bool adjust) = 0;
};

template <typename H, typename D>
struct BenchmarkHD : Benchmark {
    std::string legend() const override {
        return make_string("[%s, %s]", Name<H>::value(), D::name());
    }
    double fiddle(size_t heapSize, size_t cnt, size_t loop, bool adjust) override {
        Timer t;
        for (size_t i = 0; i < loop; ++i) {
            D d(cnt * 2);
            d.init(false);
            ASSERT_LESS((heapSize + cnt), d.data.size());
            Loops<H>::push(&d.data[0], &d.data[heapSize], d.cmp);
            t.start(); Loops<H>::fiddle(&d.data[0], &d.data[heapSize], d.cmp, &d.data[cnt], &d.data[cnt * 2], adjust); t.stop();
        }
        return t.minTime;
    }
    std::pair<double, double> sort(size_t maxHeapSize, size_t loop) override {
        Timer t1;
        Timer t2;
        for (size_t i = 0; i < loop; ++i) {
            D d(maxHeapSize);
            d.init(IsRight<H>::VALUE);
            t1.start(); Loops<H>::push(&*d.data.begin(), &*d.data.end(), d.cmp); t1.stop();
            t2.start(); Loops<H>::pop(&*d.data.begin(), &*d.data.end(), d.cmp); t2.stop();
            EXPECT_TRUE(verifyOrder(&*d.data.begin(), &*d.data.end(), d.cmp, !IsRight<H>::VALUE));
        }
        return std::make_pair(t1.minTime, t2.minTime);
    }
    void runSortBench(size_t maxHeapSize, size_t loop) override {
        std::pair<double, double> t = sort(maxHeapSize, loop);
        fprintf(stderr, "  sort bench (size=%zu): %g ms [%g ms (push) %g ms (pop)]\n",
                maxHeapSize, (t.first + t.second), t.first, t.second);
    }
    void runFiddleBench(size_t heapSize, size_t cnt, size_t loop, bool adjust) override {
        double t = fiddle(heapSize, cnt, loop, adjust);
        fprintf(stderr, "  fiddle bench (size=%zu, cnt=%zu, use adjust='%s'): %g ms\n",
                heapSize, cnt, adjust? "yes":"no", t);
    }
};

//-----------------------------------------------------------------------------

TEST_FFF("benchmark std heap with direct uint16_t values", Timer, Timer, Data16(5000)) {
    std::greater<int> cmp;
    for (size_t l = 0; l < 1000; ++l) {
        f3.init(false);
        f1.start(); std_push_loop(&*f3.data.begin(), &*f3.data.end(), cmp); f1.stop();
        f2.start(); std_pop_loop(&*f3.data.begin(), &*f3.data.end(), cmp); f2.stop();
        EXPECT_TRUE(verifyOrder(&*f3.data.begin(), &*f3.data.end(), cmp, false));
    }
    fprintf(stderr, "STD HEAP 16: %g ms [%g ms (push) %g ms (pop)]\n",
            f1.minTime + f2.minTime, f1.minTime, f2.minTime);
}

TEST_FFF("benchmark std heap with indirect uint32_t values", Timer, Timer, Data32p(5000)) {
    for (size_t l = 0; l < 1000; ++l) {
        f3.init(false);
        MyInvCmp cmp(&f3.values[0]);
        f1.start(); std_push_loop(&*f3.data.begin(), &*f3.data.end(), cmp); f1.stop();
        f2.start(); std_pop_loop(&*f3.data.begin(), &*f3.data.end(), cmp); f2.stop();
        EXPECT_TRUE(verifyOrder(&*f3.data.begin(), &*f3.data.end(), cmp, false));
    }
    fprintf(stderr, "STD HEAP 32p: %g ms [%g ms (push) %g ms (pop)]\n",
            f1.minTime + f2.minTime, f1.minTime, f2.minTime);
}

//-----------------------------------------------------------------------------

struct BenchmarkFactory {
    enum DataType {
        DATA_16  = 0,
        DATA_32p = 1,
        DATA_CNT = 2
    };
    enum HeapType {
        HEAP_LEFT               = 0,
        HEAP_RIGHT              = 1,
        HEAP_ARRAY_LEFT         = 2,
        HEAP_ARRAY_RIGHT        = 3,
        HEAP_STD_LEFT           = 4,
        HEAP_CNT                = 5,
    };
    template <typename H>
    static Benchmark::UP create(DataType d) {
        switch (d) {
        case DATA_16:  return Benchmark::UP(new BenchmarkHD<H, Data16>());
        case DATA_32p: return Benchmark::UP(new BenchmarkHD<H, Data32p>());
        default:       TEST_FATAL("undefined data type requested"); return Benchmark::UP();
        }
    }
    static Benchmark::UP create(HeapType h, DataType d) {
        switch(h) {
        case HEAP_LEFT:               return create<LeftHeap>(d);
        case HEAP_RIGHT:              return create<RightHeap>(d);
        case HEAP_ARRAY_LEFT:         return create<LeftArrayHeap>(d);
        case HEAP_ARRAY_RIGHT:        return create<RightArrayHeap>(d);
        case HEAP_STD_LEFT:           return create<LeftStdHeap>(d);
        default:                      TEST_FATAL("undefined heap type requested"); return Benchmark::UP();
        }
    }
};

void findFiddleLimit(Benchmark &a, Benchmark &b, size_t min, size_t max, bool adjust) {
    fprintf(stderr, "looking for the fiddle limit for %s(A) and %s(B) in the range [%zu, %zu]... (use adjust = '%s')\n",
            a.legend().c_str(), b.legend().c_str(), min, max, adjust? "yes":"no");
    double a_min = a.fiddle(min, 10000, 1000, adjust);
    double a_max = a.fiddle(max, 10000, 1000, adjust);
    double b_min = b.fiddle(min, 10000, 1000, adjust);
    double b_max = b.fiddle(max, 10000, 1000, adjust);
    fprintf(stderr, "  A: [%g, %g], B: [%g, %g]\n", a_min, a_max, b_min, b_max);
    if ((a_min < b_min) == (a_max < b_max)) {
        fprintf(stderr, "  NO FIDDLE LIMIT FOUND\n");
    }
    while (min < max) {
        size_t x = (min + max) / 2;
        double a_x = a.fiddle(x, 10000, 1000, adjust);
        double b_x = b.fiddle(x, 10000, 1000, adjust);
        fprintf(stderr, "  A@%zu: %g, B@%zu: %g\n", x, a_x, x, b_x);
        if ((a_x < b_x) == (a_min < b_min)) {
            min = (x + 1);
        } else {
            max = (x - 1);
        }
    }
}

TEST("find fiddle limits") {
    { // WAND future heap usecase
        Benchmark::UP b = BenchmarkFactory::create(BenchmarkFactory::HEAP_ARRAY_LEFT, BenchmarkFactory::DATA_32p);
        Benchmark::UP a = BenchmarkFactory::create(BenchmarkFactory::HEAP_LEFT, BenchmarkFactory::DATA_32p);
        findFiddleLimit(*a, *b, 8, 1024, false);
    }
    { // WAND past heap usecase
        Benchmark::UP b = BenchmarkFactory::create(BenchmarkFactory::HEAP_ARRAY_RIGHT, BenchmarkFactory::DATA_16);
        Benchmark::UP a = BenchmarkFactory::create(BenchmarkFactory::HEAP_RIGHT, BenchmarkFactory::DATA_16);
        findFiddleLimit(*a, *b, 8, 1024, false);
    }
}

TEST("benchmark") {
    for (int d = 0; d < BenchmarkFactory::DATA_CNT; ++d) {
        for (int h = 0; h < BenchmarkFactory::HEAP_CNT; ++h) {
            Benchmark::UP benchmark = BenchmarkFactory::create(BenchmarkFactory::HeapType(h), BenchmarkFactory::DataType(d));
            fprintf(stderr, "%s:\n", benchmark->legend().c_str());
            benchmark->runSortBench(5000, 1000);
            benchmark->runFiddleBench(300, 10000, 1000, false);
            benchmark->runFiddleBench(300, 10000, 1000, true);
        }
    }
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
