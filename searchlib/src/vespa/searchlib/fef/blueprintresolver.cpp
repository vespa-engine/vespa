// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprintresolver.h"
#include "blueprintfactory.h"
#include "featurenameparser.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <stack>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".fef.blueprintresolver");

namespace search::fef {

namespace {

using Accept = Blueprint::AcceptInput;

bool is_compatible(bool is_object, Accept accept_type) {
    return ((accept_type == Accept::ANY) ||
            ((accept_type == Accept::OBJECT) == (is_object)));
}

const char *type_str(bool is_object) {
    return (is_object ? "object" : "number");
}

const char *accept_type_str(Accept accept_type) {
    switch (accept_type) {
    case Accept::NUMBER: return "number";
    case Accept::OBJECT: return "object";
    case Accept::ANY:    return "any";
    }
    return "(not reached)";
}

struct Compiler : public Blueprint::DependencyHandler {
    using ExecutorSpec = BlueprintResolver::ExecutorSpec;
    using ExecutorSpecList = BlueprintResolver::ExecutorSpecList;
    using FeatureRef = BlueprintResolver::FeatureRef;
    using FeatureMap = BlueprintResolver::FeatureMap;

    struct Frame {
        ExecutorSpec spec;
        const FeatureNameParser &parser;
        Frame(Blueprint::SP blueprint, const FeatureNameParser &parser_in)
            : spec(std::move(blueprint)), parser(parser_in) {}
    };
    using Stack = std::vector<Frame>;

    struct FrameGuard {
        Stack &stack;
        explicit FrameGuard(Stack &stack_in) : stack(stack_in) {}
        ~FrameGuard() { stack.pop_back(); }
    };

    const BlueprintFactory  &factory;
    const IIndexEnvironment &index_env;
    bool                     compile_error;
    Stack                    resolve_stack;
    ExecutorSpecList        &spec_list;
    FeatureMap              &feature_map;

    Compiler(const BlueprintFactory &factory_in,
             const IIndexEnvironment &index_env_in,
             ExecutorSpecList &spec_list_out,
             FeatureMap &feature_map_out)
        : factory(factory_in),
          index_env(index_env_in),
          compile_error(false),
          resolve_stack(),
          spec_list(spec_list_out),
          feature_map(feature_map_out) {}

    Frame &self() { return resolve_stack.back(); }

    FeatureRef failed(const vespalib::string &feature_name, const vespalib::string &reason) {
        if (!compile_error) {
            LOG(warning, "invalid rank feature: '%s' (%s)", feature_name.c_str(), reason.c_str());
            for (size_t i = resolve_stack.size(); i > 0; --i) {
                const auto &frame = resolve_stack[i - 1];
                if (&frame != &self()) {
                    LOG(warning, "  ... needed by rank feature '%s'", frame.parser.featureName().c_str());
                }
            }
            compile_error = true;
        }
        return FeatureRef();
    }

    FeatureRef verify_type(const FeatureNameParser &parser, FeatureRef ref, Accept accept_type) {
        const auto &spec = spec_list[ref.executor];
        bool is_object = spec.output_types[ref.output];
        if (!is_compatible(is_object, accept_type)) {
            return failed(parser.featureName(),
                          vespalib::make_string("output '%s' has wrong type: was %s, expected %s",
                                  parser.output().c_str(), type_str(is_object), accept_type_str(accept_type)));
        }
        return ref;
    }

    FeatureRef setup_feature(const FeatureNameParser &parser, Accept accept_type) {
        Blueprint::SP blueprint = factory.createBlueprint(parser.baseName());
        if ( ! blueprint) {
            return failed(parser.featureName(),
                          vespalib::make_string("unknown basename: '%s'", parser.baseName().c_str()));
        }
        resolve_stack.emplace_back(std::move(blueprint), parser);
        FrameGuard frame_guard(resolve_stack);
        self().spec.blueprint->setName(parser.executorName());
        self().spec.blueprint->attach_dependency_handler(*this);
        if (!self().spec.blueprint->setup(index_env, parser.parameters())) {
            return failed(parser.featureName(), "invalid parameters");
        }
        if (parser.output().empty() && self().spec.output_types.empty()) {
            return failed(parser.featureName(), "has no output value");
        }
        const auto &feature = feature_map.find(parser.featureName());
        if (feature == feature_map.end()) {
            return failed(parser.featureName(),
                          vespalib::make_string("unknown output: '%s'", parser.output().c_str()));
        }
        spec_list.push_back(self().spec);
        return verify_type(parser, feature->second, accept_type);
    }

