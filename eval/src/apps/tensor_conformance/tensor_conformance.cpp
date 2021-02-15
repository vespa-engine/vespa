// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/output_writer.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/reference_evaluation.h>
#include <vespa/eval/eval/test/test_io.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <unistd.h>
#include <functional>

#include "generate.h"

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::slime::convenience;
using vespalib::slime::inject;
using vespalib::slime::SlimeInserter;
using slime::JsonFormat;
using namespace std::placeholders;

//-----------------------------------------------------------------------------

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();
const ValueBuilderFactory &simple_factory = SimpleValueBuilderFactory::get();
const ValueBuilderFactory &streamed_factory = StreamedValueBuilderFactory::get();

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
    Value::UP value = value_from_spec(spec, simple_factory);
    encode_value(*value, data);
    cursor.setData(name, Memory(data.peek(), data.size()));
}

TensorSpec extract_value(const Inspector &inspector) {
    nbostream data = extract_data(inspector);
    return spec_from_value(*decode_value(data, simple_factory));
}

//-----------------------------------------------------------------------------

TensorSpec ref_eval(const Inspector &test) {
    auto fun = Function::parse(test["expression"].asString().make_string());
    std::vector<TensorSpec> params;
    for (size_t i = 0; i < fun->num_params(); ++i) {
        params.push_back(extract_value(test["inputs"][fun->param_name(i)]));
    }
    return ReferenceEvaluation::eval(*fun, params);
}

//-----------------------------------------------------------------------------

std::vector<ValueType> get_types(const std::vector<Value::UP> &param_values) {
    std::vector<ValueType> param_types;
    for (size_t i = 0; i < param_values.size(); ++i) {
        param_types.emplace_back(param_values[i]->type());
    }
    return param_types;
}

