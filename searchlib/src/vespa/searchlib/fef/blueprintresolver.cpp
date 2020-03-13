// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprintresolver.h"
#include "blueprintfactory.h"
#include "featurenameparser.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <stack>
#include <cassert>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".fef.blueprintresolver");

namespace search::fef {

namespace {

static const size_t MAX_TRACE_SIZE = 16;

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
        ~FrameGuard() {
            stack.back().spec.blueprint->detach_dependency_handler();
            stack.pop_back();
        }
    };

    const BlueprintFactory  &factory;
    const IIndexEnvironment &index_env;
    Stack                    resolve_stack;
    ExecutorSpecList        &spec_list;
    FeatureMap              &feature_map;
    std::set<vespalib::string> setup_set;
    std::set<vespalib::string> failed_set;

    Compiler(const BlueprintFactory &factory_in,
             const IIndexEnvironment &index_env_in,
             ExecutorSpecList &spec_list_out,
             FeatureMap &feature_map_out)
        : factory(factory_in),
          index_env(index_env_in),
          resolve_stack(),
          spec_list(spec_list_out),
          feature_map(feature_map_out),
          setup_set(),
          failed_set() {}
    ~Compiler();

    Frame &self() { return resolve_stack.back(); }
    bool failed() const { return !failed_set.empty(); }

    FeatureRef fail(const vespalib::string &feature_name, const vespalib::string &reason, bool skip_self = false) {
        if (failed_set.count(feature_name) == 0) {
            failed_set.insert(feature_name);
            LOG(warning, "invalid rank feature '%s': %s", feature_name.c_str(), reason.c_str());
            size_t trace_size = 0;
            for (size_t i = resolve_stack.size(); i > 0; --i) {
                const auto &frame = resolve_stack[i - 1];
                failed_set.insert(frame.parser.featureName());
                if (!skip_self || (&frame != &self())) {
                    if (++trace_size <= MAX_TRACE_SIZE) {
                        LOG(warning, "  ... needed by rank feature '%s'", frame.parser.featureName().c_str());
                    }
                }
            }
            if (trace_size > MAX_TRACE_SIZE) {
                LOG(warning, "  ... (%zu more)", (trace_size - MAX_TRACE_SIZE));
            }
        }
        return FeatureRef();
    }

    void fail_self(const vespalib::string &reason) {
        fail(self().parser.featureName(), reason, true);
    }

    FeatureRef verify_type(const FeatureNameParser &parser, FeatureRef ref, Accept accept_type) {
        const auto &spec = spec_list[ref.executor];
        bool is_object = spec.output_types[ref.output].is_object();
        if (!is_compatible(is_object, accept_type)) {
            return fail(parser.featureName(),
                        vespalib::make_string("output '%s' has wrong type: was %s, expected %s",
                                parser.output().c_str(), type_str(is_object), accept_type_str(accept_type)));
        }
        return ref;
    }

    void setup_executor(const FeatureNameParser &parser) {
        if (setup_set.count(parser.executorName()) == 0) {
            setup_set.insert(parser.executorName());
            if (Blueprint::SP blueprint = factory.createBlueprint(parser.baseName())) {
                resolve_stack.emplace_back(std::move(blueprint), parser);
                FrameGuard frame_guard(resolve_stack);
                self().spec.blueprint->setName(parser.executorName());
                self().spec.blueprint->attach_dependency_handler(*this);
                if (!self().spec.blueprint->setup(index_env, parser.parameters())) {
                    fail_self("invalid parameters");
                }
                if (parser.output().empty() && self().spec.output_types.empty()) {
                    fail_self("has no output value");
                }
                spec_list.push_back(self().spec); // keep all feature_map refs valid
            } else {
                fail(parser.featureName(),
                     vespalib::make_string("unknown basename: '%s'", parser.baseName().c_str()));
            }
        }
    }

    FeatureRef resolve_feature(const vespalib::string &feature_name, Accept accept_type) {
        FeatureNameParser parser(feature_name);
        if (!parser.valid()) {
            return fail(feature_name, "malformed name");
        }
        if (failed_set.count(parser.featureName()) > 0) {
            return FeatureRef();
        }
        auto old_feature = feature_map.find(parser.featureName());
        if (old_feature != feature_map.end()) {
            return verify_type(parser, old_feature->second, accept_type);
        }
        if ((resolve_stack.size() + 1) > BlueprintResolver::MAX_DEP_DEPTH) {
            return fail(parser.featureName(), "dependency graph too deep");
        }
        for (const Frame &frame: resolve_stack) {
            if (frame.parser.executorName() == parser.executorName()) {
                return fail(parser.featureName(), "dependency cycle detected");
            }
        }
        setup_executor(parser);
        auto new_feature = feature_map.find(parser.featureName());
        if (new_feature != feature_map.end()) {
            return verify_type(parser, new_feature->second, accept_type);
        }
        return fail(parser.featureName(),
                    vespalib::make_string("unknown output: '%s'", parser.output().c_str()));
    }

    std::optional<FeatureType> resolve_input(const vespalib::string &feature_name, Accept accept_type) override {
        assert(self().spec.output_types.empty()); // require: 'resolve inputs' before 'define outputs'
        auto ref = resolve_feature(feature_name, accept_type);
        if (!ref.valid()) {
            // fail silently here to avoid mutiple traces for the same root error
            failed_set.insert(self().parser.featureName());
            return std::nullopt;
        }
        self().spec.inputs.push_back(ref);
        return spec_list[ref.executor].output_types[ref.output];
    }

    void define_output(const vespalib::string &output_name, FeatureType type) override {
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
        self().spec.output_types.push_back(std::move(type));
    }

    void fail(const vespalib::string &msg) override {
        fail_self(msg);
    }
};

Compiler::~Compiler() = default;

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
        if (compiler.failed()) {
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
