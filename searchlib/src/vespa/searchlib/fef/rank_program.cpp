// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rank_program.h"
#include "featureoverrider.h"
#include <vespa/vespalib/locale/c.h>
#include <vespa/vespalib/stllike/hash_set.hpp>
#include <vespa/vespalib/util/size_literals.h>
#include <algorithm>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".fef.rankprogram");

using vespalib::Stash;

namespace search::fef {

using MappedValues = std::map<const NumberOrObject *, LazyValue>;

namespace {

struct Override
{
    BlueprintResolver::FeatureRef ref;
    feature_t                     value;

    Override(const BlueprintResolver::FeatureRef &r, feature_t v) noexcept
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

     void visitProperty(const Property::Value & key,
                        const Property & values) override
    {
        auto pos = feature_map.find(key);
        if (pos != feature_map.end()) {
            overrides.emplace_back(pos->second, vespalib::locale::c::strtod(values.get().c_str(), nullptr));
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

class StashSelector {
private:
    Stash &_primary;
    Stash &_secondary;
    bool _use_primary;
    Stash::Mark _primary_mark;
public:
    StashSelector(Stash &primary, Stash &secondary)
        : _primary(primary), _secondary(secondary),
          _use_primary(true), _primary_mark(primary.mark()) {}
    Stash &get() const { return _use_primary ? _primary : _secondary; }
    void use_secondary() {
        assert(_use_primary);
        _use_primary = false;
        _primary.revert(_primary_mark);
    }
};

} // namespace search::fef::<unnamed>

bool
RankProgram::check_const(FeatureExecutor *executor, const std::vector<BlueprintResolver::FeatureRef> &inputs) const
{
    if (!executor->isPure()) {
        return false;
    }
    for (const auto &ref: inputs) {
        if (!check_const(_executors[ref.executor]->outputs().get_raw(ref.output))) {
            return false;
        }
    }
    return true;    
}

void
RankProgram::run_const(FeatureExecutor *executor)
{
    executor->lazy_execute(1);
    const auto &outputs = executor->outputs();
    for (size_t out_idx = 0; out_idx < outputs.size(); ++out_idx) {
        _is_const.insert(outputs.get_raw(out_idx));
    }
}

void
RankProgram::unbox(BlueprintResolver::FeatureRef seed, const MatchData &md)
{
    FeatureExecutor *input_executor = _executors[seed.executor];
    const NumberOrObject *input_value = input_executor->outputs().get_raw(seed.output);
    vespalib::ArrayRef<NumberOrObject> outputs = _hot_stash.create_array<NumberOrObject>(1);
    if (check_const(input_value)) {
        outputs[0].as_number = input_value->as_object.get().as_double();
        _unboxed_seeds.emplace(input_value, LazyValue(&outputs[0]));
    } else {
        vespalib::ArrayRef<LazyValue> inputs = _hot_stash.create_array<LazyValue>(1, input_value, input_executor);
        FeatureExecutor &unboxer = _hot_stash.create<UnboxingExecutor>();        
        unboxer.bind_inputs(inputs);
        unboxer.bind_outputs(outputs);
        unboxer.bind_match_data(md);
        _unboxed_seeds.emplace(input_value, LazyValue(&outputs[0], &unboxer));
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
        bool is_object = specs[ref.executor].output_types[ref.output].is_object();
        FeatureExecutor *executor = _executors[ref.executor];
        const NumberOrObject *raw_value = executor->outputs().get_raw(ref.output);
        LazyValue lazy_value = check_const(raw_value) ? LazyValue(raw_value) : LazyValue(raw_value, executor);
        if (is_object && unbox_seeds) {
            auto pos = _unboxed_seeds.find(raw_value);
            if (pos != _unboxed_seeds.end()) {
                lazy_value = pos->second;
                is_object = false;
            }
        }
        result.add(name, lazy_value, is_object);
    }
    return result;
}

RankProgram::RankProgram(BlueprintResolver::SP resolver)
    : _resolver(std::move(resolver)),
      _hot_stash(32_Ki),
      _cold_stash(),
      _executors(),
      _unboxed_seeds(),
      _is_const()
{
}

RankProgram::~RankProgram() = default;

void
RankProgram::setup(const MatchData &md,
                   const IQueryEnvironment &queryEnv,
                   const Properties &featureOverrides)
{
    assert(_executors.empty());
    std::vector<Override> overrides = prepare_overrides(_resolver->getFeatureMap(), featureOverrides);
    auto override = overrides.begin();
    auto override_end = overrides.end();

    const auto &specs = _resolver->getExecutorSpecs();
    _executors.reserve(specs.size());
    _is_const.resize(specs.size()*2); // Reserve space in hashmap for executors to be const
    for (uint32_t i = 0; i < specs.size(); ++i) {
        vespalib::ArrayRef<NumberOrObject> outputs = _hot_stash.create_array<NumberOrObject>(specs[i].output_types.size());
        StashSelector stash(_hot_stash, _cold_stash);
        FeatureExecutor *executor = &(specs[i].blueprint->createExecutor(queryEnv, stash.get()));
        bool is_const = check_const(executor, specs[i].inputs);
        if (is_const) {
            stash.use_secondary();
            executor = &(specs[i].blueprint->createExecutor(queryEnv, stash.get()));
            is_const = executor->isPure();
        }
        size_t num_inputs = specs[i].inputs.size();
        vespalib::ArrayRef<LazyValue> inputs = stash.get().create_array<LazyValue>(num_inputs, nullptr);
        for (size_t input_idx = 0; input_idx < num_inputs; ++input_idx) {
            auto ref = specs[i].inputs[input_idx];
            FeatureExecutor *input_executor = _executors[ref.executor];
            const NumberOrObject *input_value = input_executor->outputs().get_raw(ref.output);
            if (check_const(input_value)) {
                inputs[input_idx] = LazyValue(input_value);
            } else {
                inputs[input_idx] = LazyValue(input_value, input_executor);
            }
        }
        for (; (override < override_end) && (override->ref.executor == i); ++override) {
            FeatureExecutor *tmp = executor;
            executor = &(stash.get().create<FeatureOverrider>(*tmp, override->ref.output, override->value));
        }
        executor->bind_inputs(inputs);
        executor->bind_outputs(outputs);
        executor->bind_match_data(md);
        _executors.push_back(executor);
        if (is_const) {
            run_const(executor);
        }
    }
    for (const auto &seed_entry: _resolver->getSeedMap()) {
        auto seed = seed_entry.second;
        if (specs[seed.executor].output_types[seed.output].is_object()) {
            unbox(seed, md);
        }
    }
    assert(_executors.size() == specs.size());
    LOG(debug, "Num executors = %ld, hot stash = %ld, cold stash = %ld, match data fields = %d",
               _executors.size(), _hot_stash.count_used(), _cold_stash.count_used(), md.getNumTermFields());
    if (LOG_WOULD_LOG(debug)) {
        vespalib::hash_map<vespalib::string, size_t> executorStats;
        for (const FeatureExecutor * executor : _executors) {
            vespalib::string name = executor->getClassName();
            if (executorStats.find(name) == executorStats.end()) {
                executorStats[name] = 1;
            } else {
                executorStats[name]++;
            }
        }
        for (const auto & stat : executorStats) {
            LOG(debug, "There are %ld executors of type %s", stat.second, stat.first.c_str());
        }
    }
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

}
