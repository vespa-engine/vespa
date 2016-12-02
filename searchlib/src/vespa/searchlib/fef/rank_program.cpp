// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fef.rank_program");
#include "rank_program.h"
#include "featureoverrider.h"
#include <algorithm>

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
    UnboxingExecutor(SharedInputs &shared_inputs,
                     FeatureHandle old_feature,
                     FeatureHandle new_feature)
    {
        bind_shared_inputs(shared_inputs);
        addInput(old_feature);
        bindOutput(new_feature);
    }
    bool isPure() override { return true; }
    void execute(search::fef::MatchData &md) override {
        double number_value = md.resolve_object_feature(inputs()[0])->get().as_double();
        *md.resolveFeature(outputs()[0]) = number_value;
    }
};

} // namespace search::fef::<unnamed>

void
RankProgram::add_unboxing_executors(MatchDataLayout &my_mdl)
{
    const auto &specs = _resolver->getExecutorSpecs();
    for (const auto &seed_entry: _resolver->getSeedMap()) {
        auto seed = seed_entry.second;
        if (specs[seed.executor].output_types[seed.output]) {
            FeatureHandle old_handle = _executors[seed.executor]->outputs()[seed.output];
            FeatureHandle new_handle = my_mdl.allocFeature(false);
            _executors.emplace_back(&_stash.create<UnboxingExecutor>(_shared_inputs, old_handle, new_handle));
            _unboxed_seeds[seed_entry.first] = std::make_pair(old_handle, new_handle);
        }
    }
}

void
RankProgram::compile()
{
    MatchData &md = match_data();
    std::vector<bool> is_calculated(md.getNumFeatures(), false);
    for (size_t i = 0; i < _executors.size(); ++i) {
        FeatureExecutor &executor = *_executors[i];
        bool is_const = executor.isPure();
        const auto &inputs = executor.inputs();
        for (size_t in_idx = 0; is_const && (in_idx < inputs.size()); ++in_idx) { 
            is_const &= is_calculated[inputs[in_idx]];
        }
        if (is_const) {
            executor.execute(md);
            const auto &outputs = executor.outputs();
            for (size_t out_idx = 0; out_idx < outputs.size(); ++out_idx) {
                is_calculated[outputs[out_idx]] = true;
            }
        } else {
            _program.push_back(&executor);
        }
    }
}

RankProgram::RankProgram(BlueprintResolver::SP resolver)
    : _resolver(resolver),
      _shared_inputs(),
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
    MatchDataLayout my_mdl(mdl_in);
    std::vector<Override> overrides = prepare_overrides(_resolver->getFeatureMap(), featureOverrides);
    auto override = overrides.begin();
    auto override_end = overrides.end();

    const auto &specs = _resolver->getExecutorSpecs();
    _executors.reserve(specs.size());
    for (uint32_t i = 0; i < specs.size(); ++i) {
        FeatureExecutor *executor = &(specs[i].blueprint->createExecutor(queryEnv, _stash));
        assert(executor);
        executor->bind_shared_inputs(_shared_inputs);
        for (; (override < override_end) && (override->ref.executor == i); ++override) {
            FeatureExecutor *tmp = executor;
            executor = &(_stash.create<FeatureOverrider>(*tmp, override->ref.output, override->value));
            executor->bind_shared_inputs(_shared_inputs);
        }
        for (auto ref: specs[i].inputs) {
            executor->addInput(_executors[ref.executor]->outputs()[ref.output]);
        }
        executor->inputs_done();
        uint32_t out_cnt = specs[i].output_types.size();
        for (uint32_t out_idx = 0; out_idx < out_cnt; ++out_idx) {
            executor->bindOutput(my_mdl.allocFeature(specs[i].output_types[out_idx]));
        }
        executor->outputs_done();
        _executors.push_back(executor);
    }
    add_unboxing_executors(my_mdl);
    _match_data = my_mdl.createMatchData();
    compile();
}

namespace {

template <typename Each>
void extract_handles(const BlueprintResolver::FeatureMap &features,
                     const std::vector<FeatureExecutor *> &executors,
                     const Each &each)
{
    each.reserve(features.size());
    for (const auto &entry: features) {
        auto ref = entry.second;
        FeatureHandle handle = executors[ref.executor]->outputs()[ref.output];
        each.process(entry.first, handle);
    }
}

struct RawHandleCollector {
    std::vector<vespalib::string> &names;
    std::vector<FeatureHandle> &handles;
    RawHandleCollector(std::vector<vespalib::string> &names_in,
                       std::vector<FeatureHandle> &handles_in)
        : names(names_in), handles(handles_in) {}
    void reserve(size_t size) const {
        names.reserve(size);
        handles.reserve(size);
    }
    void process(const vespalib::string &name, FeatureHandle handle) const {
        names.push_back(name);
        handles.push_back(handle);
    }
};

struct MappedHandleCollector {
    typedef std::map<vespalib::string, std::pair<FeatureHandle, FeatureHandle> > MappedFeatures;
    RawHandleCollector collector;
    const MappedFeatures &mapped;
    MappedHandleCollector(std::vector<vespalib::string> &names,
                          std::vector<FeatureHandle> &handles,
                          const MappedFeatures &mapped_in)
        : collector(names, handles), mapped(mapped_in) {}
    void reserve(size_t size) const { collector.reserve(size); }
    void process(const vespalib::string &name, FeatureHandle handle) const {
        auto pos = mapped.find(name);
        if (pos == mapped.end()) {
            collector.process(name, handle);
        } else {
            assert(handle == pos->second.first);
            collector.process(name, pos->second.second);
        }
    }
};

}

void
RankProgram::get_seed_handles(std::vector<vespalib::string> &names_out,
                              std::vector<FeatureHandle> &handles_out,
                              bool unbox_seeds) const
{
    if (unbox_seeds && !_unboxed_seeds.empty()) {
        extract_handles(_resolver->getSeedMap(), _executors, MappedHandleCollector(names_out, handles_out, _unboxed_seeds));
    } else {
        extract_handles(_resolver->getSeedMap(), _executors, RawHandleCollector(names_out, handles_out));
    }
}

void
RankProgram::get_all_feature_handles(std::vector<vespalib::string> &names_out,
                                     std::vector<FeatureHandle> &handles_out,
                                     bool unbox_seeds) const
{
    if (unbox_seeds && !_unboxed_seeds.empty()) {
        extract_handles(_resolver->getFeatureMap(), _executors, MappedHandleCollector(names_out, handles_out, _unboxed_seeds));
    } else {
        extract_handles(_resolver->getFeatureMap(), _executors, RawHandleCollector(names_out, handles_out));
    }
}

} // namespace fef
} // namespace search
