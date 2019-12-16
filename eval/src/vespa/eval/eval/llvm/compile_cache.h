// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "compiled_function.h"
#include <vespa/vespalib/util/executor.h>
#include <condition_variable>
#include <mutex>

namespace vespalib {
namespace eval {

/**
 * A compilation cache used to reduce application configuration cost
 * by not having to compile equivalent expressions multiple times. The
 * expression AST is used to produce a binary key that in turn is used
 * to query the cache. The cache itself will not keep anything alive,
 * but will let you find compiled functions that are currently in use
 * by others.
 **/
class CompileCache
{
private:
    using Key = vespalib::string;
    struct Value {
        size_t num_refs;
        std::atomic<const CompiledFunction *> cf;
        std::condition_variable cond;
        CompiledFunction::UP compiled_function;
        struct ctor_tag {};
        Value(ctor_tag) : num_refs(1), cf(nullptr), cond(), compiled_function() {}
        const CompiledFunction &wait_for_result();
        const CompiledFunction &get() {
            const CompiledFunction *ptr = cf.load(std::memory_order_acquire);
            if (ptr == nullptr) {
                return wait_for_result();
            }
            return *ptr;
        }
    };
    using Map = std::map<Key,Value>;
    static std::mutex _lock;
    static Map _cached;
    static uint64_t _executor_tag;
    static std::vector<std::pair<uint64_t,Executor*>> _executor_stack;

    static void release(Map::iterator entry);
    static uint64_t attach_executor(Executor &executor);
    static void detach_executor(uint64_t tag);

public:
    class Token
    {
    private:
        friend class CompileCache;
        friend class CompileTask;
        struct ctor_tag {};
        CompileCache::Map::iterator _entry;
    public:
        Token(Token &&) = delete;
        Token(const Token &) = delete;
        Token &operator=(Token &&) = delete;
        Token &operator=(const Token &) = delete;
        using UP = std::unique_ptr<Token>;
        explicit Token(CompileCache::Map::iterator entry, ctor_tag) : _entry(entry) {}
        const CompiledFunction &get() const { return _entry->second.get(); }
        ~Token() { CompileCache::release(_entry); }
    };

    class ExecutorBinding {
    private:
        friend class CompileCache;
        uint64_t _tag;
        struct ctor_tag {};
    public:
        ExecutorBinding(ExecutorBinding &&) = delete;
        ExecutorBinding(const ExecutorBinding &) = delete;
        ExecutorBinding &operator=(ExecutorBinding &&) = delete;
        ExecutorBinding &operator=(const ExecutorBinding &) = delete;
        using UP = std::unique_ptr<ExecutorBinding>;
        explicit ExecutorBinding(Executor &executor, ctor_tag) : _tag(attach_executor(executor)) {}
        ~ExecutorBinding() { detach_executor(_tag); }
    };

    static Token::UP compile(const Function &function, PassParams pass_params);
    static ExecutorBinding::UP bind(Executor &executor) {
        return std::make_unique<ExecutorBinding>(executor, ExecutorBinding::ctor_tag());
    }
    static size_t num_cached();
    static size_t num_bound();
    static size_t count_refs();
    static size_t count_pending();

private:
    struct CompileTask : public Executor::Task {
        std::shared_ptr<Function const> function;
        PassParams pass_params;
        Token::UP token;
        CompileTask(const Function &function_in, PassParams pass_params_in, Token::UP token_in)
            : function(function_in.shared_from_this()), pass_params(pass_params_in), token(std::move(token_in)) {}
        void run() override;
    };
};

} // namespace vespalib::eval
} // namespace vespalib
