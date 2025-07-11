// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/test/time_bomb.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <vespa/eval/eval/key_gen.h>
#include <vespa/eval/eval/test/eval_spec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/test/nexus.h>
#include <set>

using namespace vespalib;
using namespace vespalib::eval;
using vespalib::test::Nexus;
using vespalib::eval::test::EvalSpec;

using vespalib::make_string_short::fmt;

struct MyExecutor : public Executor {
    std::vector<Executor::Task::UP> tasks;
    Executor::Task::UP execute(Executor::Task::UP task) override {
        tasks.push_back(std::move(task));
        return Executor::Task::UP();
    }
    void run_tasks() {
        for (const auto &task: tasks) {
            task.get()->run();
        }
        tasks.clear();
    }
    ~MyExecutor() { run_tasks(); }
    void wakeup() override { }
};

//-----------------------------------------------------------------------------

TEST(CompileCacheTest, require_that_parameter_passing_selection_affects_function_key)
{
    EXPECT_NE(gen_key(*Function::parse("a+b"), PassParams::SEPARATE),
              gen_key(*Function::parse("a+b"), PassParams::ARRAY));
}

TEST(CompileCacheTest, require_that_the_number_of_parameters_affects_function_key)
{
    EXPECT_NE(gen_key(*Function::parse({"a", "b"}, "a+b"), PassParams::SEPARATE),
              gen_key(*Function::parse({"a", "b", "c"}, "a+b"), PassParams::SEPARATE));
    EXPECT_NE(gen_key(*Function::parse({"a", "b"}, "a+b"), PassParams::ARRAY),
              gen_key(*Function::parse({"a", "b", "c"}, "a+b"), PassParams::ARRAY));
}

TEST(CompileCacheTest, require_that_implicit_and_explicit_parameters_give_the_same_function_key)
{
    EXPECT_EQ(gen_key(*Function::parse({"a", "b"}, "a+b"), PassParams::SEPARATE),
                 gen_key(*Function::parse("a+b"), PassParams::SEPARATE));
    EXPECT_EQ(gen_key(*Function::parse({"a", "b"}, "a+b"), PassParams::ARRAY),
                 gen_key(*Function::parse("a+b"), PassParams::ARRAY));
}

TEST(CompileCacheTest, require_that_symbol_names_does_not_affect_function_key)
{
    EXPECT_EQ(gen_key(*Function::parse("a+b"), PassParams::SEPARATE),
              gen_key(*Function::parse("x+y"), PassParams::SEPARATE));
    EXPECT_EQ(gen_key(*Function::parse("a+b"), PassParams::ARRAY),
              gen_key(*Function::parse("x+y"), PassParams::ARRAY));
}

TEST(CompileCacheTest, require_that_different_values_give_different_function_keys)
{
    EXPECT_NE(gen_key(*Function::parse("1"), PassParams::SEPARATE),
              gen_key(*Function::parse("2"), PassParams::SEPARATE));
    EXPECT_NE(gen_key(*Function::parse("1"), PassParams::ARRAY),
              gen_key(*Function::parse("2"), PassParams::ARRAY));
}

TEST(CompileCacheTest, require_that_different_strings_give_different_function_keys)
{
    EXPECT_NE(gen_key(*Function::parse("\"a\""), PassParams::SEPARATE),
              gen_key(*Function::parse("\"b\""), PassParams::SEPARATE));
    EXPECT_NE(gen_key(*Function::parse("\"a\""), PassParams::ARRAY),
              gen_key(*Function::parse("\"b\""), PassParams::ARRAY));
}

//-----------------------------------------------------------------------------

struct CheckKeys : EvalSpec::EvalTest {
    bool failed = false;
    std::set<std::string> seen_keys;
    ~CheckKeys() override;
    bool check_key(const std::string &key) {
        bool seen = (seen_keys.count(key) > 0);
        seen_keys.insert(key);
        return seen;
    }
    virtual void next_expression(const std::vector<std::string> &param_names,
                                 const std::string &expression) override
    {
        auto function = Function::parse(param_names, expression);
        if (!CompiledFunction::detect_issues(*function)) {
            if (check_key(gen_key(*function, PassParams::ARRAY)) ||
                check_key(gen_key(*function, PassParams::SEPARATE)) ||
                check_key(gen_key(*function, PassParams::LAZY)))
            {
                failed = true;
                fprintf(stderr, "key collision for: %s\n", expression.c_str());
            }
        }
    }
    virtual void handle_case(const std::vector<std::string> &,
                             const std::vector<double> &,
                             const std::string &,
                             double) override {}
};

