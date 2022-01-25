// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compile_cache.h"
#include <vespa/eval/eval/key_gen.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <thread>

namespace vespalib::eval {

std::mutex CompileCache::_lock{};
CompileCache::Map CompileCache::_cached{};
uint64_t CompileCache::_executor_tag{0};
std::vector<std::pair<uint64_t,std::shared_ptr<Executor>>> CompileCache::_executor_stack{};

const CompiledFunction &
CompileCache::Value::wait_for_result()
{
    std::unique_lock<std::mutex> guard(result->lock);
    result->cond.wait(guard, [this](){ return bool(result->compiled_function); });
    return *(result->compiled_function);
}

void
CompileCache::release(Map::iterator entry)
{
    std::lock_guard<std::mutex> guard(_lock);
    if (--(entry->second.num_refs) == 0) {
        _cached.erase(entry);
    }
}

uint64_t
CompileCache::attach_executor(std::shared_ptr<Executor> executor)
{
    std::lock_guard<std::mutex> guard(_lock);
    _executor_stack.emplace_back(++_executor_tag, std::move(executor));
    return _executor_tag;
}

void
CompileCache::detach_executor(uint64_t tag)
{
    std::lock_guard<std::mutex> guard(_lock);
    auto &list = _executor_stack;
    list.erase(std::remove_if(list.begin(), list.end(),
                              [tag](const auto &a){ return (a.first == tag); }),
               list.end());
};

CompileCache::Token::UP
CompileCache::compile(const Function &function, PassParams pass_params)
{
    Token::UP token;
    Executor::Task::UP task;
    std::shared_ptr<Executor> executor;
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
            task = std::make_unique<CompileTask>(function, pass_params, res.first->second.result);
            task = CpuUsage::wrap(std::move(task), CpuUsage::Category::SETUP);
            if (!_executor_stack.empty()) {
                executor = _executor_stack.back().second;
            }
        }
    }
    if (executor) {
        task = executor->execute(std::move(task));
    }
    if (task) {
        std::thread([&task](){ task.get()->run(); }).join();
    }
    return token;
}

void
CompileCache::wait_pending()
{
    std::vector<Token::UP> pending;
    {
        std::lock_guard<std::mutex> guard(_lock);
        for (auto entry = _cached.begin(); entry != _cached.end(); ++entry) {
            if (entry->second.result->cf.load(std::memory_order_acquire) == nullptr) {
                ++(entry->second.num_refs);
                pending.push_back(std::make_unique<Token>(entry, Token::ctor_tag()));
            }
        }
    }
    {
        for (const auto &token: pending) {
            const CompiledFunction &fun = token->get();
            (void) fun;
        }
    }
}

size_t
CompileCache::num_cached()
{
    std::lock_guard<std::mutex> guard(_lock);
    return _cached.size();
}

size_t
CompileCache::num_bound()
{
    std::lock_guard<std::mutex> guard(_lock);
    return _executor_stack.size();
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
        if (entry.second.result->cf.load(std::memory_order_acquire) == nullptr) {
            ++pending;
        }
    }
    return pending;
}

void
CompileCache::CompileTask::run()
{
    auto compiled = std::make_unique<CompiledFunction>(*function, pass_params);
    std::lock_guard<std::mutex> guard(result->lock);
    result->compiled_function = std::move(compiled);
    result->cf.store(result->compiled_function.get(), std::memory_order_release);
    result->cond.notify_all();
}

}
