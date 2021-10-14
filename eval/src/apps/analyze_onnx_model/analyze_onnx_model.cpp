// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/onnx/onnx_wrapper.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/guard.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string_short::fmt;

using vespalib::FilePointer;
using namespace vespalib::eval;

bool read_line(FilePointer &file, vespalib::string &line) {
    char line_buffer[1024];
    char *res = fgets(line_buffer, sizeof(line_buffer), file.fp());
    if (res == nullptr) {
        line.clear();
        return false;
    }
    line = line_buffer;
    while (!line.empty() && isspace(line[line.size() - 1])) {
        line.pop_back();
    }
    return true;
}

void extract(const vespalib::string &str, const vespalib::string &prefix, vespalib::string &dst) {
    if (starts_with(str, prefix)) {
        size_t pos = prefix.size();
        while ((str.size() > pos) && isspace(str[pos])) {
            ++pos;
        }
        dst = str.substr(pos);
    }
}

void report_memory_usage(const vespalib::string &desc) {
    vespalib::string vm_size = "unknown";
    vespalib::string vm_rss = "unknown";
    vespalib::string line;
    FilePointer file(fopen("/proc/self/status", "r"));
    while (read_line(file, line)) {
        extract(line, "VmSize:", vm_size);
        extract(line, "VmRSS:", vm_rss);
    }
    fprintf(stderr, "vm_size: %s, vm_rss: %s (%s)\n", vm_size.c_str(), vm_rss.c_str(), desc.c_str());
}

struct Options {
    size_t pos = 0;
    std::vector<vespalib::string> opt_list;
    void add_option(const vespalib::string &opt) {
        opt_list.push_back(opt);
    }
    vespalib::string get_option(const vespalib::string &desc, const vespalib::string &fallback) {
        vespalib::string opt;
        if (pos < opt_list.size()) {
            opt = opt_list[pos];
            fprintf(stderr, "option[%zu](%s): %s\n",
                    pos, desc.c_str(), opt.c_str());
        } else {
            opt = fallback;
            fprintf(stderr, "unspecified option[%zu](%s), fallback: %s\n",
                    pos, desc.c_str(), fallback.c_str());
        }
        ++pos;
        return opt;
    }
    bool get_bool_opt(const vespalib::string &desc, const vespalib::string &fallback) {
        auto opt = get_option(desc, fallback);
        REQUIRE((opt == "true") || (opt == "false"));
        return (opt == "true");
    }
    size_t get_size_opt(const vespalib::string &desc, const vespalib::string &fallback) {
        auto opt = get_option(desc, fallback);
        size_t value = atoi(opt.c_str());
        REQUIRE(value > 0);
        return value;
    }
};

void dump_model_info(const Onnx &model) {
    fprintf(stderr, "model meta-data:\n");
    for (size_t i = 0; i < model.inputs().size(); ++i) {
        fprintf(stderr, "  input[%zu]: '%s' %s\n", i, model.inputs()[i].name.c_str(), model.inputs()[i].type_as_string().c_str());
    }
    for (size_t i = 0; i < model.outputs().size(); ++i) {
        fprintf(stderr, "  output[%zu]: '%s' %s\n", i, model.outputs()[i].name.c_str(), model.outputs()[i].type_as_string().c_str());
    }
}

void dump_wire_info(const Onnx::WireInfo &wire) {
    fprintf(stderr, "test setup:\n");
    REQUIRE_EQ(wire.vespa_inputs.size(), wire.onnx_inputs.size());
    for (size_t i = 0; i < wire.vespa_inputs.size(); ++i) {
        fprintf(stderr, "  input[%zu]: %s -> %s\n", i, wire.vespa_inputs[i].to_spec().c_str(), wire.onnx_inputs[i].type_as_string().c_str());
    }
    REQUIRE_EQ(wire.onnx_outputs.size(), wire.vespa_outputs.size());
    for (size_t i = 0; i < wire.onnx_outputs.size(); ++i) {
        fprintf(stderr, "  output[%zu]: %s -> %s\n", i, wire.onnx_outputs[i].type_as_string().c_str(), wire.vespa_outputs[i].to_spec().c_str());
    }
}

