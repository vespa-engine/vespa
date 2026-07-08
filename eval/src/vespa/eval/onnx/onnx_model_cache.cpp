// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onnx_model_cache.h"

namespace vespalib::eval {

std::mutex          OnnxModelCache::_lock{};
OnnxModelCache::Map OnnxModelCache::_cached{};

void OnnxModelCache::release(Map::iterator entry) {
    std::lock_guard<std::mutex> guard(_lock);
    if (--(entry->second.num_refs) == 0) {
        _cached.erase(entry);
    }
}

OnnxModelCache::Token::UP OnnxModelCache::load(const std::string& model_file, Onnx::Optimize optimize) {
    std::lock_guard<std::mutex> guard(_lock);
    Key                         key{model_file, optimize};
    auto                        pos = _cached.find(key);
    if (pos == _cached.end()) {
        auto model = std::make_unique<Onnx>(model_file, optimize);
        auto res = _cached.emplace(std::move(key), std::move(model));
        assert(res.second);
        pos = res.first;
    }
    return std::make_unique<Token>(pos, ctor_tag());
}

size_t OnnxModelCache::num_cached() {
    std::lock_guard<std::mutex> guard(_lock);
    return _cached.size();
}

size_t OnnxModelCache::count_refs() {
    std::lock_guard<std::mutex> guard(_lock);
    size_t                      refs = 0;
    for (const auto& entry : _cached) {
        refs += entry.second.num_refs;
    }
    return refs;
}

} // namespace vespalib::eval
