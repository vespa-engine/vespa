// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compile_cache.h"
#include <vespa/eval/eval/key_gen.h>

namespace vespalib {
namespace eval {

std::mutex CompileCache::_lock{};
CompileCache::Map CompileCache::_cached{};
Executor *CompileCache::_executor{nullptr};

const CompiledFunction &
CompileCache::Value::wait_for_result()
{
    std::unique_lock<std::mutex> guard(_lock);
    cond.wait(guard, [this](){ return bool(compiled_function); });
    return *compiled_function;
}

void
CompileCache::release(Map::iterator entry)
{
    std::lock_guard<std::mutex> guard(_lock);
    if (--(entry->second.num_refs) == 0) {
        _cached.erase(entry);
    }
}

CompileCache::Token::UP
CompileCache::compile(const Function &function, PassParams pass_params)
{
    Token::UP token;
    CompileTask::UP task;
    vespalib::string key = gen_key(function, pass_params);
    {
        std::lock_guard<std::mutex> guard(_lock);
        auto pos = _cached.find(key);
        if (pos != _cached.end()) {
            ++(pos->second.num_refs);
            token = std::make_unique<Token>(pos, Token::ctor_tag());
        } else {
            auto res = _cached.emplace(std::move(key), Value::ctor_tag());
            assert(res.second);
            token = std::make_unique<Token>(res.first, Token::ctor_tag());
            ++(res.first->second.num_refs);
            task = std::make_unique<CompileTask>(function, pass_params,
                    std::make_unique<Token>(res.first, Token::ctor_tag()));
            if (_executor != nullptr) {
                task = _executor->execute(std::move(task));
            }
        }
    }
    if (task) {
        task->run();
    }
    return token;
}

void
CompileCache::attach_executor(Executor &executor)
{
    std::lock_guard<std::mutex> guard(_lock);
    _executor = &executor;
}

void
CompileCache::detach_executor()
{
    std::lock_guard<std::mutex> guard(_lock);
    _executor = nullptr;
}

size_t
CompileCache::num_cached()
{
    std::lock_guard<std::mutex> guard(_lock);
    return _cached.size();
}

size_t
CompileCache::count_refs()
{
    std::lock_guard<std::mutex> guard(_lock);
    size_t refs = 0;
    for (const auto &entry: _cached) {
        refs += entry.second.num_refs;
    }
    return refs;
}

size_t
CompileCache::count_pending()
{
    std::lock_guard<std::mutex> guard(_lock);
    size_t pending = 0;
    for (const auto &entry: _cached) {
        if (entry.second.compiled_function.get() == nullptr) {
            ++pending;
        }
    }
    return pending;
}

void
CompileCache::CompileTask::run()
{
    auto &entry = token->_entry->second;
    auto result = std::make_unique<CompiledFunction>(*function, pass_params);
    std::lock_guard<std::mutex> guard(_lock);
    entry.compiled_function = std::move(result);
    entry.cf.store(entry.compiled_function.get(), std::memory_order_release);
    entry.cond.notify_all();
}

} // namespace vespalib::eval
} // namespace vespalib