CheckKeys::~CheckKeys() = default;

TEST(CompileCacheTest, require_that_all_conformance_expressions_have_different_function_keys)
{
    CheckKeys f1;
    EvalSpec f2;
    f2.add_all_cases();
    f2.each_case(f1);
    EXPECT_TRUE(!f1.failed);
    EXPECT_GT(f1.seen_keys.size(), 100u);
}

//-----------------------------------------------------------------------------

void verify_cache(size_t expect_cached, size_t expect_refs) {
    EXPECT_EQ(expect_cached, CompileCache::num_cached());
    EXPECT_EQ(expect_refs, CompileCache::count_refs());
}

TEST(CompileCacheTest, require_that_cache_is_initially_empty)
{
    verify_cache(0, 0);
}

TEST(CompileCacheTest, require_that_unused_functions_are_evicted_from_the_cache)
{
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::ARRAY);
    {
        SCOPED_TRACE("1st");
        verify_cache(1, 1);
    }
    token_a.reset();
    {
        SCOPED_TRACE("2nd");
        verify_cache(0, 0);
    }
}

TEST(CompileCacheTest, require_that_agents_can_have_separate_functions_in_the_cache)
{
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::ARRAY);
    CompileCache::Token::UP token_b = CompileCache::compile(*Function::parse("x*y"), PassParams::ARRAY);
    verify_cache(2, 2);
}

TEST(CompileCacheTest, require_that_agents_can_share_functions_in_the_cache)
{
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::ARRAY);
    CompileCache::Token::UP token_b = CompileCache::compile(*Function::parse("x+y"), PassParams::ARRAY);
    verify_cache(1, 2);
}

