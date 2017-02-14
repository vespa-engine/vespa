// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fef.rank_program");
#include "rank_program.h"
#include "featureoverrider.h"
#include <algorithm>
#include <set>

using vespalib::Stash;

namespace search {
namespace fef {

using MappedValues = std::map<const NumberOrObject *, const NumberOrObject *>;

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
    const NumberOrObject &input;
    NumberOrObject &output;
    UnboxingExecutor(const NumberOrObject &input_in, NumberOrObject &output_in)
        : input(input_in), output(output_in) {}
    void execute(uint32_t) override { output.as_number = input.as_object.get().as_double(); }
};

class Features {
private:
    vespalib::ArrayRef<NumberOrObject> _features;
    size_t                             _used;
public:
    explicit Features(vespalib::ArrayRef<NumberOrObject> features)
        : _features(features), _used(0) {}
    vespalib::ArrayRef<NumberOrObject> alloc(size_t cnt) {
        assert((_used + cnt) <= _features.size());
        NumberOrObject *begin = &_features[_used];
        _used += cnt;
        return vespalib::ArrayRef<NumberOrObject>(begin, cnt);
    }
    bool is_full() const { return (_used == _features.size()); }
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

class ProgramBuilder {
private:
    std::vector<FeatureExecutor *> _program;
    std::set<const NumberOrObject *> _is_calculated;
public:
    ProgramBuilder() : _program(), _is_calculated() {}
    bool is_calculated(const NumberOrObject *raw_value) const {
        return (_is_calculated.count(raw_value) == 1);
    }
    void add(FeatureExecutor *executor, bool is_const) {
        if (is_const) {
            executor->execute(1);
            const auto &outputs = executor->outputs();
            for (size_t out_idx = 0; out_idx < outputs.size(); ++out_idx) {
                _is_calculated.insert(outputs.get_raw(out_idx));
            }
        } else {
            _program.push_back(executor);
        }
    }
    void unbox(const NumberOrObject &input, NumberOrObject &output, Stash &stash) {
        if (is_calculated(&input)) {
            output.as_number = input.as_object.get().as_double();
        } else {
            _program.push_back(&stash.create<UnboxingExecutor>(input, output));
        }
    }
    const std::vector<FeatureExecutor *> &get() const { return _program; }
};

bool executor_is_const(FeatureExecutor *executor,
                       const ProgramBuilder &program,
                       const std::vector<FeatureExecutor *> &executors,
                       const std::vector<BlueprintResolver::FeatureRef> &inputs)
{
    if (!executor->isPure()) {
        return false;
    }
    for (const auto &ref: inputs) {
        if (!program.is_calculated(executors[ref.executor]->outputs().get_raw(ref.output))) {
            return false;
        }
    }
    return true;
}

size_t count_features(const BlueprintResolver &resolver) {
    size_t cnt = 0;
    const auto &specs = resolver.getExecutorSpecs();
    for (const auto &entry: specs) {
        cnt += entry.output_types.size(); // normal outputs
    }
    for (const auto &seed_entry: resolver.getSeedMap()) {
        auto seed = seed_entry.second;
        if (specs[seed.executor].output_types[seed.output]) {
            ++cnt; // unboxed seeds
        }
    }
    return cnt;
}

FeatureResolver resolve(const BlueprintResolver::FeatureMap &features,
                        const BlueprintResolver::ExecutorSpecList &specs,
                        const std::vector<FeatureExecutor *> &executors,
                        const MappedValues &unboxed_seeds,
                        bool unbox_seeds)
{
    FeatureResolver result(features.size());
    for (const auto &entry: features) {
        const auto &name = entry.first;
        auto ref = entry.second;
        bool is_object = specs[ref.executor].output_types[ref.output];
        const NumberOrObject *raw_value = executors[ref.executor]->outputs().get_raw(ref.output);
        if (is_object && unbox_seeds) {
            auto pos = unboxed_seeds.find(raw_value);
            if (pos != unboxed_seeds.end()) {
                raw_value = pos->second;
                is_object = false;
            }
        }
        result.add(name, raw_value, is_object);
    }
    return result;
}

} // namespace search::fef::<unnamed>

RankProgram::RankProgram(BlueprintResolver::SP resolver)
    : _resolver(resolver),
      _match_data(),
      _hot_stash(32768),
      _cold_stash(),
      _program(),
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

    ProgramBuilder program;
    Features features(_hot_stash.create_array<NumberOrObject>(count_features(*_resolver)));
    const auto &specs = _resolver->getExecutorSpecs();
    for (uint32_t i = 0; i < specs.size(); ++i) {
        StashSelector stash(_hot_stash, _cold_stash);
        FeatureExecutor *executor = &(specs[i].blueprint->createExecutor(queryEnv, stash.get()));
        bool is_const = executor_is_const(executor, program, _executors, specs[i].inputs);
        if (is_const) {
            stash.use_secondary();
            executor = &(specs[i].blueprint->createExecutor(queryEnv, stash.get()));
            is_const = executor->isPure();
        }
        size_t num_inputs = specs[i].inputs.size();
        vespalib::ArrayRef<const NumberOrObject *> inputs = stash.get().create_array<const NumberOrObject *>(num_inputs);
        for (size_t input_idx = 0; input_idx < num_inputs; ++input_idx) {
            auto ref = specs[i].inputs[input_idx];
            inputs[input_idx] = _executors[ref.executor]->outputs().get_raw(ref.output);
        }
        vespalib::ArrayRef<NumberOrObject> outputs = features.alloc(specs[i].output_types.size());
        for (; (override < override_end) && (override->ref.executor == i); ++override) {
            FeatureExecutor *tmp = executor;
            executor = &(stash.get().create<FeatureOverrider>(*tmp, override->ref.output, override->value));
        }
        executor->bind_inputs(inputs);
        executor->bind_outputs(outputs);
        executor->bind_match_data(*_match_data);
        _executors.push_back(executor);
        program.add(executor, is_const);
    }
    for (const auto &seed_entry: _resolver->getSeedMap()) {
        auto seed = seed_entry.second;
        if (specs[seed.executor].output_types[seed.output]) {
            const NumberOrObject &input = *_executors[seed.executor]->outputs().get_raw(seed.output);
            NumberOrObject &output = features.alloc(1)[0];
            _unboxed_seeds[&input] = &output;
            program.unbox(input, output, _hot_stash);
        }
    }
    _program = _hot_stash.copy_array<FeatureExecutor *>(program.get());
    assert(_executors.size() == specs.size());
    assert(features.is_full());
}

FeatureResolver
RankProgram::get_seeds(bool unbox_seeds) const
{
    return resolve(_resolver->getSeedMap(), _resolver->getExecutorSpecs(), _executors, _unboxed_seeds, unbox_seeds);
}

FeatureResolver
RankProgram::get_all_features(bool unbox_seeds) const
{
    return resolve(_resolver->getFeatureMap(), _resolver->getExecutorSpecs(), _executors, _unboxed_seeds, unbox_seeds);
}

} // namespace fef
} // namespace search
