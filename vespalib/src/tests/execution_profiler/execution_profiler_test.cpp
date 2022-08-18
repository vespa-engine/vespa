// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/execution_profiler.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <thread>

using Profiler = vespalib::ExecutionProfiler;
using vespalib::Slime;
using vespalib::slime::Cursor;
using vespalib::slime::Inspector;

void fox(Profiler &profiler) {
    profiler.start(profiler.resolve("fox"));
    std::this_thread::sleep_for(1ms);
    profiler.complete();
}

void baz(Profiler &profiler) {
    profiler.start(profiler.resolve("baz"));
    fox(profiler);
    fox(profiler);
    fox(profiler);
    profiler.complete();
}

void bar(Profiler &profiler) {
    profiler.start(profiler.resolve("bar"));
    baz(profiler);
    fox(profiler);
    baz(profiler);
    fox(profiler);
    profiler.complete();
}

void foo(Profiler &profiler) {
    profiler.start(profiler.resolve("foo"));
    bar(profiler);
    baz(profiler);
    fox(profiler);
    profiler.complete();
}
    
const Inspector *find_child(const Inspector &arr, const std::pair<vespalib::string,size_t> &entry) {
    struct MyArrayTraverser : public vespalib::slime::ArrayTraverser {
        const Inspector *node = nullptr;
        const std::pair<vespalib::string,size_t> &needle;
        MyArrayTraverser(const std::pair<vespalib::string,size_t> &needle_in) : needle(needle_in) {}
        void entry(size_t, const Inspector &obj) override {
            if ((obj["name"].asString().make_string() == needle.first) &&
                (obj["count"].asLong() == int64_t(needle.second)))
            {
                assert(node == nullptr);
                node = &obj;
            }
        }
    };
    MyArrayTraverser traverser(entry);
    arr.traverse(traverser);
    return traverser.node;
}

bool verify_path(const Inspector &root, const std::vector<std::pair<vespalib::string,size_t>> &path) {    
    const Inspector *pos = &root;
    bool first = true;
    for (const auto &entry: path) {
        if (first) {
            pos = find_child((*pos)["roots"], entry);
        } else {
            pos = find_child((*pos)["children"], entry);
        }
        first = false;
        if (pos == nullptr) {
            fprintf(stderr, "could not find entry [%s, %zu]\n", entry.first.c_str(), entry.second);
            return false;
        }
    }
    if ((*pos)["roots"].valid() || (*pos)["children"].valid()) {
        fprintf(stderr, "path too shallow\n");
        return false;
    }
    return true;
}

TEST(ExecutionProfilerTest, resolve_names) {
    Profiler profiler(64);
    EXPECT_EQ(profiler.resolve("foo"), 0);
    EXPECT_EQ(profiler.resolve("bar"), 1);
    EXPECT_EQ(profiler.resolve("baz"), 2);
    EXPECT_EQ(profiler.resolve("foo"), 0);
    EXPECT_EQ(profiler.resolve("bar"), 1);
    EXPECT_EQ(profiler.resolve("baz"), 2);
}

TEST(ExecutionProfilerTest, empty_report) {
    Profiler profiler(64);
    profiler.resolve("foo");
    profiler.resolve("bar");
    profiler.resolve("baz");
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s\n", slime.toString().c_str());
    EXPECT_EQ(slime["roots"].entries(), 0);
    EXPECT_TRUE(verify_path(slime.get(), {}));
}

TEST(ExecutionProfilerTest, perform_dummy_profiling) {
    Profiler profiler(64);
    for (int i = 0; i < 3; ++i) {
        foo(profiler);
        bar(profiler);
        baz(profiler);
        fox(profiler);
    }
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s\n", slime.toString().c_str());
    EXPECT_EQ(slime["roots"].entries(), 4);
    EXPECT_TRUE(verify_path(slime.get(), {{"foo", 3}, {"bar", 3}, {"baz", 6}, {"fox", 18}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"foo", 3}, {"bar", 3}, {"fox", 6}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"foo", 3}, {"baz", 3}, {"fox", 9}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"foo", 3}, {"fox", 3}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"bar", 3}, {"baz", 6}, {"fox", 18}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"bar", 3}, {"fox", 6}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"baz", 3}, {"fox", 9}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"fox", 3}}));
}

TEST(ExecutionProfilerTest, perform_shallow_dummy_profiling) {
    Profiler profiler(2);
    for (int i = 0; i < 3; ++i) {
        foo(profiler);
        bar(profiler);
        baz(profiler);
        fox(profiler);
    }
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s\n", slime.toString().c_str());
    EXPECT_EQ(slime["roots"].entries(), 4);
    EXPECT_TRUE(verify_path(slime.get(), {{"foo", 3}, {"bar", 3}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"foo", 3}, {"bar", 3}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"foo", 3}, {"baz", 3}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"foo", 3}, {"fox", 3}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"bar", 3}, {"baz", 6}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"bar", 3}, {"fox", 6}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"baz", 3}, {"fox", 9}}));
    EXPECT_TRUE(verify_path(slime.get(), {{"fox", 3}}));
}

GTEST_MAIN_RUN_ALL_TESTS()
