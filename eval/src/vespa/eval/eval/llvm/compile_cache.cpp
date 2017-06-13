// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compile_cache.h"
#include <vespa/eval/eval/key_gen.h>
#include <thread>

namespace vespalib {
namespace eval {

std::mutex CompileCache::_lock;
CompileCache::Map CompileCache::_cached;

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
    std::lock_guard<std::mutex> guard(_lock);
    CompileContext compile_ctx(function, pass_params);
    std::thread thread(do_compile, std::ref(compile_ctx));
    thread.join();
    return std::move(compile_ctx.token);
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

void
CompileCache::do_compile(CompileContext &ctx) {
    vespalib::string key = gen_key(ctx.function, ctx.pass_params);
    auto pos = _cached.find(key);
    if (pos != _cached.end()) {
        ++(pos->second.num_refs);
        ctx.token.reset(new Token(pos));
    } else {
        auto res = _cached.emplace(std::move(key), Value(CompiledFunction(ctx.function, ctx.pass_params)));
        assert(res.second);
        ctx.token.reset(new Token(res.first));
    }
}

} // namespace vespalib::eval
} // namespace vespalib
