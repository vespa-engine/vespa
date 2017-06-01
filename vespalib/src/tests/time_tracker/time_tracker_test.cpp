// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/time_tracker.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;

size_t count(const std::string &token, const vespalib::string &str) {
    size_t cnt = 0;
    for (size_t pos = str.find(token); pos != str.npos; pos = str.find(token, pos + 1)) {
        ++cnt;
    }
    return cnt;
}

void do_stuff(size_t n) {
    vespalib::string data;
    for (size_t i = 0; i < n; ++i) {
        data.append(make_string("%zu%zu", i, i));
    }
}

TEST("require that thread aware time tracking works") {
    TimeTracker outer_tt(2);
    TimeTracker medio_tt(0);
    TimeTracker inner_tt(3);
    {
        TIMED_THREAD(outer_tt);
        TIMED("foo", do_stuff(100));
        TIMED("bar", do_stuff(200));
        TIMED("baz", do_stuff(300));
        {
            TIMED_SCOPE("foo");
            do_stuff(100);
            {
                {
                    TIMED_THREAD(medio_tt);
                    TIMED("ignore", do_stuff(100)); // max_level == 0
                    TIMED("ignore", do_stuff(200)); // max_level == 0
                    TIMED("ignore", do_stuff(300)); // max_level == 0
                    {
                        TIMED_THREAD(inner_tt); 
                        TIMED("foo", do_stuff(100));
                        TIMED("bar", do_stuff(200));
                        TIMED("baz", do_stuff(300));
                        {
                            TIMED_SCOPE("foo");
                            do_stuff(100);
                            {
                                TIMED_SCOPE("bar");
                                do_stuff(200);
                                {
                                    TIMED_SCOPE("baz");
                                    do_stuff(300);
                                }
                            }
                        }
                    }
                }
                TIMED_SCOPE("bar");
                do_stuff(200);
                {
                    TIMED_SCOPE("ignore"); // below max level
                    TIMED("ignore", do_stuff(100)); // below max level
                    TIMED("ignore", do_stuff(200)); // below max level
                    TIMED("ignore", do_stuff(300)); // below max level
                }
            }
        }
    }
    TIMED("ignore", do_stuff(100)); // outside
    TIMED("ignore", do_stuff(200)); // outside
    TIMED("ignore", do_stuff(300)); // outside
    fprintf(stderr, "outer stats: \n%s\n", outer_tt.get_stats().c_str());
    EXPECT_EQUAL(2u, count("foo:", outer_tt.get_stats()));
    EXPECT_EQUAL(2u, count("bar:", outer_tt.get_stats()));
    EXPECT_EQUAL(1u, count("baz:", outer_tt.get_stats()));
    EXPECT_EQUAL(3u, count("foo",  outer_tt.get_stats()));
    EXPECT_EQUAL(2u, count("bar",  outer_tt.get_stats()));
    EXPECT_EQUAL(0u, count("ignore",  outer_tt.get_stats()));
    EXPECT_EQUAL(5u, count("\n",   outer_tt.get_stats()));
    EXPECT_EQUAL("", medio_tt.get_stats());
    fprintf(stderr, "inner stats: \n%s\n", inner_tt.get_stats().c_str());
    EXPECT_EQUAL(2u, count("foo:", inner_tt.get_stats()));
    EXPECT_EQUAL(2u, count("bar:", inner_tt.get_stats()));
    EXPECT_EQUAL(2u, count("baz:", inner_tt.get_stats()));
    EXPECT_EQUAL(4u, count("foo",  inner_tt.get_stats()));
    EXPECT_EQUAL(3u, count("bar",  inner_tt.get_stats()));
    EXPECT_EQUAL(0u, count("ignore",  inner_tt.get_stats()));
    EXPECT_EQUAL(6u, count("\n",   inner_tt.get_stats()));
}

TEST_MAIN() { TEST_RUN_ALL(); }
