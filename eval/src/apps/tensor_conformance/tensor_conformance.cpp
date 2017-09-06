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
constexpr bool not_compact = false;

//-----------------------------------------------------------------------------

size_t num_tests = 0;
std::map<vespalib::string,size_t> result_map;

vespalib::string result_stats() {
    vespalib::string stats;
    for (const auto &entry: result_map) {        
        if (!stats.empty()) {
            stats += ", ";
        }
        stats += make_string("%s: %zu", entry.first.c_str(), entry.second);
    }
    return stats;
}

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

void dump_test(const Inspector &test) {
    fprintf(stderr, "expression: '%s'\n", test["expression"].asString().make_string().c_str());
    for (const auto &input: extract_fields(test["inputs"])) {
        auto value = extract_value(test["inputs"][input]);
        fprintf(stderr, "input '%s': %s\n", input.c_str(), value.to_string().c_str());
    }
}

//-----------------------------------------------------------------------------

class MyTestBuilder : public TestBuilder {
private:
    Output &_out;
    void build_test(Cursor &test, const vespalib::string &expression,
                    const std::map<vespalib::string,TensorSpec> &input_map)
    {
        test.setString("expression", expression);
        Cursor &inputs = test.setObject("inputs");
        for (const auto &input: input_map) {
            insert_value(inputs, input.first, input.second);
        }
    }
public:
    MyTestBuilder(Output &out) : _out(out) {}
    void add(const vespalib::string &expression,
             const std::map<vespalib::string,TensorSpec> &inputs) override
    {
        Slime slime;
        build_test(slime.setObject(), expression, inputs);
        insert_value(slime.get().setObject("result"), "expect",
                     eval_expr(slime.get(), SimpleTensorEngine::ref()));
        JsonFormat::encode(slime, _out, not_compact);
        ++num_tests;
    }
    void add(const vespalib::string &expression,
             const std::map<vespalib::string,TensorSpec> &inputs,
             const TensorSpec &expect) override
    {
        Slime slime;
        build_test(slime.setObject(), expression, inputs);
        insert_value(slime.get().setObject("result"), "expect", expect);
        if (!EXPECT_EQUAL(eval_expr(slime.get(), SimpleTensorEngine::ref()), expect)) {
            dump_test(slime.get());
        }
        JsonFormat::encode(slime, _out, not_compact);
        ++num_tests;
    }
};

void generate(Output &out) {
    MyTestBuilder my_test_builder(out);
    Generator::generate(my_test_builder);
}

//-----------------------------------------------------------------------------

void evaluate(Input &in, Output &out) {
    while (in.obtain().size > 0) {
        Slime slime;
        if (JsonFormat::decode(in, slime)) {
            ++num_tests;
            insert_value(slime.get()["result"], "prod_cpp",
                         eval_expr(slime.get(), DefaultTensorEngine::ref()));
            JsonFormat::encode(slime, out, not_compact);
        }
    }
}

//-----------------------------------------------------------------------------

void verify(Input &in) {
    while (in.obtain().size > 0) {
        Slime slime;
        if (JsonFormat::decode(in, slime)) {
            ++num_tests;
            TensorSpec reference_result = eval_expr(slime.get(), SimpleTensorEngine::ref());
            for (const auto &result: extract_fields(slime.get()["result"])) {
                ++result_map[result];
                TEST_STATE(make_string("verifying result: '%s'", result.c_str()).c_str());
                if (!EXPECT_EQUAL(reference_result, extract_value(slime.get()["result"][result]))) {
                    dump_test(slime.get());
                }
            }
        }
    }
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
        fprintf(stderr, "generated %zu test cases\n", num_tests);
    } else if (mode == "evaluate") {
        evaluate(std_in, std_out);
        fprintf(stderr, "evaluated %zu test cases\n", num_tests);
    } else if (mode == "verify") {
        verify(std_in);
        fprintf(stderr, "verified %zu test cases (%s)\n", num_tests, result_stats().c_str());
    } else {
        TEST_ERROR(make_string("unknown mode: %s", mode.c_str()).c_str());
    }
    return (TEST_MASTER.fini() ? 0 : 1);
}
