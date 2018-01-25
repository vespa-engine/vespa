// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/tensor.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/tensor_engine.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/test/test_io.h>
#include <unistd.h>

#include "generate.h"

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::slime::convenience;
using slime::JsonFormat;
using tensor::DefaultTensorEngine;

//-----------------------------------------------------------------------------

uint8_t unhex(char c) {
    if (c >= '0' && c <= '9') {
        return (c - '0');
    }
    if (c >= 'A' && c <= 'F') {
        return ((c - 'A') + 10);
    }
    TEST_ERROR("bad hex char");
    return 0;
}

void extract_data_from_string(Memory hex_dump, nbostream &data) {
    if ((hex_dump.size > 2) && (hex_dump.data[0] == '0') && (hex_dump.data[1] == 'x')) {
        for (size_t i = 2; i < (hex_dump.size - 1); i += 2) {
            data << uint8_t((unhex(hex_dump.data[i]) << 4) | unhex(hex_dump.data[i + 1]));
        }
    }
}

nbostream extract_data(const Inspector &value) {
    nbostream data;
    if (value.asString().size > 0) {
        extract_data_from_string(value.asString(), data);
    } else {
        Memory buf = value.asData();
        data.write(buf.data, buf.size);
    }
    return data;
}

//-----------------------------------------------------------------------------

void insert_value(Cursor &cursor, const vespalib::string &name, const TensorSpec &spec) {
    nbostream data;
    Value::UP value = SimpleTensorEngine::ref().from_spec(spec);
    SimpleTensorEngine::ref().encode(*value, data);
    cursor.setData(name, Memory(data.peek(), data.size()));
}

TensorSpec extract_value(const Inspector &inspector) {
    nbostream data = extract_data(inspector);
    const auto &engine = SimpleTensorEngine::ref();
    return engine.to_spec(*engine.decode(data));
}

//-----------------------------------------------------------------------------

std::vector<ValueType> get_types(const std::vector<Value::UP> &param_values) {
    std::vector<ValueType> param_types;
    for (size_t i = 0; i < param_values.size(); ++i) {
        param_types.emplace_back(param_values[i]->type());
    }
    return param_types;
}

TensorSpec eval_expr(const Inspector &test, const TensorEngine &engine, bool typed) {
    Function fun = Function::parse(test["expression"].asString().make_string());
    std::vector<Value::UP> param_values;
    std::vector<Value::CREF> param_refs;
    for (size_t i = 0; i < fun.num_params(); ++i) {
        param_values.emplace_back(engine.from_spec(extract_value(test["inputs"][fun.param_name(i)])));
        param_refs.emplace_back(*param_values.back());
    }
    NodeTypes types = typed ? NodeTypes(fun, get_types(param_values)) : NodeTypes();
    InterpretedFunction ifun(engine, fun, types);
    InterpretedFunction::Context ctx(ifun);
    SimpleObjectParams params(param_refs);
    const Value &result = ifun.eval(ctx, params);
    if (typed) {
        ASSERT_EQUAL(result.type(), types.get_type(fun.root()));
    }
    return engine.to_spec(result);
}

TensorSpec eval_expr_tf(const Inspector &test, const TensorEngine &engine) {
    Stash stash;
    Function fun = Function::parse(test["expression"].asString().make_string());
    std::vector<Value::UP> param_values;
    std::vector<Value::CREF> param_refs;
    for (size_t i = 0; i < fun.num_params(); ++i) {
        param_values.emplace_back(engine.from_spec(extract_value(test["inputs"][fun.param_name(i)])));
        param_refs.emplace_back(*param_values.back());
    }
    SimpleObjectParams params(param_refs);
    NodeTypes types = NodeTypes(fun, get_types(param_values));
    const auto &plain_fun = make_tensor_function(engine, fun.root(), types, stash);
    const auto &optimized = engine.optimize(plain_fun, stash);
    const Value &result = optimized.eval(engine, params, stash);
    ASSERT_EQUAL(result.type(), plain_fun.result_type());
    ASSERT_EQUAL(result.type(), types.get_type(fun.root()));
    return engine.to_spec(result);
}

//-----------------------------------------------------------------------------

std::vector<vespalib::string> extract_fields(const Inspector &object) {
    struct FieldExtractor : slime::ObjectTraverser {
        std::vector<vespalib::string> result;
        void field(const Memory &symbol, const Inspector &) override {
            result.push_back(symbol.make_string());
        }
    } extractor;
    object.traverse(extractor);
    return std::move(extractor.result);
};

//-----------------------------------------------------------------------------

