// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/data/output_writer.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/reference_evaluation.h>
#include <vespa/eval/eval/test/test_io.h>
#include <vespa/eval/eval/test/gen_spec.h>
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

size_t fail_cnt = 0;
size_t ignore_cnt = 0;

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
    REQUIRE_FAILED("bad hex char");
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

void dump_test(const Inspector &test) {
    fprintf(stderr, "expression: '%s'\n", test["expression"].asString().make_string().c_str());
    for (const auto &input: extract_fields(test["inputs"])) {
        auto value = extract_value(test["inputs"][input]);
        fprintf(stderr, "input '%s': %s\n", input.c_str(), value.to_string().c_str());
    }
}

//-----------------------------------------------------------------------------

TensorSpec ref_eval(const Inspector &test) {
    auto fun = Function::parse(test["expression"].asString().make_string());
    std::vector<TensorSpec> params;
    for (size_t i = 0; i < fun->num_params(); ++i) {
        params.push_back(extract_value(test["inputs"][fun->param_name(i)]));
    }
    auto result = ReferenceEvaluation::eval(*fun, params);
    if (result.type() == "error") {
        dump_test(test);
        REQUIRE_FAILED("reference evaluation failed!");
    }
    return result;
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
    REQUIRE_EQ(result.type(), types.get_type(fun->root()));
    return spec_from_value(result);
}

//-----------------------------------------------------------------------------

void print_test(const Inspector &test, OutputWriter &dst) {
    dst.printf("expression: '%s'\n", test["expression"].asString().make_string().c_str());
    for (const auto &input: extract_fields(test["inputs"])) {
        auto value = extract_value(test["inputs"][input]);
        dst.printf("input '%s': %s\n", input.c_str(), value.to_string().c_str());
    }
    auto result = eval_expr(test, prod_factory);
    dst.printf("result: %s\n", result.to_string().c_str());
    auto ignore = extract_fields(test["ignore"]);
    if (!ignore.empty()) {
        dst.printf("ignore:");
        for (const auto &impl: ignore) {
            REQUIRE(test["ignore"][impl].asBool());
            dst.printf(" %s", impl.c_str());
        }
        dst.printf("\n");
    }
}

//-----------------------------------------------------------------------------

class MyTestBuilder : public TestBuilder {
private:
    TestWriter _writer;
public:
    MyTestBuilder(bool full_in, Output &out) : TestBuilder(full_in), _writer(out) {}
    void add(const vespalib::string &expression,
             const std::map<vespalib::string,TensorSpec> &inputs_in,
             const std::set<vespalib::string> &ignore_in) override
    {
        Cursor &test = _writer.create();
        test.setString("expression", expression);
        Cursor &inputs = test.setObject("inputs");
        for (const auto& [name, spec]: inputs_in) {
            insert_value(inputs, name, spec);
        }
        test.setObject("result");
        if (!ignore_in.empty()) {
            Cursor &ignore = test.setObject("ignore");
            for (const auto &impl: ignore_in) {
                ignore.setBool(impl, true);
            }
        }
    }
    void add_failing_test(bool ignore_fail) {
        Cursor &test = _writer.create();
        test.setString("expression", "a");
        insert_value(test.setObject("inputs"), "a", GenSpec(1).idx("x", 3));
        insert_value(test.setObject("result"), "dummy", GenSpec(2).idx("x", 3));
        if (ignore_fail) {
            test.setObject("ignore").setBool("dummy", true);
        }
    }
};

void generate(Output &out, bool full) {
    MyTestBuilder my_test_builder(full, out);
    Generator::generate(my_test_builder);
    // my_test_builder.add_failing_test(true);
    // my_test_builder.add_failing_test(false);
}

//-----------------------------------------------------------------------------

void evaluate(Input &in, Output &out) {
    auto handle_test = [&out](Slime &slime) {
        insert_value(slime["result"], "cpp_prod",
                     eval_expr(slime.get(), prod_factory));
        insert_value(slime["result"], "cpp_simple_value",
                     eval_expr(slime.get(), simple_factory));
        insert_value(slime["result"], "cpp_streamed_value",
                     eval_expr(slime.get(), streamed_factory));
        write_compact(slime, out);
    };
    auto handle_summary = [&out](Slime &slime) {
        write_compact(slime, out);
    };
    for_each_test(in, handle_test, handle_summary);
}

//-----------------------------------------------------------------------------

void verify(Input &in, Output &out) {
    std::map<vespalib::string,size_t> result_map;
    auto handle_test = [&result_map](Slime &slime) {
        TensorSpec reference_result = ref_eval(slime.get());
        for (const auto &result: extract_fields(slime["result"])) {
            ++result_map[result];
            auto actual_result = extract_value(slime["result"][result]);
            if (!require_impl::eq(actual_result, reference_result)) {
                bool ignore_fail = slime["ignore"][result].asBool();
                if (ignore_fail) {
                    ++ignore_cnt;
                } else {
                    ++fail_cnt;
                }
                fprintf(stderr, "%sexpression failed('%s'): '%s'\n", ignore_fail ? "IGNORED: " : "",
                        result.c_str(), slime["expression"].asString().make_string().c_str());
                fprintf(stderr, "%s", TensorSpec::diff(actual_result, "actual", reference_result, "expected").c_str());
                dump_test(slime.get());
            }
        }
    };
    auto handle_summary = [&out,&result_map](Slime &slime) {
        Cursor &stats = slime.get().setObject("stats");
        for (const auto &entry: result_map) {
            stats.setLong(entry.first, entry.second);
        }
        REQUIRE(!slime["fail_cnt"].valid());
        REQUIRE(!slime["ignore_cnt"].valid());
        slime.get().setLong("fail_cnt", fail_cnt);
        slime.get().setLong("ignore_cnt", ignore_cnt);
        JsonFormat::encode(slime, out, false);
    };
    for_each_test(in, handle_test, handle_summary);
}

//-----------------------------------------------------------------------------

void display(Input &in, Output &out) {
    size_t test_cnt = 0;
    auto handle_test = [&out,&test_cnt](Slime &slime) {
        OutputWriter dst(out, 4_Ki);
        dst.printf("\n------- TEST #%zu -------\n\n", test_cnt++);
        print_test(slime.get(), dst);
    };
    auto handle_summary = [&out,&test_cnt](Slime &) {
        OutputWriter dst(out, 1024);
        dst.printf("%zu tests displayed\n", test_cnt);
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
    fprintf(stderr, "    'display': read tests from stdin and print them to stdout\n");
    fprintf(stderr, "               in human-readable form\n");
    fprintf(stderr, "    'generate-some': write some test cases to stdout\n");
    return 1;
}

int main(int argc, char **argv) {
    StdIn std_in;
    StdOut std_out;
    if (argc < 2) {
        return usage(argv[0]);
    }
    vespalib::string mode = argv[1];
    if (mode == "generate") {
        generate(std_out, true);
    } else if (mode == "generate-some") {
        generate(std_out, false);
    } else if (mode == "evaluate") {
        evaluate(std_in, std_out);
    } else if (mode == "verify") {
        verify(std_in, std_out);
    } else if (mode == "display") {
        display(std_in, std_out);
    } else {
        REQUIRE_FAILED(make_string("unknown mode: %s", mode.c_str()).c_str());
    }
    if (fail_cnt == 0) {
        fprintf(stderr, "(mode=%s) DONE (no failures detected)\n", mode.c_str());
        return 0;
    } else {
        fprintf(stderr, "(mode=%s) ERROR: detected %zu failure(s)\n", mode.c_str(), fail_cnt);
        return 1;
    }
}
