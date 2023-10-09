// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/dual_merge_director.h>

using namespace vespalib;

struct MySource : public DualMergeDirector::Source {

    bool typeA;
    std::string data;
    std::string diff;

    MySource(bool a, size_t num_sources, size_t source_id);
    ~MySource();
    void merge(Source &mt) override {
        MySource &rhs = static_cast<MySource&>(mt);
        ASSERT_EQUAL(typeA, rhs.typeA);
        ASSERT_EQUAL(data.size(), rhs.data.size());
        for (size_t i = 0; i < data.size(); ++i) {
            int d = (rhs.data[i] - '0');
            data[i] += d;
            diff[i] += d;
            rhs.diff[i] -= d;
        }
    }
    void verifyFinal() const {
        EXPECT_EQUAL(std::string(data.size(), '1'), data);
        EXPECT_EQUAL(std::string(diff.size(), '6'), diff);
    }
    void verifyIntermediate() const {
        EXPECT_EQUAL(std::string(diff.size(), '5'), diff);
    }
};

MySource::MySource(bool a, size_t num_sources, size_t source_id)
    : typeA(a),
      data(num_sources, '0'),
      diff(num_sources, '5')
{
    if (source_id < num_sources) {
        data[source_id] = '1';
        diff[source_id] = '6';
    }
}
MySource::~MySource() {}

TEST_MT_F("require that merging works", 64, std::unique_ptr<DualMergeDirector>()) {
    for (size_t use_threads = 1; use_threads <= num_threads; ++use_threads) {
        MySource sourceA(true, use_threads, thread_id);
        MySource sourceB(false, use_threads, thread_id);
        if (thread_id == 0) {
            f1.reset(new DualMergeDirector(use_threads));
        }
        TEST_BARRIER();
        if (thread_id < use_threads) {
            f1->dualMerge(thread_id, sourceA, sourceB);
        }
        TEST_BARRIER();
        if (thread_id == 0) {
            sourceA.verifyFinal();
            sourceB.verifyFinal();
        } else if (thread_id < use_threads) {
            sourceA.verifyIntermediate();
            sourceB.verifyIntermediate();
        }
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