class MyTestBuilder : public TestBuilder {
private:
    TestWriter _writer;
    void make_test(const vespalib::string &expression,
                   const std::map<vespalib::string,TensorSpec> &input_map,
                   const TensorSpec *expect = nullptr)
    {
        Cursor &test = _writer.create();
        test.setString("expression", expression);
        Cursor &inputs = test.setObject("inputs");
        for (const auto &input: input_map) {
            insert_value(inputs, input.first, input.second);
        }
        if (expect != nullptr) {
            insert_value(test.setObject("result"), "expect", *expect);
        } else {
            insert_value(test.setObject("result"), "expect",
                         eval_expr(test, SimpleTensorEngine::ref(), false));
        }
    }
public:
    MyTestBuilder(Output &out) : _writer(out) {}
    void add(const vespalib::string &expression,
             const std::map<vespalib::string,TensorSpec> &inputs,
             const TensorSpec &expect) override
    {
        make_test(expression, inputs, &expect);
    }
    void add(const vespalib::string &expression,
             const std::map<vespalib::string,TensorSpec> &inputs) override
    {
        make_test(expression, inputs);
    }
};

void generate(Output &out) {
    MyTestBuilder my_test_builder(out);
    Generator::generate(my_test_builder);
}

//-----------------------------------------------------------------------------

void evaluate(Input &in, Output &out) {
    auto handle_test = [&out](Slime &slime)
                       {
                           insert_value(slime["result"], "cpp_prod",
                                   eval_expr(slime.get(), DefaultTensorEngine::ref(), true));
                           insert_value(slime["result"], "cpp_prod_untyped",
                                   eval_expr(slime.get(), DefaultTensorEngine::ref(), false));
                           insert_value(slime["result"], "cpp_ref_typed",
                                   eval_expr(slime.get(), SimpleTensorEngine::ref(), true));
                           insert_value(slime["result"], "cpp_tensor_function",
                                   eval_expr_tf(slime.get(), DefaultTensorEngine::ref()));
                           write_compact(slime, out);
                       };
    auto handle_summary = [&out](Slime &slime)
                          {
                              write_compact(slime, out);
                          };
    for_each_test(in, handle_test, handle_summary);
}

//-----------------------------------------------------------------------------

void dump_test(const Inspector &test) {
    fprintf(stderr, "expression: '%s'\n", test["expression"].asString().make_string().c_str());
    for (const auto &input: extract_fields(test["inputs"])) {
        auto value = extract_value(test["inputs"][input]);
        fprintf(stderr, "input '%s': %s\n", input.c_str(), value.to_string().c_str());
    }
}

void verify(Input &in, Output &out) {
    std::map<vespalib::string,size_t> result_map;
    auto handle_test = [&out,&result_map](Slime &slime)
                       {
                           TensorSpec reference_result = eval_expr(slime.get(), SimpleTensorEngine::ref(), false);
                           for (const auto &result: extract_fields(slime["result"])) {
                               ++result_map[result];
                               TEST_STATE(make_string("verifying result: '%s'", result.c_str()).c_str());
                               if (!EXPECT_EQUAL(reference_result, extract_value(slime["result"][result]))) {
                                   dump_test(slime.get());
                               }
                           }
                       };
    auto handle_summary = [&out,&result_map](Slime &slime)
                          {
                              Cursor &stats = slime.get().setObject("stats");
                              for (const auto &entry: result_map) {
                                  stats.setLong(entry.first, entry.second);
                              }
                              JsonFormat::encode(slime, out, false);
                          };
    for_each_test(in, handle_test, handle_summary);
}

//-----------------------------------------------------------------------------

int usage(const char *self) {
    fprintf(stderr, "usage: %s <mode>\n", self);
    fprintf(stderr, "  <mode>: which mode to activate\n");
    fprintf(stderr, "    'generate': write test cases to stdout\n");
    fprintf(stderr, "    'evaluate': read test cases from stdin, annotate them with\n");
    fprintf(stderr, "                results from various implementations and write\n");
    fprintf(stderr, "                them to stdout\n");
    fprintf(stderr, "    'verify': read annotated test cases from stdin and verify\n");
    fprintf(stderr, "              that all results are as expected\n");
    return 1;
}

int main(int argc, char **argv) {
    StdIn std_in;
    StdOut std_out;
    if (argc != 2) {
        return usage(argv[0]);
    }
    vespalib::string mode = argv[1];
    TEST_MASTER.init(make_string("vespa-tensor-conformance-%s", mode.c_str()).c_str());
    if (mode == "generate") {
        generate(std_out);
    } else if (mode == "evaluate") {
        evaluate(std_in, std_out);
    } else if (mode == "verify") {
        verify(std_in, std_out);
    } else {
        TEST_ERROR(make_string("unknown mode: %s", mode.c_str()).c_str());
    }
    return (TEST_MASTER.fini() ? 0 : 1);
}
