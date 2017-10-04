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
#include <unistd.h>

#include "generate.h"

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::slime::convenience;
using slime::JsonFormat;
using tensor::DefaultTensorEngine;

constexpr size_t CHUNK_SIZE = 16384;

//-----------------------------------------------------------------------------

class StdIn : public Input {
private:
    bool _eof = false;
    SimpleBuffer _input;
public:
    ~StdIn() {}
    Memory obtain() override {
        if ((_input.get().size == 0) && !_eof) {
            WritableMemory buf = _input.reserve(CHUNK_SIZE);
            ssize_t res = read(STDIN_FILENO, buf.data, buf.size);
            _eof = (res == 0);
            assert(res >= 0); // fail on stdio read errors
            _input.commit(res);
        }
        return _input.obtain();
    }
    Input &evict(size_t bytes) override {
        _input.evict(bytes);
        return *this;
    }
};

class StdOut : public Output {
private:
    SimpleBuffer _output;
public:
    ~StdOut() {}
    WritableMemory reserve(size_t bytes) override {
        return _output.reserve(bytes);
    }
    Output &commit(size_t bytes) override {
        _output.commit(bytes);
        Memory buf = _output.obtain();
        ssize_t res = write(STDOUT_FILENO, buf.data, buf.size);
        assert(res == ssize_t(buf.size)); // fail on stdout write failures
        _output.evict(res);
        return *this;
    }
};

void write_compact(const Slime &slime, Output &out) {
    JsonFormat::encode(slime, out, true);
    out.reserve(1).data[0] = '\n';
    out.commit(1);
}

void write_readable(const Slime &slime, Output &out) {
    JsonFormat::encode(slime, out, false);
}

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

TensorSpec to_spec(const Value &value) {
    if (value.is_error()) {
        return TensorSpec("error");
    } else if (value.is_double()) {
        return TensorSpec("double").add({}, value.as_double());
    } else {
        ASSERT_TRUE(value.is_tensor());
        auto tensor = value.as_tensor();
        return tensor->engine().to_spec(*tensor);        
    }
}

const Value &to_value(const TensorSpec &spec, const TensorEngine &engine, Stash &stash) {
    if (spec.type() == "error") {
        return stash.create<ErrorValue>();
    } else if (spec.type() == "double") {
        double value = 0.0;
        for (const auto &cell: spec.cells()) {
            value += cell.second;
        }
        return stash.create<DoubleValue>(value);
    } else {
        ASSERT_TRUE(starts_with(spec.type(), "tensor("));
        return stash.create<TensorValue>(engine.create(spec));
    }
}

void insert_value(Cursor &cursor, const vespalib::string &name, const TensorSpec &spec) {
    Stash stash;
    nbostream data;
    const Value &value = to_value(spec, SimpleTensorEngine::ref(), stash);
    SimpleTensorEngine::ref().encode(value, data, stash);
    cursor.setData(name, Memory(data.peek(), data.size()));
}

TensorSpec extract_value(const Inspector &inspector) {
    Stash stash;
    nbostream data = extract_data(inspector);
    return to_spec(SimpleTensorEngine::ref().decode(data, stash));
}

//-----------------------------------------------------------------------------

TensorSpec eval_expr(const Inspector &test, const TensorEngine &engine) {
    Stash stash;
    Function fun = Function::parse(test["expression"].asString().make_string());
    std::vector<Value::CREF> param_values;
    std::vector<ValueType> param_types;
    for (size_t i = 0; i < fun.num_params(); ++i) {
        param_values.emplace_back(to_value(extract_value(test["inputs"][fun.param_name(i)]), engine, stash));
    }
    for (size_t i = 0; i < fun.num_params(); ++i) {
        param_types.emplace_back(param_values[i].get().type());
    }
    NodeTypes types(fun, param_types);
    InterpretedFunction ifun(engine, fun, types);
    InterpretedFunction::Context ctx(ifun);
    InterpretedFunction::SimpleObjectParams params(param_values);
    return to_spec(ifun.eval(ctx, params));
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
    Output &_out;
    size_t  _num_tests;
    void make_test(const vespalib::string &expression,
                   const std::map<vespalib::string,TensorSpec> &input_map,
                   const TensorSpec *expect = nullptr)
    {
        Slime slime;
        Cursor &test = slime.setObject();
        test.setString("expression", expression);
        Cursor &inputs = test.setObject("inputs");
        for (const auto &input: input_map) {
            insert_value(inputs, input.first, input.second);
        }
        if (expect != nullptr) {
            insert_value(test.setObject("result"), "expect", *expect);
        } else {
            insert_value(test.setObject("result"), "expect",
                         eval_expr(slime.get(), SimpleTensorEngine::ref()));
        }
        write_compact(slime, _out);
        ++_num_tests;
    }
public:
    MyTestBuilder(Output &out) : _out(out), _num_tests(0) {}
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
    void make_summary() {
        Slime slime;
        Cursor &summary = slime.setObject();
        summary.setLong("num_tests", _num_tests);
        write_compact(slime, _out);
    }
};

void generate(Output &out) {
    MyTestBuilder my_test_builder(out);
    Generator::generate(my_test_builder);
    my_test_builder.make_summary();
}

//-----------------------------------------------------------------------------

void for_each_test(Input &in,
                   const std::function<void(Slime&)> &handle_test,
                   const std::function<void(Slime&)> &handle_summary)
{
    size_t num_tests = 0;
    bool got_summary = false;
    while (in.obtain().size > 0) {
        Slime slime;
        if (JsonFormat::decode(in, slime)) {
            bool is_test = slime["expression"].valid();
            bool is_summary = slime["num_tests"].valid();
            ASSERT_TRUE(is_test != is_summary);
            if (is_test) {
                ++num_tests;
                ASSERT_TRUE(!got_summary);
                handle_test(slime);
            } else {
                got_summary = true;
                ASSERT_EQUAL(slime["num_tests"].asLong(), int64_t(num_tests));
                handle_summary(slime);
            }
        } else {
            ASSERT_EQUAL(in.obtain().size, 0u);
        }
    }
    ASSERT_TRUE(got_summary);
}

//-----------------------------------------------------------------------------

void evaluate(Input &in, Output &out) {
    auto handle_test = [&out](Slime &slime)
                       {
                           insert_value(slime["result"], "prod_cpp",
                                   eval_expr(slime.get(), DefaultTensorEngine::ref()));
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
                           TensorSpec reference_result = eval_expr(slime.get(), SimpleTensorEngine::ref());
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
                              write_readable(slime, out);
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
