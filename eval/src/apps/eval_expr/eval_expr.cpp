// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/lazy_params.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/feature_name_extractor.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/make_tensor_function.h>
#include <vespa/eval/eval/optimize_tensor_function.h>
#include <vespa/eval/eval/compile_tensor_function.h>
#include <vespa/eval/eval/test/test_io.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

using namespace vespalib::eval;

const auto &factory = FastValueBuilderFactory::get();

int usage(const char *self) {
    fprintf(stderr, "usage: %s [--verbose] <expr> [expr ...]\n", self);
    fprintf(stderr, "  Evaluate a sequence of expressions. The first expression must be\n");
    fprintf(stderr, "  self-contained (no external values). Later expressions may use the\n");
    fprintf(stderr, "  results of earlier expressions. Expressions are automatically named\n");
    fprintf(stderr, "  using single letter symbols ('a' through 'z'). Quote expressions to\n");
    fprintf(stderr, "  make sure they become separate parameters.\n");
    fprintf(stderr, "  The --verbose option may be specified to get more detailed informaion\n");
    fprintf(stderr, "  about how the various expressions are optimized.\n");
    fprintf(stderr, "example: %s \"2+2\" \"a+2\" \"a+b\"\n", self);
    fprintf(stderr, "  (a=4, b=6, c=10)\n\n");
    return 1;
}

int overflow(int cnt, int max) {
    fprintf(stderr, "error: too many expressions: %d (max is %d)\n", cnt, max);
    return 2;
}

struct Context {
    std::vector<vespalib::string> param_names;
    std::vector<ValueType>        param_types;
    std::vector<Value::UP>        param_values;
    std::vector<Value::CREF>      param_refs;
    bool                          collect_meta;
    CTFMetaData                   meta;

    Context(bool collect_meta_in) : param_names(), param_types(), param_values(), param_refs(), collect_meta(collect_meta_in), meta() {}
    ~Context();

    bool eval_next(const vespalib::string &name, const vespalib::string &expr) {
        meta = CTFMetaData();
        SimpleObjectParams params(param_refs);
        auto fun = Function::parse(param_names, expr, FeatureNameExtractor());
        if (fun->has_error()) {
            fprintf(stderr, "error: expression parse error (%s): %s\n", name.c_str(), fun->get_error().c_str());
            return false;
        }
        NodeTypes types = NodeTypes(*fun, param_types);
        ValueType res_type = types.get_type(fun->root());
        if (res_type.is_error() || !types.errors().empty()) {
            fprintf(stderr, "error: expression type issues (%s)\n", name.c_str());
            for (const auto &issue: types.errors()) {
                fprintf(stderr, "   issue: %s\n", issue.c_str());
            }
            return false;
        }
        vespalib::Stash stash;
        const TensorFunction &plain_fun = make_tensor_function(factory, fun->root(), types, stash);
        const TensorFunction &optimized = optimize_tensor_function(factory, plain_fun, stash);
        InterpretedFunction ifun(factory, optimized, collect_meta ? &meta : nullptr);
        InterpretedFunction::Context ctx(ifun);
        Value::UP result = factory.copy(ifun.eval(ctx, params));
        assert(result->type() == res_type);
        param_names.push_back(name);
        param_types.push_back(res_type);
        param_values.push_back(std::move(result));
        param_refs.emplace_back(*param_values.back());
        return true;
    }

    void print_last(bool with_name) {
        auto spec = spec_from_value(param_refs.back().get());
        if (!meta.steps.empty()) {
            if (with_name) {
                fprintf(stderr, "meta-data(%s):\n", param_names.back().c_str());
            } else {
                fprintf(stderr, "meta-data:\n");
            }
            for (const auto &step: meta.steps) {
                fprintf(stderr, "  class: %s\n", step.class_name.c_str());
                fprintf(stderr, "    symbol: %s\n", step.symbol_name.c_str());
            }
        }
        if (with_name) {
            fprintf(stdout, "%s: ", param_names.back().c_str());
        }
        if (param_types.back().is_double()) {
            fprintf(stdout, "%.32g\n", spec.as_double());
        } else {
            fprintf(stdout, "%s\n", spec.to_string().c_str());
        }
    }
};
Context::~Context() = default;

int main(int argc, char **argv) {
    bool verbose = ((argc > 1) && (vespalib::string(argv[1]) == "--verbose"));
    int expr_idx = verbose ? 2 : 1;
    int expr_cnt = (argc - expr_idx);
    int expr_max = ('z' - 'a') + 1;
    if (expr_cnt == 0) {
        return usage(argv[0]);
    }
    if (expr_cnt > expr_max) {
        return overflow(expr_cnt, expr_max);
    }
    Context ctx(verbose);
    vespalib::string name("a");
    for (int i = expr_idx; i < argc; ++i) {
        if (!ctx.eval_next(name, argv[i])) {
            return 3;
        }
        ctx.print_last(expr_cnt > 1);
        ++name[0];
    }
    return 0;
}
