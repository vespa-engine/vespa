// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprintresolver.h"
#include "blueprintfactory.h"
#include "featurenameparser.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <cassert>
#include <set>
#include <thread>

using vespalib::make_string_short::fmt;
using vespalib::ThreadStackExecutor;
using vespalib::makeLambdaTask;

namespace search::fef {

namespace {

constexpr int MAX_TRACE_SIZE = BlueprintResolver::MAX_TRACE_SIZE;
constexpr int TRACE_SKIP_POS = 10;

using Accept = Blueprint::AcceptInput;

vespalib::string describe(const vespalib::string &feature_name) {
    return BlueprintResolver::describe_feature(feature_name);
}

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
    using Errors = std::vector<vespalib::string>;

    struct FrameGuard {
        Stack &stack;
        explicit FrameGuard(Stack &stack_in) : stack(stack_in) {}
        ~FrameGuard() {
            stack.back().spec.blueprint->detach_dependency_handler();
            stack.pop_back();
        }
    };

    const BlueprintFactory     &factory;
    const IIndexEnvironment    &index_env;
    Stack                       resolve_stack;
    Errors                      errors;
    ExecutorSpecList           &spec_list;
    FeatureMap                 &feature_map;
    std::set<vespalib::string>  setup_set;
    std::set<vespalib::string>  failed_set;
    const char                 *min_stack;
    const char                 *max_stack;

    Compiler(const BlueprintFactory &factory_in,
             const IIndexEnvironment &index_env_in,
             ExecutorSpecList &spec_list_out,
             FeatureMap &feature_map_out)
        : factory(factory_in),
          index_env(index_env_in),
          resolve_stack(),
          errors(),
          spec_list(spec_list_out),
          feature_map(feature_map_out),
          setup_set(),
          failed_set(),
          min_stack(nullptr),
          max_stack(nullptr) {}
    ~Compiler();

    void probe_stack() {
        const char c = 'X';
        min_stack = (min_stack == nullptr) ? &c : std::min(min_stack, &c);
        max_stack = (max_stack == nullptr) ? &c : std::max(max_stack, &c);
    }

    int stack_usage() const {
        return (max_stack - min_stack);
    }

    Frame &self() { return resolve_stack.back(); }
    bool failed() const { return !failed_set.empty(); }

    vespalib::string make_trace(bool skip_self) {
        vespalib::string trace;
        auto pos = resolve_stack.rbegin();
        auto end = resolve_stack.rend();
        if ((pos != end) && skip_self) {
            ++pos;
        }
        size_t i = 0;
        size_t n = (end - pos);
        for (; (pos != end); ++pos, ++i) {
            failed_set.insert(pos->parser.featureName());
            bool should_trace = (n <= MAX_TRACE_SIZE);
            should_trace |= (i < TRACE_SKIP_POS);
            should_trace |= ((end - pos) < (MAX_TRACE_SIZE - TRACE_SKIP_POS));
            if (should_trace) {
                trace += fmt("  ... needed by %s\n", describe(pos->parser.featureName()).c_str());
            } else if (i == TRACE_SKIP_POS) {
                trace += fmt("  (skipped %zu entries)\n", (n - MAX_TRACE_SIZE) + 1);
            }
        }
        return trace;
    }

    FeatureRef fail(const vespalib::string &feature_name, const vespalib::string &reason, bool skip_self = false) {
        if (failed_set.count(feature_name) == 0) {
            failed_set.insert(feature_name);
            auto trace = make_trace(skip_self);
            vespalib::string msg;
            if (trace.empty()) {
                msg = fmt("invalid %s: %s", describe(feature_name).c_str(), reason.c_str());
            } else {
                msg = fmt("invalid %s: %s\n%s", describe(feature_name).c_str(), reason.c_str(), trace.c_str());
            }
            errors.emplace_back(msg);
        }
        probe_stack();
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
                        fmt("output '%s' has wrong type: was %s, expected %s",
                            parser.output().c_str(), type_str(is_object), accept_type_str(accept_type)));
        }
        probe_stack();
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
                fail(parser.featureName(), fmt("unknown basename: '%s'", parser.baseName().c_str()));
            }
        }
    }

    FeatureRef resolve_feature(const vespalib::string &feature_name, Accept accept_type) {
        auto parser = std::make_unique<FeatureNameParser>(feature_name);
        if (!parser->valid()) {
            return fail(feature_name, "malformed name");
        }
        if (failed_set.count(parser->featureName()) > 0) {
            return fail(parser->featureName(), "already failed");
        }
        auto old_feature = feature_map.find(parser->featureName());
        if (old_feature != feature_map.end()) {
            return verify_type(*parser, old_feature->second, accept_type);
        }
        if ((resolve_stack.size() + 1) > BlueprintResolver::MAX_DEP_DEPTH) {
            return fail(parser->featureName(), "dependency graph too deep");
        }
        for (const Frame &frame: resolve_stack) {
            if (frame.parser.executorName() == parser->executorName()) {
                return fail(parser->featureName(), "dependency cycle detected");
            }
        }
        setup_executor(*parser);
        auto new_feature = feature_map.find(parser->featureName());
        if (new_feature != feature_map.end()) {
            return verify_type(*parser, new_feature->second, accept_type);
        }
        return fail(parser->featureName(), fmt("unknown output: '%s'", parser->output().c_str()));
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
      _seedMap(),
      _warnings()
{
}

vespalib::string
BlueprintResolver::describe_feature(const vespalib::string &name)
{
    auto parser = std::make_unique<FeatureNameParser>(name);
    if (parser->valid() &&
        (parser->baseName() == "rankingExpression") &&
        (parser->parameters().size() == 1) &&
        parser->output().empty())
    {
        auto param = parser->parameters()[0];
        param = param.substr(0, param.find("@"));
        return fmt("function %s", param.c_str());
    }
    return fmt("rank feature %s", name.c_str());
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
    auto compile_task = makeLambdaTask([&]() {
                                   compiler.probe_stack();
                                   for (const auto &seed: _seeds) {
                                       auto ref = compiler.resolve_feature(seed, Blueprint::AcceptInput::ANY);
                                       if (compiler.failed()) {
                                           _warnings = std::move(compiler.errors);
                                           return;
                                       }
                                       _seedMap.emplace(FeatureNameParser(seed).featureName(), ref);
                                   }
                               });
    ThreadStackExecutor executor(1, 8_Mi);
    executor.execute(std::move(compile_task));
    executor.sync();
    executor.shutdown();
    size_t stack_usage = compiler.stack_usage();
    if (stack_usage > (128_Ki)) {
        _warnings.emplace_back(fmt("high stack usage: %zu bytes", stack_usage));
    }
    return !compiler.failed();
}

}