struct MakeInputType {
    Options &opts;
    std::map<vespalib::string,int> symbolic_sizes;
    MakeInputType(Options &opts_in) : opts(opts_in), symbolic_sizes() {}
    ValueType operator()(const Onnx::TensorInfo &info) {
        int d = 0;
        std::vector<ValueType::Dimension> dim_list;
        for (const auto &dim: info.dimensions) {
            REQUIRE(d <= 9);
            size_t size = 0;
            if (dim.is_known()) {
                size = dim.value;
            } else if (dim.is_symbolic()) {
                size = symbolic_sizes[dim.name];
                if (size == 0) {
                    size = opts.get_size_opt(fmt("symbolic size '%s'", dim.name.c_str()), "1");
                    symbolic_sizes[dim.name] = size;
                }
            } else {
                size = opts.get_size_opt(fmt("size of input '%s' dimension %d", info.name.c_str(), d), "1");
            }
            dim_list.emplace_back(fmt("d%d", d), size);
            ++d;
        }
        return ValueType::make_type(Onnx::WirePlanner::best_cell_type(info.elements), std::move(dim_list));
    }
};

Onnx::WireInfo make_plan(Options &opts, const Onnx &model) {
    Onnx::WirePlanner planner;
    MakeInputType make_input_type(opts);
    for (const auto &input: model.inputs()) {
        auto type = make_input_type(input);
        REQUIRE(planner.bind_input_type(type, input));
    }
    for (const auto &output: model.outputs()) {
        REQUIRE(!planner.make_output_type(output).is_error());
    }
    return planner.get_wire_info(model);
}

struct MyEval {
    Onnx::EvalContext context;
    std::vector<Value::UP> inputs;    
    MyEval(const Onnx &model, const Onnx::WireInfo &wire) : context(model, wire), inputs() {
        for (const auto &input_type: wire.vespa_inputs) {
            TensorSpec spec(input_type.to_spec());
            inputs.push_back(value_from_spec(spec, FastValueBuilderFactory::get()));
        }
    }
    void eval() {
        for (size_t i = 0; i < inputs.size(); ++i) {
            context.bind_param(i, *inputs[i]);
        }
        context.eval();
    }
};

int usage(const char *self) {
    fprintf(stderr, "usage: %s <onnx-model> [options...]\n", self);
    fprintf(stderr, "  load onnx model and report memory usage\n");
    fprintf(stderr, "  options are used to specify unknown values, like dimension sizes\n");
    fprintf(stderr, "  options are accepted in the order in which they are needed\n");
    fprintf(stderr, "  tip: run without options first, to see which you need\n");
    return 1;
}

int my_main(int argc, char **argv) {
    if (argc < 2) {
        return usage(argv[0]);
    }
    Options opts;
    for (int i = 2; i < argc; ++i) {
        opts.add_option(argv[i]);
    }
    Onnx::Optimize optimize = opts.get_bool_opt("optimize model", "true")
        ? Onnx::Optimize::ENABLE : Onnx::Optimize::DISABLE;
    report_memory_usage("before loading model");
    Onnx model(argv[1], optimize);
    report_memory_usage("after loading model");
    dump_model_info(model);
    auto wire_info = make_plan(opts, model);
    dump_wire_info(wire_info);
    std::vector<std::unique_ptr<MyEval>> eval_list;
    size_t max_concurrent = opts.get_size_opt("max concurrent evaluations", "1");
    report_memory_usage("no evaluations yet");
    for (size_t i = 1; i <= max_concurrent; ++i) {
        eval_list.push_back(std::make_unique<MyEval>(model, wire_info));
        eval_list.back()->eval();
        if ((i % 8) == 0) {
            report_memory_usage(fmt("concurrent evaluations: %zu", i));
        }
    }
    if ((max_concurrent % 8) != 0) {
        report_memory_usage(fmt("concurrent evaluations: %zu", max_concurrent));
    }
    eval_list.resize(1);
    double min_time_s = vespalib::BenchmarkTimer::benchmark([&e = *eval_list.back()](){ e.eval(); }, 10.0);    
    fprintf(stderr, "estimated model evaluation time: %g ms\n", min_time_s * 1000.0);
    return 0;
}

int main(int argc, char **argv) {
    try {
        return my_main(argc, argv);
    } catch (const std::exception &ex) {
        fprintf(stderr, "got exception: %s\n", ex.what());
        return 2;
    }
}