    FeatureRef resolve_feature(const vespalib::string &feature_name, Accept accept_type) {
        FeatureNameParser parser(feature_name);
        if (!parser.valid()) {
            return failed(feature_name, "malformed name");
        }
        const auto &feature = feature_map.find(parser.featureName());
        if (feature != feature_map.end()) {
            return verify_type(parser, feature->second, accept_type);
        }
        if ((resolve_stack.size() + 1) > BlueprintResolver::MAX_DEP_DEPTH) {
            return failed(parser.featureName(), "dependency graph too deep");
        }
        for (const Frame &frame: resolve_stack) {
            if (frame.parser.executorName() == parser.executorName()) {
                return failed(parser.featureName(), "dependency cycle detected");
            }
        }
        return setup_feature(parser, accept_type);
    }

    const FeatureType &resolve_input(const vespalib::string &feature_name, Accept accept_type) override {
        assert(self().spec.output_types.empty()); // require: 'resolve inputs' before 'define outputs'
        auto ref = resolve_feature(feature_name, accept_type);
        if (!ref.valid()) {
            return FeatureType::number();
        }
        self().spec.inputs.push_back(ref);
        return spec_list[ref.executor].output_types[ref.output];
    }

    void define_output(const vespalib::string &output_name, const FeatureType &type) override {
        vespalib::string feature_name = self().parser.executorName();
        if (!output_name.empty()) {
            feature_name.push_back('.');
            feature_name.append(output_name);
        }
        FeatureRef output_ref(spec_list.size(), self().spec.output_types.size());
        if (output_ref.output == 0) {
            feature_map.emplace(self().parser.executorName(), output_ref);
        }
        feature_map.emplace(feature_name, output_ref);
        self().spec.output_types.push_back(type);
    }
};

} // namespace search::fef::<unnamed>

BlueprintResolver::ExecutorSpec::ExecutorSpec(Blueprint::SP blueprint_in)
    : blueprint(std::move(blueprint_in)),
      inputs(),
      output_types()
{ }

BlueprintResolver::ExecutorSpec::~ExecutorSpec() = default;
BlueprintResolver::~BlueprintResolver() = default;

BlueprintResolver::BlueprintResolver(const BlueprintFactory &factory,
                                     const IIndexEnvironment &indexEnv)
    : _factory(factory),
      _indexEnv(indexEnv),
      _seeds(),
      _executorSpecs(),
      _featureMap(),
      _seedMap()
{
}

void
BlueprintResolver::addSeed(vespalib::stringref feature)
{
    _seeds.emplace_back(feature);
}

bool
BlueprintResolver::compile()
{
    assert(_executorSpecs.empty()); // only one compilation allowed
    Compiler compiler(_factory, _indexEnv, _executorSpecs, _featureMap);
    for (const auto &seed: _seeds) {
        auto ref = compiler.resolve_feature(seed, Blueprint::AcceptInput::ANY);
        if (compiler.compile_error) {
            return false;
        }
        _seedMap.emplace(FeatureNameParser(seed).featureName(), ref);
    }
    return true;
}

const BlueprintResolver::ExecutorSpecList &
BlueprintResolver::getExecutorSpecs() const
{
    return _executorSpecs;
}

const BlueprintResolver::FeatureMap &
BlueprintResolver::getFeatureMap() const
{
    return _featureMap;
}

const BlueprintResolver::FeatureMap &
BlueprintResolver::getSeedMap() const
{
    return _seedMap;
}

}