TEST(CompileCacheTest, require_that_cache_usage_works)
{
    {
        SCOPED_TRACE("1");
        verify_cache(0, 0);
    }
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQ(5.0, token_a->get().get_function<2>()(2.0, 3.0));
    {
        SCOPED_TRACE("2");
        verify_cache(1, 1);
    }
    CompileCache::Token::UP token_b = CompileCache::compile(*Function::parse("x*y"), PassParams::SEPARATE);
    EXPECT_EQ(6.0, token_b->get().get_function<2>()(2.0, 3.0));
    {
        SCOPED_TRACE("3");
        verify_cache(2, 2);
    }
    CompileCache::Token::UP token_c = CompileCache::compile(*Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQ(5.0, token_c->get().get_function<2>()(2.0, 3.0));
    {
        SCOPED_TRACE("4");
        verify_cache(2, 3);
    }
    token_a.reset();
    {
        SCOPED_TRACE("5");
        verify_cache(2, 2);
    }
    token_b.reset();
    {
        SCOPED_TRACE("6");
        verify_cache(1, 1);
    }
    token_c.reset();
    {
        SCOPED_TRACE("7");
        verify_cache(0, 0);
    }
}

TEST(CompileCacheTest, require_that_async_cache_usage_works)
{
    auto executor = std::make_shared<ThreadStackExecutor>(8);
    auto binding = CompileCache::bind(executor);
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQ(5.0, token_a->get().get_function<2>()(2.0, 3.0));
    CompileCache::Token::UP token_b = CompileCache::compile(*Function::parse("x*y"), PassParams::SEPARATE);
    EXPECT_EQ(6.0, token_b->get().get_function<2>()(2.0, 3.0));
    CompileCache::Token::UP token_c = CompileCache::compile(*Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQ(5.0, token_c->get().get_function<2>()(2.0, 3.0));
    EXPECT_EQ(CompileCache::num_cached(), 2u);
    token_a.reset();
    {
        SCOPED_TRACE("1");
        verify_cache(2, 2);
    }
    token_b.reset();
    {
        SCOPED_TRACE("2");
        verify_cache(1, 1);
    }
    token_c.reset();
    {
        SCOPED_TRACE("3");
        verify_cache(0, 0);
    }
}

TEST(CompileCacheTest, require_that_compile_tasks_are_run_in_the_most_recently_bound_executor)
{
    auto exe1 = std::make_shared<MyExecutor>();
    auto exe2 = std::make_shared<MyExecutor>();
    auto token0 = CompileCache::compile(*Function::parse("a+b"), PassParams::SEPARATE);
    EXPECT_EQ(CompileCache::num_bound(), 0u);
    EXPECT_EQ(exe1->tasks.size(), 0u);
    EXPECT_EQ(exe2->tasks.size(), 0u);
    {
        auto bind1 = CompileCache::bind(exe1);
        auto token1 = CompileCache::compile(*Function::parse("a-b"), PassParams::SEPARATE);
        EXPECT_EQ(CompileCache::num_bound(), 1u);
        EXPECT_EQ(exe1->tasks.size(), 1u);
        EXPECT_EQ(exe2->tasks.size(), 0u);
        {
            auto bind2  = CompileCache::bind(exe2);
            auto token2 = CompileCache::compile(*Function::parse("a*b"), PassParams::SEPARATE);
            EXPECT_EQ(CompileCache::num_bound(), 2u);
            EXPECT_EQ(exe1->tasks.size(), 1u);
            EXPECT_EQ(exe2->tasks.size(), 1u);
        }
        EXPECT_EQ(CompileCache::num_bound(), 1u);
    }
    EXPECT_EQ(CompileCache::num_bound(), 0u);
}

TEST(CompileCacheTest, require_that_executors_may_be_unbound_in_any_order)
{
    auto exe1 = std::make_shared<MyExecutor>();
    auto exe2 = std::make_shared<MyExecutor>();
    auto exe3 = std::make_shared<MyExecutor>();
    auto bind1 = CompileCache::bind(exe1);
    auto bind2 = CompileCache::bind(exe2);
    auto bind3 = CompileCache::bind(exe3);
    EXPECT_EQ(CompileCache::num_bound(), 3u);
    bind2.reset();
    EXPECT_EQ(CompileCache::num_bound(), 2u);
    bind3.reset();
    EXPECT_EQ(CompileCache::num_bound(), 1u);
    auto token = CompileCache::compile(*Function::parse("a+b"), PassParams::SEPARATE);
    EXPECT_EQ(exe1->tasks.size(), 1u);
    EXPECT_EQ(exe2->tasks.size(), 0u);
    EXPECT_EQ(exe3->tasks.size(), 0u);
}

TEST(CompileCacheTest, require_that_the_same_executor_can_be_bound_multiple_times)
{
    auto exe1 = std::make_shared<MyExecutor>();
    auto bind1 = CompileCache::bind(exe1);
    auto bind2 = CompileCache::bind(exe1);
    auto bind3 = CompileCache::bind(exe1);
    EXPECT_EQ(CompileCache::num_bound(), 3u);
    bind2.reset();
    EXPECT_EQ(CompileCache::num_bound(), 2u);
    bind3.reset();
    EXPECT_EQ(CompileCache::num_bound(), 1u);
    auto token = CompileCache::compile(*Function::parse("a+b"), PassParams::SEPARATE);
    EXPECT_EQ(CompileCache::num_bound(), 1u);
    EXPECT_EQ(exe1->tasks.size(), 1u);
}

struct CompileCheck : EvalSpec::EvalTest {
    struct Entry {
        CompileCache::Token::UP fun;
        std::vector<double> params;
        double expect;
        Entry(CompileCache::Token::UP fun_in, const std::vector<double> &params_in, double expect_in)
            : fun(std::move(fun_in)), params(params_in), expect(expect_in) {}
        Entry(Entry&&) noexcept = default;
        ~Entry();
    };
    std::vector<Entry> list;
    void next_expression(const std::vector<std::string> &,
                         const std::string &) override {}
    void handle_case(const std::vector<std::string> &param_names,
                     const std::vector<double> &param_values,
                     const std::string &expression,
                     double expected_result) override
    {
        auto function = Function::parse(param_names, expression);
        ASSERT_TRUE(!function->has_error());
        bool has_issues = CompiledFunction::detect_issues(*function);
        if (!has_issues) {
            list.emplace_back(CompileCache::compile(*function, PassParams::ARRAY), param_values, expected_result);
        }
    }
    void verify() {
        for (const Entry &entry: list) {
            auto fun = entry.fun->get().get_function();
            if (std::isnan(entry.expect)) {
                EXPECT_TRUE(std::isnan(fun(entry.params.data())));
            } else {
                EXPECT_EQ(fun(entry.params.data()), entry.expect);
            }
        }
    }
};

CompileCheck::Entry::~Entry() = default;

TEST(CompileCacheTest, compile_sequentially_then_run_all_conformance_tests)
{
    EvalSpec f1;
    f1.add_all_cases();
    for (size_t i = 0; i < 2; ++i) {
        CompileCheck test;
        auto t0 = steady_clock::now();
        f1.each_case(test);
        auto t1 = steady_clock::now();
        CompileCache::wait_pending();
        auto t2 = steady_clock::now();
        test.verify();
        auto t3 = steady_clock::now();
        fprintf(stderr, "sequential (run %zu): setup: %" PRIu64 " ms, wait: %" PRIu64 " ms, verify: %" PRIu64 " us, total: %" PRIu64 " ms\n",
                i, count_ms(t1 - t0), count_ms(t2 - t1), count_us(t3 - t2), count_ms(t3 - t0));
    }
}

TEST(CompileCacheTest, compile_concurrently_with_8_threads_then_run_all_conformance_tests)
{
    EvalSpec f1;
    f1.add_all_cases();
    auto executor = std::make_shared<ThreadStackExecutor>(8);
    auto binding = CompileCache::bind(executor);
    while (executor->num_idle_workers() < 8) {
        std::this_thread::sleep_for(1ms);
    }
    for (size_t i = 0; i < 2; ++i) {
        CompileCheck test;
        auto t0 = steady_clock::now();
        f1.each_case(test);
        auto t1 = steady_clock::now();
        CompileCache::wait_pending();
        auto t2 = steady_clock::now();
        test.verify();
        auto t3 = steady_clock::now();
        fprintf(stderr, "concurrent (run %zu): setup: %" PRIu64 " ms, wait: %" PRIu64 " ms, verify: %" PRIu64 " us, total: %" PRIu64 " ms\n",
                i, count_ms(t1 - t0), count_ms(t2 - t1), count_us(t3 - t2), count_ms(t3 - t0));
    }
}

struct MyCompileTask : public Executor::Task {
    size_t seed;
    size_t loop;
    MyCompileTask(size_t seed_in, size_t loop_in) : seed(seed_in), loop(loop_in) {}
    void run() override {
        for (size_t i = 0; i < loop; ++i) {
            // use custom constant to make a unique function that needs compilation
            auto token = CompileCache::compile(*Function::parse(fmt("%zu", seed + i)), PassParams::SEPARATE);
        }
    }
};

TEST(CompileCacheTest, require_that_deadlock_is_avoided_with_blocking_executor)
{
    constexpr size_t num_threads = 8;
    std::shared_ptr<Executor> f1;
    TimeBomb f2(300);
    auto task = [&f1](Nexus& ctx) {
        size_t loop = 16;
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            auto t0 = steady_clock::now();
            f1 = std::make_shared<BlockingThreadStackExecutor>(2, 3);
            auto binding = CompileCache::bind(f1);
            ctx.barrier(); // #1
            for (size_t i = 0; i < num_threads; ++i) {
                f1->execute(std::make_unique<MyCompileTask>(i * loop, loop));
            }
            ctx.barrier(); // #2
            auto t1 = steady_clock::now();
            fprintf(stderr, "deadlock test took %" PRIu64 " ms\n", count_ms(t1 - t0));

        } else {
            ctx.barrier(); // #1
            size_t seed = (10000 + (thread_id * loop));
            for (size_t i = 0; i < loop; ++i) {
                // use custom constant to make a unique function that needs compilation
                auto token = CompileCache::compile(*Function::parse(fmt("%zu", seed + i)), PassParams::SEPARATE);
            }
            ctx.barrier(); // #2
        }
    };
    Nexus::run(num_threads, task);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