TensorSpec eval_expr(const Inspector &test, const ValueBuilderFactory &factory) {
    auto fun = Function::parse(test["expression"].asString().make_string());
    std::vector<Value::UP> param_values;
    std::vector<Value::CREF> param_refs;
    for (size_t i = 0; i < fun->num_params(); ++i) {
        param_values.emplace_back(value_from_spec(extract_value(test["inputs"][fun->param_name(i)]), factory));
        param_refs.emplace_back(*param_values.back());
    }
    NodeTypes types = NodeTypes(*fun, get_types(param_values));
    InterpretedFunction ifun(factory, *fun, types);
    InterpretedFunction::Context ctx(ifun);
    SimpleObjectParams params(param_refs);
    const Value &result = ifun.eval(ctx, params);
    ASSERT_EQUAL(result.type(), types.get_type(fun->root()));
    return spec_from_value(result);
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

void print_test(const Inspector &test, OutputWriter &dst) {
    dst.printf("expression: '%s'\n", test["expression"].asString().make_string().c_str());
    for (const auto &input: extract_fields(test["inputs"])) {
        auto value = extract_value(test["inputs"][input]);
        dst.printf("input '%s': %s\n", input.c_str(), value.to_string().c_str());
    }
    auto result = extract_value(test["result"]["expect"]);
    dst.printf("expected result: %s\n", result.to_string().c_str());
}

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
            insert_value(test.setObject("result"), "expect", ref_eval(test));
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
                                   eval_expr(slime.get(), prod_factory));
                           insert_value(slime["result"], "cpp_simple_value",
                                   eval_expr(slime.get(), simple_factory));
                           insert_value(slime["result"], "cpp_streamed_value",
                                   eval_expr(slime.get(), streamed_factory));
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
    auto handle_test = [&result_map](Slime &slime)
                       {
                           TensorSpec reference_result = ref_eval(slime.get());
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

struct TestList {
    std::vector<Slime> list;
    void add_test(Slime &slime) {
        list.emplace_back();
        inject(slime.get(), SlimeInserter(list.back()));
    }
};

struct TestSpec {
    vespalib::string expression;
    std::vector<TensorSpec> inputs;
    TensorSpec result;
    TestSpec() : expression(), inputs(), result("error") {}
    ~TestSpec();
    void decode(const Inspector &test) {
        const auto &my_expression = test["expression"];
        ASSERT_TRUE(my_expression.valid());
        expression = my_expression.asString().make_string();
        auto fun = Function::parse(expression);
        ASSERT_TRUE(!fun->has_error());
        ASSERT_EQUAL(fun->num_params(), test["inputs"].fields());
        for (size_t i = 0; i < fun->num_params(); ++i) {
            TEST_STATE(make_string("input #%zu", i).c_str());
            const auto &my_input = test["inputs"][fun->param_name(i)];
            ASSERT_TRUE(my_input.valid());
            inputs.push_back(extract_value(my_input));
        }
        const auto &my_result = test["result"]["expect"];
        ASSERT_TRUE(my_result.valid());
        result = extract_value(my_result);
    }
};
TestSpec::~TestSpec() = default;

void compare_test(const Inspector &expect_in, const Inspector &actual_in) {
    TestSpec expect;
    TestSpec actual;
    {
        TEST_STATE("decoding expected test case");
        expect.decode(expect_in);
    }
    {
        TEST_STATE("decoding actual test case");
        actual.decode(actual_in);
    }
    {
        TEST_STATE("comparing test cases");
        ASSERT_EQUAL(expect.expression, actual.expression);
        ASSERT_EQUAL(expect.inputs.size(), actual.inputs.size());
        for (size_t i = 0; i < expect.inputs.size(); ++i) {
            TEST_STATE(make_string("input #%zu", i).c_str());
            ASSERT_EQUAL(expect.inputs[i], actual.inputs[i]);
        }
        ASSERT_EQUAL(expect.result, actual.result);
    }
}

void compare(Input &expect, Input &actual) {
    TestList expect_tests;
    TestList actual_tests;
    for_each_test(expect, std::bind(&TestList::add_test, &expect_tests, _1), [](Slime &) noexcept {});
    for_each_test(actual, std::bind(&TestList::add_test, &actual_tests, _1), [](Slime &) noexcept {});
    ASSERT_TRUE(!expect_tests.list.empty());
    ASSERT_TRUE(!actual_tests.list.empty());
    ASSERT_EQUAL(expect_tests.list.size(), actual_tests.list.size());
    size_t num_tests = expect_tests.list.size();
    fprintf(stderr, "...found %zu test cases to compare...\n", num_tests);
    for (size_t i = 0; i < num_tests; ++i) {
        TEST_STATE(make_string("test case #%zu", i).c_str());
        compare_test(expect_tests.list[i].get(), actual_tests.list[i].get());
    }
}

//-----------------------------------------------------------------------------

void display(Input &in, Output &out) {
    size_t test_cnt = 0;
    auto handle_test = [&out,&test_cnt](Slime &slime)
                       {
                           OutputWriter dst(out, 4_Ki);
                           dst.printf("\n------- TEST #%zu -------\n\n", test_cnt++);
                           print_test(slime.get(), dst);
                       };
    auto handle_summary = [&out,&test_cnt](Slime &)
                          {
                              OutputWriter dst(out, 1024);
                              dst.printf("%zu tests displayed\n", test_cnt);
                          };
    for_each_test(in, handle_test, handle_summary);
}

//-----------------------------------------------------------------------------

int usage(const char *self) {
    fprintf(stderr, "usage: %s <mode>\n", self);
    fprintf(stderr, "usage: %s compare <expect> <actual>\n", self);
    fprintf(stderr, "  <mode>: which mode to activate\n");
    fprintf(stderr, "    'generate': write test cases to stdout\n");
    fprintf(stderr, "    'evaluate': read test cases from stdin, annotate them with\n");
    fprintf(stderr, "                results from various implementations and write\n");
    fprintf(stderr, "                them to stdout\n");
    fprintf(stderr, "    'verify': read annotated test cases from stdin and verify\n");
    fprintf(stderr, "              that all results are as expected\n");
    fprintf(stderr, "    'display': read tests from stdin and print them to stdout\n");
    fprintf(stderr, "               in human-readable form\n");
    fprintf(stderr, "    'compare': read test cases from two separate files and\n");
    fprintf(stderr, "               compare them to verify equivalence\n");
    return 1;
}

int main(int argc, char **argv) {
    StdIn std_in;
    StdOut std_out;
    if (argc < 2) {
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
    } else if (mode == "display") {
        display(std_in, std_out);
    } else if (mode == "compare") {
        if (argc == 4) {
            MappedFileInput expect(argv[2]);
            MappedFileInput actual(argv[3]);
            if (expect.valid() && actual.valid()) {
                compare(expect, actual);
            } else {
                if (!expect.valid()) {
                    TEST_ERROR(make_string("could not read file: %s", argv[2]).c_str());
                }
                if (!actual.valid()) {
                    TEST_ERROR(make_string("could not read file: %s", argv[3]).c_str());
                }
            }
        } else {
            TEST_ERROR("wrong number of parameters for 'compare'\n");
        }
    } else {
        TEST_ERROR(make_string("unknown mode: %s", mode.c_str()).c_str());
    }
    return (TEST_MASTER.fini() ? 0 : 1);
}
