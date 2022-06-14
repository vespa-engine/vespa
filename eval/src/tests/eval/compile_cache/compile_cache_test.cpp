// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <vespa/eval/eval/key_gen.h>
#include <vespa/eval/eval/test/eval_spec.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <set>

using namespace vespalib;
using namespace vespalib::eval;

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

TEST("require that parameter passing selection affects function key") {
    EXPECT_NOT_EQUAL(gen_key(*Function::parse("a+b"), PassParams::SEPARATE),
                     gen_key(*Function::parse("a+b"), PassParams::ARRAY));
}

TEST("require that the number of parameters affects function key") {
    EXPECT_NOT_EQUAL(gen_key(*Function::parse({"a", "b"}, "a+b"), PassParams::SEPARATE),
                     gen_key(*Function::parse({"a", "b", "c"}, "a+b"), PassParams::SEPARATE));
    EXPECT_NOT_EQUAL(gen_key(*Function::parse({"a", "b"}, "a+b"), PassParams::ARRAY),
                     gen_key(*Function::parse({"a", "b", "c"}, "a+b"), PassParams::ARRAY));
}

TEST("require that implicit and explicit parameters give the same function key") {
    EXPECT_EQUAL(gen_key(*Function::parse({"a", "b"}, "a+b"), PassParams::SEPARATE),
                 gen_key(*Function::parse("a+b"), PassParams::SEPARATE));
    EXPECT_EQUAL(gen_key(*Function::parse({"a", "b"}, "a+b"), PassParams::ARRAY),
                 gen_key(*Function::parse("a+b"), PassParams::ARRAY));
}

TEST("require that symbol names does not affect function key") {
    EXPECT_EQUAL(gen_key(*Function::parse("a+b"), PassParams::SEPARATE),
                 gen_key(*Function::parse("x+y"), PassParams::SEPARATE));
    EXPECT_EQUAL(gen_key(*Function::parse("a+b"), PassParams::ARRAY),
                 gen_key(*Function::parse("x+y"), PassParams::ARRAY));
}

TEST("require that different values give different function keys") {
    EXPECT_NOT_EQUAL(gen_key(*Function::parse("1"), PassParams::SEPARATE),
                     gen_key(*Function::parse("2"), PassParams::SEPARATE));
    EXPECT_NOT_EQUAL(gen_key(*Function::parse("1"), PassParams::ARRAY),
                     gen_key(*Function::parse("2"), PassParams::ARRAY));
}

TEST("require that different strings give different function keys") {
    EXPECT_NOT_EQUAL(gen_key(*Function::parse("\"a\""), PassParams::SEPARATE),
                     gen_key(*Function::parse("\"b\""), PassParams::SEPARATE));
    EXPECT_NOT_EQUAL(gen_key(*Function::parse("\"a\""), PassParams::ARRAY),
                     gen_key(*Function::parse("\"b\""), PassParams::ARRAY));
}

//-----------------------------------------------------------------------------

struct CheckKeys : test::EvalSpec::EvalTest {
    bool failed = false;
    std::set<vespalib::string> seen_keys;
    ~CheckKeys() override;
    bool check_key(const vespalib::string &key) {
        bool seen = (seen_keys.count(key) > 0);
        seen_keys.insert(key);
        return seen;
    }
    virtual void next_expression(const std::vector<vespalib::string> &param_names,
                                 const vespalib::string &expression) override
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
    virtual void handle_case(const std::vector<vespalib::string> &,
                             const std::vector<double> &,
                             const vespalib::string &,
                             double) override {}
};

CheckKeys::~CheckKeys() = default;

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
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::ARRAY);
    TEST_DO(verify_cache(1, 1));
    token_a.reset();
    TEST_DO(verify_cache(0, 0));
}

TEST("require that agents can have separate functions in the cache") {
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::ARRAY);
    CompileCache::Token::UP token_b = CompileCache::compile(*Function::parse("x*y"), PassParams::ARRAY);
    TEST_DO(verify_cache(2, 2));
}

TEST("require that agents can share functions in the cache") {
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::ARRAY);
    CompileCache::Token::UP token_b = CompileCache::compile(*Function::parse("x+y"), PassParams::ARRAY);
    TEST_DO(verify_cache(1, 2));
}

