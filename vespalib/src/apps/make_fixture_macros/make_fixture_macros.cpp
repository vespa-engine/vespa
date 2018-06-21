// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <stdio.h>
#include <stdlib.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP("make_fixture_macros");

void out(const char *str) { fprintf(stdout, "%s", str); }
void out_n(const char *str, int n) { fprintf(stdout, str, n); }
void out_nn(const char *str, int n) { fprintf(stdout, str, n, n); }
void out_nnn(const char *str, int n) { fprintf(stdout, str, n, n, n); }
void out_if(const char *str, bool cond) { if (cond) { out(str); } }
void out_opt(bool cond, const char *str1, const char *str2) {
    if (cond) {
        out(str1);
    } else {
        out(str2);
    }
}
void out_list(const char *pre, const char *str, const char *sep, const char *post, int n, int x) {
    out_if(pre, n > 0);
    for (int i = 0; i < n; ++i) {
        out_if(sep, i > 0);
        switch (x) {
        case 0: out(str); break;
        case 1: out_n(str, i + 1); break;
        case 2: out_nn(str, i + 1); break;
        default: LOG_ABORT("should not be reached");
        }
    }
    out_if(post, n > 0);
}
void out_fff(int n) {
    out_list("_", "F", "", "", n, 0);
}
void out_list_n(const char *pre, const char *str, const char *post, int n) {
    out_list(pre, str, ", ", post, n, 1);
}
void out_list_nn(const char *pre, const char *str, const char *post, int n) {
    out_list(pre, str, ", ", post, n, 2);
}

void make_wrapper(int n) {
    out_list_n("    template <", "typename F%d", "> \\\n", n);
    out("    struct Test : vespalib::TestFixtureWrapper { \\\n");
    out_if("        F1 &f; \\\n", n == 1);
    out_list("", "        F%d &f%d; \\\n", "", "", n, 2);
    out_list_nn("        Test(", "F%d &f%d_in", ") : ", n);
    out_if("f(f1_in), ", n == 1);
    out_list_nn("", "f%d(f%d_in)", " {} \\\n", n);
    out("        void test_entry_point() override; \\\n");
    out("    }; \\\n");
}

void make_perform(int n) {
    out("        Test");
    out_list_n("<", "F%d", ">", n);
    out(" test");
    out_list_n("(", "f%d", ")", n);
    out("; \\\n");
    out("        return runTest(test, threads); \\\n");
}

void make_dispatch(int n) {
    for (int i = n; i > 0; --i) {
        out_list_n("    template <", "typename F%d", "> \\\n", i);
        out_n("    bool dispatch%d(", i);
        out_list_nn("", "F%d &f%d", ", ", i - 1);
        out_nn("F%d *_f%d_ptr_) { \\\n", i);
        out_nnn("        std::unique_ptr<F%d> _f%d_ap_(_f%d_ptr_); \\\n", i);
        out_nnn("        F%d &f%d = *_f%d_ap_; \\\n", i);
        out_if("        size_t num_threads(threads); (void) num_threads; \\\n", i < n);
        if (i < n) {
            out_n("        return dispatch%d(", i + 1);
            out_list_n("", "f%d", ", ", i);
            out_n("new fixture%d); \\\n", i + 1);
        } else {
            make_perform(n);
        }
        out("    } \\\n");
    }
}

void make_macro_impl(int n) {
    fprintf(stdout, "// common test macro implementation for %d test fixtures BEGIN\n\n", n);
    out("#define TEST");
    out_fff(n);
    out("_IMPL(name, ignore, threads");
    out_list_n(", ", "fixture%d", "", n);
    out(") \\\n");
    out("namespace { \\\n");
    out("struct TEST_CAT(TestKitHook, __LINE__) : vespalib::TestHook { \\\n");
    out("    TEST_CAT(TestKitHook, __LINE__)() : vespalib::TestHook(__FILE__, name, ignore) {} \\\n");
    make_wrapper(n);
    make_dispatch(n);
    out("    bool run() override { \\\n");
    out("        TEST_STATE(name); \\\n");
    out_if("        size_t num_threads(threads); (void) num_threads; \\\n", n > 0);
    if (n > 0) {
        out("        return dispatch1(new fixture1); \\\n");
    } else {
        make_perform(0);
    }
    out("    } \\\n");
    out("}; \\\n");
    out("TEST_CAT(TestKitHook, __LINE__) TEST_CAT(testKitHook, __LINE__); \\\n");
    out("} /* end of unnamed namespace */ \\\n");
    out_list_n("template <", "typename F%d", "> \\\n", n);
    out("void TEST_CAT(TestKitHook, __LINE__)::Test");
    out_list_n("<", "F%d", ">", n);
    out("::test_entry_point()\n");
    fprintf(stdout, "\n// common test macro implementation for %d test fixtures END\n\n", n);
}

void make_macro_wire(int n) {
    fprintf(stdout, "// test macro variants for %d test fixtures BEGIN\n\n", n);
    for (int ignore = 0; ignore < 2; ++ignore) {
        for (int mt = 0; mt < 2; ++mt) {
            out("#define ");
            out_if("IGNORE_", ignore);
            out("TEST");
            out_if("_MT", mt);
            out_fff(n);
            out("(name");
            out_if(", threads", mt);
            out_list_n(", ", "fixture%d", "", n);
            out(") TEST");
            out_fff(n);
            out("_IMPL(name");
            out_opt(ignore, ", true", ", false");
            out_opt(mt, ", threads", ", 1");
            out_list_n(", ", "fixture%d", "", n);
            out(")\n");
        }
    }
    fprintf(stdout, "\n// test macro variants for %d test fixtures END\n\n", n);
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "usage: %s <N>\n", argv[0]);
        fprintf(stderr, "    produce macros for up to N (minimum 3) test fixtures\n");
        return 1;
    }
    int n = std::max(3, atoi(argv[1]));
    fprintf(stdout, "// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.\n");
    fprintf(stdout, "// macros for up to %d test fixtures, generated by "
            "vespalib/testkit/make_fixture_macros\n\n", n);
    for (int i = 0; i <= n; ++i) {
        make_macro_impl(i);
        make_macro_wire(i);
    }
    return 0;
}
