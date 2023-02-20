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

template <typename PathPos>
bool find_path(const Inspector &self, PathPos pos, PathPos end, bool first = false) {
    const Inspector &children = first ? self["roots"] : self["children"];
    if (pos == end) {
        return (children.entries() == 0);
    }
    auto needle = *pos++;
    for (size_t i = 0; i < children.entries(); ++i) {
        if ((children[i]["name"].asString().make_string() == needle.first) &&
            (children[i]["count"].asLong() == needle.second) &&
            (find_path(children[i], pos, end)))
        {
            return true;
        }
    }
    return false;
}

bool find_path(const Slime &slime, const std::vector<std::pair<vespalib::string,int64_t>> &path) {
    return find_path(slime.get(), path.begin(), path.end(), true);
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

TEST(ExecutionProfilerTest, empty_tree_report) {
    Profiler profiler(64);
    profiler.resolve("foo");
    profiler.resolve("bar");
    profiler.resolve("baz");
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s\n", slime.toString().c_str());
    EXPECT_EQ(slime["profiler"].asString().make_string(), "tree");
    EXPECT_EQ(slime["depth"].asLong(), 64);
    EXPECT_EQ(slime["total_time_ms"].asDouble(), 0.0);
    EXPECT_EQ(slime["roots"].entries(), 0);
    EXPECT_TRUE(find_path(slime, {}));
}

TEST(ExecutionProfilerTest, empty_flat_report) {
    Profiler profiler(-64);
    profiler.resolve("foo");
    profiler.resolve("bar");
    profiler.resolve("baz");
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s\n", slime.toString().c_str());
    EXPECT_EQ(slime["profiler"].asString().make_string(), "flat");
    EXPECT_EQ(slime["topn"].asLong(), 64);
    EXPECT_EQ(slime["total_time_ms"].asDouble(), 0.0);
    EXPECT_EQ(slime["roots"].entries(), 0);
    EXPECT_TRUE(find_path(slime, {}));
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
    EXPECT_EQ(slime["profiler"].asString().make_string(), "tree");
    EXPECT_EQ(slime["depth"].asLong(), 64);
    EXPECT_EQ(slime["roots"].entries(), 4);
    EXPECT_TRUE(find_path(slime, {{"foo", 3}, {"bar", 3}, {"baz", 6}, {"fox", 18}}));
    EXPECT_TRUE(find_path(slime, {{"foo", 3}, {"bar", 3}, {"fox", 6}}));
    EXPECT_TRUE(find_path(slime, {{"foo", 3}, {"baz", 3}, {"fox", 9}}));
    EXPECT_TRUE(find_path(slime, {{"foo", 3}, {"fox", 3}}));
    EXPECT_TRUE(find_path(slime, {{"bar", 3}, {"baz", 6}, {"fox", 18}}));
    EXPECT_TRUE(find_path(slime, {{"bar", 3}, {"fox", 6}}));
    EXPECT_TRUE(find_path(slime, {{"baz", 3}, {"fox", 9}}));
    EXPECT_TRUE(find_path(slime, {{"fox", 3}}));
}

TEST(ExecutionProfilerTest, perform_flat_dummy_profiling) {
    Profiler profiler(-64);
    for (int i = 0; i < 3; ++i) {
        foo(profiler);
        bar(profiler);
        baz(profiler);
        fox(profiler);
    }
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s\n", slime.toString().c_str());
    EXPECT_EQ(slime["profiler"].asString().make_string(), "flat");
    EXPECT_EQ(slime["topn"].asLong(), 64);
    EXPECT_EQ(slime["roots"].entries(), 4);
    EXPECT_TRUE(find_path(slime, {{"foo", 3}}));
    EXPECT_TRUE(find_path(slime, {{"bar", 6}}));
    EXPECT_TRUE(find_path(slime, {{"baz", 18}}));
    EXPECT_TRUE(find_path(slime, {{"fox", 72}}));
}

TEST(ExecutionProfilerTest, perform_limited_flat_dummy_profiling) {
    Profiler profiler(-2);
    for (int i = 0; i < 3; ++i) {
        foo(profiler);
        bar(profiler);
        baz(profiler);
        fox(profiler);
    }
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s\n", slime.toString().c_str());
    EXPECT_EQ(slime["profiler"].asString().make_string(), "flat");
    EXPECT_EQ(slime["topn"].asLong(), 2);
    EXPECT_EQ(slime["roots"].entries(), 2);
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
    EXPECT_EQ(slime["profiler"].asString().make_string(), "tree");
    EXPECT_EQ(slime["depth"].asLong(), 2);
    EXPECT_EQ(slime["roots"].entries(), 4);
    EXPECT_TRUE(find_path(slime, {{"foo", 3}, {"bar", 3}}));
    EXPECT_TRUE(find_path(slime, {{"foo", 3}, {"bar", 3}}));
    EXPECT_TRUE(find_path(slime, {{"foo", 3}, {"baz", 3}}));
    EXPECT_TRUE(find_path(slime, {{"foo", 3}, {"fox", 3}}));
    EXPECT_TRUE(find_path(slime, {{"bar", 3}, {"baz", 6}}));
    EXPECT_TRUE(find_path(slime, {{"bar", 3}, {"fox", 6}}));
    EXPECT_TRUE(find_path(slime, {{"baz", 3}, {"fox", 9}}));
    EXPECT_TRUE(find_path(slime, {{"fox", 3}}));
}

TEST(ExecutionProfilerTest, with_name_mapping) {
    Profiler profiler(64);
    for (int i = 0; i < 3; ++i) {
        foo(profiler);
        bar(profiler);
        baz(profiler);
        fox(profiler);
    }
    Slime slime;
    profiler.report(slime.setObject(), [](const vespalib::string &name)noexcept->vespalib::string {
                                           if ((name == "foo") || (name == "bar")) {
                                               return "magic";
                                           }
                                           return name;
                                       });
    fprintf(stderr, "%s\n", slime.toString().c_str());
    EXPECT_EQ(slime["roots"].entries(), 4);
    EXPECT_TRUE(find_path(slime, {{"magic", 3}, {"magic", 3}, {"baz", 6}, {"fox", 18}}));
    EXPECT_TRUE(find_path(slime, {{"magic", 3}, {"magic", 3}, {"fox", 6}}));
    EXPECT_TRUE(find_path(slime, {{"magic", 3}, {"baz", 3}, {"fox", 9}}));
    EXPECT_TRUE(find_path(slime, {{"magic", 3}, {"fox", 3}}));
    EXPECT_TRUE(find_path(slime, {{"magic", 3}, {"baz", 6}, {"fox", 18}}));
    EXPECT_TRUE(find_path(slime, {{"magic", 3}, {"fox", 6}}));
    EXPECT_TRUE(find_path(slime, {{"baz", 3}, {"fox", 9}}));
    EXPECT_TRUE(find_path(slime, {{"fox", 3}}));
}

TEST(ExecutionProfilerTest, flat_profiling_does_not_report_tasks_with_count_0) {
    Profiler profiler(-2);
    {
        profiler.resolve("foo");
        profiler.resolve("bar");
        profiler.start(profiler.resolve("baz"));
        profiler.complete();
    }
    Slime slime;
    profiler.report(slime.setObject());
    fprintf(stderr, "%s\n", slime.toString().c_str());
    EXPECT_EQ(slime["profiler"].asString().make_string(), "flat");
    EXPECT_EQ(slime["topn"].asLong(), 2);
    EXPECT_EQ(slime["roots"].entries(), 1);
    EXPECT_EQ(slime["roots"][0]["name"].asString().make_stringref(), "baz");
    EXPECT_EQ(slime["roots"][0]["count"].asLong(), 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