TEST("require that cache usage works") {
    TEST_DO(verify_cache(0, 0));
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQUAL(5.0, token_a->get().get_function<2>()(2.0, 3.0));
    TEST_DO(verify_cache(1, 1));
    CompileCache::Token::UP token_b = CompileCache::compile(*Function::parse("x*y"), PassParams::SEPARATE);
    EXPECT_EQUAL(6.0, token_b->get().get_function<2>()(2.0, 3.0));
    TEST_DO(verify_cache(2, 2));
    CompileCache::Token::UP token_c = CompileCache::compile(*Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQUAL(5.0, token_c->get().get_function<2>()(2.0, 3.0));
    TEST_DO(verify_cache(2, 3));
    token_a.reset();
    TEST_DO(verify_cache(2, 2));
    token_b.reset();
    TEST_DO(verify_cache(1, 1));
    token_c.reset();
    TEST_DO(verify_cache(0, 0));
}

TEST("require that async cache usage works") {
    auto executor = std::make_shared<ThreadStackExecutor>(8, 256_Ki);
    auto binding = CompileCache::bind(executor);
    CompileCache::Token::UP token_a = CompileCache::compile(*Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQUAL(5.0, token_a->get().get_function<2>()(2.0, 3.0));
    CompileCache::Token::UP token_b = CompileCache::compile(*Function::parse("x*y"), PassParams::SEPARATE);
    EXPECT_EQUAL(6.0, token_b->get().get_function<2>()(2.0, 3.0));
    CompileCache::Token::UP token_c = CompileCache::compile(*Function::parse("x+y"), PassParams::SEPARATE);
    EXPECT_EQUAL(5.0, token_c->get().get_function<2>()(2.0, 3.0));
    EXPECT_EQUAL(CompileCache::num_cached(), 2u);
    token_a.reset();
    TEST_DO(verify_cache(2, 2));
    token_b.reset();
    TEST_DO(verify_cache(1, 1));
    token_c.reset();
    TEST_DO(verify_cache(0, 0));
}

TEST("require that compile tasks are run in the most recently bound executor") {
    auto exe1 = std::make_shared<MyExecutor>();
    auto exe2 = std::make_shared<MyExecutor>();
    auto token0 = CompileCache::compile(*Function::parse("a+b"), PassParams::SEPARATE);
    EXPECT_EQUAL(CompileCache::num_bound(), 0u);
    EXPECT_EQUAL(exe1->tasks.size(), 0u);
    EXPECT_EQUAL(exe2->tasks.size(), 0u);
    {
        auto bind1 = CompileCache::bind(exe1);
        auto token1 = CompileCache::compile(*Function::parse("a-b"), PassParams::SEPARATE);
        EXPECT_EQUAL(CompileCache::num_bound(), 1u);
        EXPECT_EQUAL(exe1->tasks.size(), 1u);
        EXPECT_EQUAL(exe2->tasks.size(), 0u);
        {
            auto bind2  = CompileCache::bind(exe2);
            auto token2 = CompileCache::compile(*Function::parse("a*b"), PassParams::SEPARATE);
            EXPECT_EQUAL(CompileCache::num_bound(), 2u);
            EXPECT_EQUAL(exe1->tasks.size(), 1u);
            EXPECT_EQUAL(exe2->tasks.size(), 1u);
        }
        EXPECT_EQUAL(CompileCache::num_bound(), 1u);
    }
    EXPECT_EQUAL(CompileCache::num_bound(), 0u);
}

TEST("require that executors may be unbound in any order") {
    auto exe1 = std::make_shared<MyExecutor>();
    auto exe2 = std::make_shared<MyExecutor>();
    auto exe3 = std::make_shared<MyExecutor>();
    auto bind1 = CompileCache::bind(exe1);
    auto bind2 = CompileCache::bind(exe2);
    auto bind3 = CompileCache::bind(exe3);
    EXPECT_EQUAL(CompileCache::num_bound(), 3u);
    bind2.reset();
    EXPECT_EQUAL(CompileCache::num_bound(), 2u);
    bind3.reset();
    EXPECT_EQUAL(CompileCache::num_bound(), 1u);
    auto token = CompileCache::compile(*Function::parse("a+b"), PassParams::SEPARATE);
    EXPECT_EQUAL(exe1->tasks.size(), 1u);
    EXPECT_EQUAL(exe2->tasks.size(), 0u);
    EXPECT_EQUAL(exe3->tasks.size(), 0u);
}

TEST("require that the same executor can be bound multiple times") {
    auto exe1 = std::make_shared<MyExecutor>();
    auto bind1 = CompileCache::bind(exe1);
    auto bind2 = CompileCache::bind(exe1);
    auto bind3 = CompileCache::bind(exe1);
    EXPECT_EQUAL(CompileCache::num_bound(), 3u);
    bind2.reset();
    EXPECT_EQUAL(CompileCache::num_bound(), 2u);
    bind3.reset();
    EXPECT_EQUAL(CompileCache::num_bound(), 1u);
    auto token = CompileCache::compile(*Function::parse("a+b"), PassParams::SEPARATE);
    EXPECT_EQUAL(CompileCache::num_bound(), 1u);
    EXPECT_EQUAL(exe1->tasks.size(), 1u);
}

struct CompileCheck : test::EvalSpec::EvalTest {
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
    void next_expression(const std::vector<vespalib::string> &,
                         const vespalib::string &) override {}
    void handle_case(const std::vector<vespalib::string> &param_names,
                     const std::vector<double> &param_values,
                     const vespalib::string &expression,
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
                EXPECT_EQUAL(fun(entry.params.data()), entry.expect);
            }
        }
    }
};

CompileCheck::Entry::~Entry() = default;

TEST_F("compile sequentially, then run all conformance tests", test::EvalSpec()) {
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

TEST_F("compile concurrently (8 threads), then run all conformance tests", test::EvalSpec()) {
    f1.add_all_cases();
    auto executor = std::make_shared<ThreadStackExecutor>(8, 256_Ki);
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

TEST_MT_FF("require that deadlock is avoided with blocking executor", 8, std::shared_ptr<Executor>(nullptr), TimeBomb(300)) {
    size_t loop = 16;
    if (thread_id == 0) {
        auto t0 = steady_clock::now();
        f1 = std::make_shared<BlockingThreadStackExecutor>(2, 256_Ki, 3);
        auto binding = CompileCache::bind(f1);
        TEST_BARRIER(); // #1
        for (size_t i = 0; i < num_threads; ++i) {
            f1->execute(std::make_unique<MyCompileTask>(i * loop, loop));
        }
        TEST_BARRIER(); // #2
        auto t1 = steady_clock::now();
        fprintf(stderr, "deadlock test took %" PRIu64 " ms\n", count_ms(t1 - t0));

    } else {
        TEST_BARRIER(); // #1
        size_t seed = (10000 + (thread_id * loop));
        for (size_t i = 0; i < loop; ++i) {
            // use custom constant to make a unique function that needs compilation
            auto token = CompileCache::compile(*Function::parse(fmt("%zu", seed + i)), PassParams::SEPARATE);
        }
        TEST_BARRIER(); // #2
    }
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
