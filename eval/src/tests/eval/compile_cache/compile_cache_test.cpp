// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <vespa/eval/eval/key_gen.h>
#include <vespa/eval/eval/test/eval_spec.h>
#include <set>

using namespace vespalib::eval;

//-----------------------------------------------------------------------------

TEST("require that parameter passing selection affects function key") {
    EXPECT_NOT_EQUAL(gen_key(Function::parse("a+b"), PassParams::SEPARATE),
                     gen_key(Function::parse("a+b"), PassParams::ARRAY));
}

TEST("require that the number of parameters affects function key") {
    EXPECT_NOT_EQUAL(gen_key(Function::parse({"a", "b"}, "a+b"), PassParams::SEPARATE),
                     gen_key(Function::parse({"a", "b", "c"}, "a+b"), PassParams::SEPARATE));
    EXPECT_NOT_EQUAL(gen_key(Function::parse({"a", "b"}, "a+b"), PassParams::ARRAY),
                     gen_key(Function::parse({"a", "b", "c"}, "a+b"), PassParams::ARRAY));
}

TEST("require that implicit and explicit parameters give the same function key") {
    EXPECT_EQUAL(gen_key(Function::parse({"a", "b"}, "a+b"), PassParams::SEPARATE),
                 gen_key(Function::parse("a+b"), PassParams::SEPARATE));
    EXPECT_EQUAL(gen_key(Function::parse({"a", "b"}, "a+b"), PassParams::ARRAY),
                 gen_key(Function::parse("a+b"), PassParams::ARRAY));
}

TEST("require that symbol names does not affect function key") {
    EXPECT_EQUAL(gen_key(Function::parse("a+b"), PassParams::SEPARATE),
                 gen_key(Function::parse("x+y"), PassParams::SEPARATE));
    EXPECT_EQUAL(gen_key(Function::parse("a+b"), PassParams::ARRAY),
                 gen_key(Function::parse("x+y"), PassParams::ARRAY));
}

TEST("require that let bind names does not affect function key") {
    EXPECT_EQUAL(gen_key(Function::parse("let(a,1,a+a)"), PassParams::SEPARATE),
                 gen_key(Function::parse("let(b,1,b+b)"), PassParams::SEPARATE));
    EXPECT_EQUAL(gen_key(Function::parse("let(a,1,a+a)"), PassParams::ARRAY),
                 gen_key(Function::parse("let(b,1,b+b)"), PassParams::ARRAY));
}

TEST("require that different values give different function keys") {
    EXPECT_NOT_EQUAL(gen_key(Function::parse("1"), PassParams::SEPARATE),
                     gen_key(Function::parse("2"), PassParams::SEPARATE));
    EXPECT_NOT_EQUAL(gen_key(Function::parse("1"), PassParams::ARRAY),
                     gen_key(Function::parse("2"), PassParams::ARRAY));
}

TEST("require that different strings give different function keys") {
    EXPECT_NOT_EQUAL(gen_key(Function::parse("\"a\""), PassParams::SEPARATE),
                     gen_key(Function::parse("\"b\""), PassParams::SEPARATE));
    EXPECT_NOT_EQUAL(gen_key(Function::parse("\"a\""), PassParams::ARRAY),
                     gen_key(Function::parse("\"b\""), PassParams::ARRAY));
}

//-----------------------------------------------------------------------------

struct CheckKeys : test::EvalSpec::EvalTest {
    bool failed = false;
    std::set<vespalib::string> seen_keys;
    bool check_key(const vespalib::string &key) {
        bool seen = (seen_keys.count(key) > 0);
        seen_keys.insert(key);
        return seen;
    }
    virtual void next_expression(const std::vector<vespalib::string> &param_names,
                                 const vespalib::string &expression) override
    {
        Function function = Function::parse(param_names, expression);
        if (!CompiledFunction::detect_issues(function)) {
            if (check_key(gen_key(function, PassParams::ARRAY)) ||
                check_key(gen_key(function, PassParams::SEPARATE)) ||
                check_key(gen_key(function, PassParams::LAZY)))
            {
                failed = true;
                fprintf(stderr, "key collision for: %s\n", expression.c_str());
            }
        }
    }
    virtual void handle_case(const std::vector<vespalib::string> &,
                             const std::vector<double> &,
                             const vespalib::string &,
                             double) override {}
};

TEST_FF("require that all conformance expressions have different function keys",
        CheckKeys(), test::EvalSpec())
{
    f2.add_all_cases();
    f2.each_case(f1);
    EXPECT_TRUE(!f1.failed);
    EXPECT_GREATER(f1.seen_keys.size(), 100u);
}

//-----------------------------------------------------------------------------

void verify_cache(size_t expect_cached, size_t expect_refs) {
    EXPECT_EQUAL(expect_cached, CompileCache::num_cached());
    EXPECT_EQUAL(expect_refs, CompileCache::count_refs());
}

TEST("require that cache is initially empty") {
    TEST_DO(verify_cache(0, 0));
}

TEST("require that unused functions are evicted from the cache") {
    CompileCache::Token::UP token_a = CompileCache::compile(Function::parse("x+y"), PassParams::ARRAY);
    TEST_DO(verify_cache(1, 1));
    token_a.reset();
    TEST_DO(verify_cache(0, 0));
}

TEST("require that agents can have separate functions in the cache") {
    CompileCache::Token::UP token_a = CompileCache::compile(Function::parse("x+y"), PassParams::ARRAY);
    CompileCache::Token::UP token_b = CompileCache::compile(Function::parse("x*y"), PassParams::ARRAY);
    TEST_DO(verify_cache(2, 2));
}

TEST("require that agents can share functions in the cache") {
    CompileCache::Token::UP token_a = CompileCache::compile(Function::parse("x+y"), PassParams::ARRAY);
    CompileCache::Token::UP token_b = CompileCache::compile(Function::parse("x+y"), PassParams::ARRAY);
    TEST_DO(verify_cache(1, 2));
}

TEST("require that cache usage works") {
    TEST_DO(verify_cache(0, 0));
    CompileCache::Token::UP token_a = CompileCache::compile(Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQUAL(5.0, token_a->get().get_function<2>()(2.0, 3.0));
    TEST_DO(verify_cache(1, 1));
    CompileCache::Token::UP token_b = CompileCache::compile(Function::parse("x*y"), PassParams::SEPARATE);
    EXPECT_EQUAL(6.0, token_b->get().get_function<2>()(2.0, 3.0));
    TEST_DO(verify_cache(2, 2));
    CompileCache::Token::UP token_c = CompileCache::compile(Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQUAL(5.0, token_c->get().get_function<2>()(2.0, 3.0));
    TEST_DO(verify_cache(2, 3));
    token_a.reset();
    TEST_DO(verify_cache(2, 2));
    token_b.reset();
    TEST_DO(verify_cache(1, 1));
    token_c.reset();
    TEST_DO(verify_cache(0, 0));
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
