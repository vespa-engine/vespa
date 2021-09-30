// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_lookup_table.h"
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/key_gen.h>
#include <vespa/eval/eval/llvm/compiled_function.h>

namespace vespalib::eval {

namespace {

bool step_params(std::vector<double> &params, const ValueType &type) {
    const auto &dims = type.dimensions();
    for (size_t idx = params.size(); idx-- > 0; ) {
        if (size_t(params[idx] += 1.0) < dims[idx].size) {
            return true;
        } else {
            params[idx] = 0.0;
        }
    }
    return false;
}

std::vector<uint32_t> make_index_table(const Function &idx_fun, const ValueType &type) {
    std::vector<uint32_t> result;
    result.reserve(type.dense_subspace_size());
    std::vector<double> params(type.dimensions().size(), 0.0);
    CompiledFunction cfun(idx_fun, PassParams::ARRAY);
    auto fun = cfun.get_function();
    do {
        result.push_back(uint32_t(fun(&params[0])));
    } while(step_params(params, type));
    assert(result.size() == type.dense_subspace_size());
    return result;
}

}

std::mutex IndexLookupTable::_lock{};
IndexLookupTable::Map IndexLookupTable::_cached{};

size_t
IndexLookupTable::num_cached()
{
    std::lock_guard<std::mutex> guard(_lock);
    return _cached.size();
}

size_t
IndexLookupTable::count_refs()
{
    std::lock_guard<std::mutex> guard(_lock);
    size_t refs = 0;
    for (const auto &entry: _cached) {
        refs += entry.second.num_refs;
    }
    return refs;
}

IndexLookupTable::Token::UP
IndexLookupTable::create(const Function &idx_fun, const ValueType &type)
{
    assert(type.is_dense());
    assert(idx_fun.num_params() == type.dimensions().size());
    assert(!CompiledFunction::detect_issues(idx_fun));
    auto key = type.to_spec() + gen_key(idx_fun, PassParams::ARRAY);
    {
        std::lock_guard<std::mutex> guard(_lock);
        auto pos = _cached.find(key);
        if (pos != _cached.end()) {
            ++(pos->second.num_refs);
            return std::make_unique<Token>(pos, Token::ctor_tag());
        }
    }
    // avoid holding the lock while making the index table
    auto table = make_index_table(idx_fun, type);
    {
        std::lock_guard<std::mutex> guard(_lock);
        auto pos = _cached.find(key);
        if (pos != _cached.end()) {
            ++(pos->second.num_refs);
            // in case of a race; return the same table for all callers
            return std::make_unique<Token>(pos, Token::ctor_tag());
        } else {
            auto res = _cached.emplace(std::move(key), Value::ctor_tag());
            assert(res.second);
            res.first->second.data = std::move(table);
            return std::make_unique<Token>(res.first, Token::ctor_tag());
        }
    }
}

}
