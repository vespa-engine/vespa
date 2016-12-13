// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fef.rank_program");
#include "rank_program.h"
#include "featureoverrider.h"
#include <algorithm>
#include <set>

namespace search {
namespace fef {

namespace {

struct Override
{
    BlueprintResolver::FeatureRef ref;
    feature_t                     value;

    Override(const BlueprintResolver::FeatureRef &r, feature_t v)
        : ref(r), value(v) {}

    bool operator<(const Override &rhs) const {
        return (ref.executor < rhs.ref.executor);
    }
};

struct OverrideVisitor : public IPropertiesVisitor
{
    const BlueprintResolver::FeatureMap &feature_map;
    std::vector<Override>               &overrides;

    OverrideVisitor(const BlueprintResolver::FeatureMap &feature_map_in,
                    std::vector<Override> &overrides_out)
        : feature_map(feature_map_in), overrides(overrides_out) {}

    virtual void visitProperty(const Property::Value & key,
                               const Property & values)
    {
        auto pos = feature_map.find(key);
        if (pos != feature_map.end()) {
            overrides.push_back(Override(pos->second, strtod(values.get().c_str(), nullptr)));
        }
    }
};

std::vector<Override> prepare_overrides(const BlueprintResolver::FeatureMap &feature_map,
                                        const Properties &featureOverrides)
{
    std::vector<Override> overrides;
    overrides.reserve(featureOverrides.numValues());
    OverrideVisitor visitor(feature_map, overrides);
    featureOverrides.visitProperties(visitor);
    std::sort(overrides.begin(), overrides.end());
    return overrides;
}

struct UnboxingExecutor : FeatureExecutor {
    bool isPure() override { return true; }
    void execute(uint32_t) override {
        outputs().set_number(0, inputs().get_object(0).get().as_double());
    }
};

} // namespace search::fef::<unnamed>

size_t
RankProgram::count_features() const
{
    size_t cnt = 0;
    const auto &specs = _resolver->getExecutorSpecs();
    for (const auto &entry: specs) {
        cnt += entry.output_types.size(); // normal outputs
    }
    for (const auto &seed_entry: _resolver->getSeedMap()) {
        auto seed = seed_entry.second;
        if (specs[seed.executor].output_types[seed.output]) {
            ++cnt; // unboxed seeds
        }
    }
    return cnt;
}

void
RankProgram::add_unboxing_executors(vespalib::ArrayRef<NumberOrObject> features, size_t feature_offset, size_t total_features)
{
    const auto &specs = _resolver->getExecutorSpecs();
    for (const auto &seed_entry: _resolver->getSeedMap()) {
        auto seed = seed_entry.second;
        if (specs[seed.executor].output_types[seed.output]) {
            vespalib::ArrayRef<const NumberOrObject *> inputs = _stash.create_array<const NumberOrObject *>(1);
            inputs[0] = _executors[seed.executor]->outputs().get_raw(seed.output);
            vespalib::ArrayRef<NumberOrObject> outputs(&features[feature_offset++], 1);
            _unboxed_seeds[inputs[0]] = &outputs[0];
            _executors.emplace_back(&_stash.create<UnboxingExecutor>());
            _executors.back()->bind_inputs(inputs);
            _executors.back()->bind_outputs(outputs);
            _executors.back()->bind_match_data(*_match_data);            
        }
    }
    assert(feature_offset == total_features);
}

void
RankProgram::compile()
{
    std::set<const NumberOrObject *> is_calculated;
    for (size_t i = 0; i < _executors.size(); ++i) {
        FeatureExecutor &executor = *_executors[i];
        bool is_const = executor.isPure();
        const auto &inputs = executor.inputs();
        for (size_t in_idx = 0; is_const && (in_idx < inputs.size()); ++in_idx) { 
            is_const &= (is_calculated.count(inputs.get_raw(in_idx)) > 0);
        }
        if (is_const) {
            executor.execute(1);
            const auto &outputs = executor.outputs();
            for (size_t out_idx = 0; out_idx < outputs.size(); ++out_idx) {
                is_calculated.insert(outputs.get_raw(out_idx));
            }
        } else {
            _program.push_back(&executor);
        }
    }
}

FeatureResolver
RankProgram::resolve(const BlueprintResolver::FeatureMap &features, bool unbox_seeds) const
{
    FeatureResolver result(features.size());
    const auto &specs = _resolver->getExecutorSpecs();
    for (const auto &entry: features) {
        const auto &name = entry.first;
        auto ref = entry.second;
        bool is_object = specs[ref.executor].output_types[ref.output];
        const NumberOrObject *raw_value = _executors[ref.executor]->outputs().get_raw(ref.output);
        if (is_object && unbox_seeds) {
            auto pos = _unboxed_seeds.find(raw_value);
            if (pos != _unboxed_seeds.end()) {
                raw_value = pos->second;
                is_object = false;
            }
        }
        result.add(name, raw_value, is_object);
    }
    return result;
}

RankProgram::RankProgram(BlueprintResolver::SP resolver)
    : _resolver(resolver),
      _program(),
      _stash(),
      _executors(),
      _unboxed_seeds()
{
}

void
RankProgram::setup(const MatchDataLayout &mdl_in,
                   const IQueryEnvironment &queryEnv,
                   const Properties &featureOverrides)
{
    assert(_executors.empty());
    _match_data = mdl_in.createMatchData();
    std::vector<Override> overrides = prepare_overrides(_resolver->getFeatureMap(), featureOverrides);
    auto override = overrides.begin();
    auto override_end = overrides.end();

    size_t feature_offset = 0;
    size_t total_features = count_features();
    vespalib::ArrayRef<NumberOrObject> features = _stash.create_array<NumberOrObject>(total_features);
    const auto &specs = _resolver->getExecutorSpecs();
    _executors.reserve(specs.size());
    for (uint32_t i = 0; i < specs.size(); ++i) {
        size_t num_inputs = specs[i].inputs.size();
        vespalib::ArrayRef<const NumberOrObject *> inputs = _stash.create_array<const NumberOrObject *>(num_inputs);
        for (size_t input_idx = 0; input_idx < num_inputs; ++input_idx) {
            auto ref = specs[i].inputs[input_idx];
            inputs[input_idx] = _executors[ref.executor]->outputs().get_raw(ref.output);
        }
        size_t num_outputs =  specs[i].output_types.size();
        vespalib::ArrayRef<NumberOrObject> outputs(&features[feature_offset], num_outputs);
        feature_offset += num_outputs;
        FeatureExecutor *executor = &(specs[i].blueprint->createExecutor(queryEnv, _stash));
        for (; (override < override_end) && (override->ref.executor == i); ++override) {
            FeatureExecutor *tmp = executor;
            executor = &(_stash.create<FeatureOverrider>(*tmp, override->ref.output, override->value));
        }
        executor->bind_inputs(inputs);
        executor->bind_outputs(outputs);
        executor->bind_match_data(*_match_data);
        _executors.push_back(executor);
    }
    add_unboxing_executors(features, feature_offset, total_features);
    compile();
}

FeatureResolver
RankProgram::get_seeds(bool unbox_seeds) const
{
    return resolve(_resolver->getSeedMap(), unbox_seeds);
}

FeatureResolver
RankProgram::get_all_features(bool unbox_seeds) const
{
    return resolve(_resolver->getFeatureMap(), unbox_seeds);
}

} // namespace fef
} // namespace search
