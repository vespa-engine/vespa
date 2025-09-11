// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/dual_merge_director.h>

using namespace vespalib;
using vespalib::test::Nexus;

struct MySource : public DualMergeDirector::Source {

    bool typeA;
    size_t id;
    std::string data;
    std::string diff;

    MySource(bool a, size_t num_sources, size_t source_id);
    ~MySource() override;
    void merge(Source &mt) override {
        MySource &rhs = static_cast<MySource&>(mt);
        ASSERT_EQ(typeA, rhs.typeA);
        ASSERT_GT(rhs.id, id);
        ASSERT_EQ(data.size(), rhs.data.size());
        for (size_t i = 0; i < data.size(); ++i) {
            int d = (rhs.data[i] - '0');
            data[i] += d;
            diff[i] += d;
            rhs.diff[i] -= d;
        }
    }
    void verifyFinal() const {
        EXPECT_EQ(std::string(data.size(), '1'), data);
        EXPECT_EQ(std::string(diff.size(), '6'), diff);
    }
    void verifyIntermediate() const {
        EXPECT_EQ(std::string(diff.size(), '5'), diff);
    }
};

MySource::MySource(bool a, size_t num_sources, size_t source_id)
    : typeA(a),
      id(source_id),
      data(num_sources, '0'),
      diff(num_sources, '5')
{
    if (source_id < num_sources) {
        data[source_id] = '1';
        diff[source_id] = '6';
    }
}

MySource::~MySource() = default;

TEST(DualMergeDirectorTest, require_that_merging_works) {
    size_t num_threads = 64;
    std::unique_ptr<DualMergeDirector> f1;
    auto task = [&](Nexus &ctx){
                    for (size_t use_threads = 1; use_threads <= num_threads; ++use_threads) {
                        MySource sourceA(true, use_threads, ctx.thread_id());
                        MySource sourceB(false, use_threads, ctx.thread_id());
                        if (ctx.thread_id() == 0) {
                            f1.reset(new DualMergeDirector(use_threads));
                        }
                        ctx.barrier();
                        if (ctx.thread_id() < use_threads) {
                            f1->dualMerge(ctx.thread_id(), sourceA, sourceB);
                        }
                        ctx.barrier();
                        if (ctx.thread_id() == 0) {
                            sourceA.verifyFinal();
                            sourceB.verifyFinal();
                        } else if (ctx.thread_id() < use_threads) {
                            sourceA.verifyIntermediate();
                            sourceB.verifyIntermediate();
                        }
                    }
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
