// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/onnx/onnx_wrapper.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/test/test_io.h>
#include <vespa/vespalib/util/benchmark_timer.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/guard.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <charconv>
#ifdef __linux__
#include <malloc.h>
#include <dlfcn.h>
#endif

using vespalib::make_string_short::fmt;

using vespalib::Slime;
using vespalib::slime::JsonFormat;
using vespalib::slime::Inspector;
using vespalib::slime::Cursor;
using vespalib::FilePointer;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

struct MyError : public std::exception {
    explicit MyError(vespalib::stringref m) :
        std::exception(),
        msg(m)
    {}
    const char * what() const noexcept override { return msg.c_str(); }
    vespalib::string msg;
};

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
struct MemoryUsage {
    size_t vm_size;
    size_t rss_size;
    size_t malloc_peak;
    size_t malloc_current;
};

#ifdef __linux__
static const vespalib::string UNKNOWN = "unknown";

size_t convert(const vespalib::string & s) {
    if (s == UNKNOWN) return 0;
    size_t v(0);
    size_t end = s.find("kB");
    auto [ptr,ec] = std::from_chars(s.data(), s.data()+std::min(s.size(), end), v, 10);
    if (ec != std::errc()) {
        throw std::runtime_error(fmt("Bad format : '%s' at '%s'", s.c_str(), ptr));
    }
    if (end == vespalib::string::npos) {
        throw std::runtime_error(fmt("Bad format : %s", s.c_str()));
    }
    return v * 1024;
}

MemoryUsage extract_memory_usage() {
    vespalib::string vm_size = UNKNOWN;
    vespalib::string vm_rss = UNKNOWN;
    FilePointer file(fopen("/proc/self/status", "r"));
    if (file.valid()) {
        vespalib::string line;
        while (read_line(file, line)) {
            extract(line, "VmSize:", vm_size);
            extract(line, "VmRSS:", vm_rss);
        }
    }
    MemoryUsage usage = {};
    usage.vm_size = convert(vm_size);
    usage.rss_size = convert(vm_rss);

#if __GLIBC_PREREQ(2, 33)
    struct mallinfo2 info = mallinfo2();
    usage.malloc_peak = size_t(info.usmblks);
    usage.malloc_current = size_t(info.arena + info.hblkhd);
#else
    struct mallinfo info = mallinfo();

    if (dlsym(RTLD_NEXT, "is_vespamalloc") != nullptr) {
        // Vespamalloc reports arena in 1M blocks as an 'int' is too small.
        usage.malloc_peak = size_t(info.usmblks) * 1_Mi;
        usage.malloc_current = size_t(info.arena + info.hblkhd) * 1_Mi;
    } else {
        usage.malloc_peak = size_t(info.usmblks);
        usage.malloc_current = size_t(info.arena + info.hblkhd);
    }
#endif
    return usage;
}
#else
MemoryUsage extract_memory_usage() {
    return { 0, 0, 0, 0 };
}
#endif

void report_memory_usage(const vespalib::string &desc) {
    MemoryUsage m = extract_memory_usage();
    fprintf(stderr, "vm_size: %zu kB, vm_rss: %zu kB, malloc_peak: %zu kb, malloc_curr: %zu (%s)\n",
            m.vm_size/1_Ki, m.rss_size/1_Ki, m.malloc_peak/1_Ki, m.malloc_current/1_Ki, desc.c_str());
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
    explicit MakeInputType(Options &opts_in) : opts(opts_in), symbolic_sizes() {}
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

vespalib::string make_bound_str(const std::map<vespalib::string,size_t> &bound) {
    vespalib::string result;
    if (!bound.empty()) {
        for (const auto &[name, size]: bound) {
            if (result.empty()) {
                result.append(" (");
            } else {
                result.append(",");
            }
            result.append(fmt("%s=%zu", name.c_str(), size));
        }
        result.append(")");
    }
    return result;
}

void bind_input(Onnx::WirePlanner &planner, const Onnx::TensorInfo &input, const ValueType &type) {
    auto bound = planner.get_bound_sizes(input);
    if (!planner.bind_input_type(type, input)) {
        auto bound_str = make_bound_str(bound);
        throw MyError{fmt("incompatible type for input '%s': %s -> %s%s",
                          input.name.c_str(), type.to_spec().c_str(), input.type_as_string().c_str(), bound_str.c_str())};
    }
}

ValueType make_output(const Onnx::WirePlanner &planner, const Onnx::TensorInfo &output) {
    auto type = planner.make_output_type(output);
    if (type.is_error()) {
        throw MyError{fmt("unable to make compatible type for output '%s': %s -> error",
                          output.name.c_str(), output.type_as_string().c_str())};
    }
    return type;
}

Onnx::WireInfo make_plan(Options &opts, const Onnx &model) {
    Onnx::WirePlanner planner;
    MakeInputType make_input_type(opts);
    for (const auto &input: model.inputs()) {
        auto type = make_input_type(input);
        bind_input(planner, input, type);
    }
    planner.prepare_output_types(model);
    for (const auto &output: model.outputs()) {
        make_output(planner, output);
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
    fprintf(stderr, "  tip: run without options first, to see which you need\n\n");
    fprintf(stderr, "usage: %s --probe-types\n", self);
    fprintf(stderr, "  use onnx model to infer/probe output types based on input types\n");
    fprintf(stderr, "  parameters are read from stdin and results are written to stdout\n");
    fprintf(stderr, "  input format (json): {model:<filename>, inputs:{<name>:vespa-type-string}}\n");
    fprintf(stderr, "  output format (json): {outputs:{<name>:vespa-type-string}}\n");
    return 1;
}

int probe_types() {
    StdIn std_in;
    StdOut std_out;
    Slime params;
    if (!JsonFormat::decode(std_in, params)) {
        throw MyError{"invalid json"};
    }
    MemoryUsage vm_before = extract_memory_usage();
    Slime result;
    auto &root = result.setObject();
    auto &types = root.setObject("outputs");
    Onnx model(params["model"].asString().make_string(), Onnx::Optimize::ENABLE);
    Onnx::WirePlanner planner;
    for (const auto & input : model.inputs()) {
        auto spec = params["inputs"][input.name].asString().make_string();
        auto input_type = ValueType::from_spec(spec);
        if (input_type.is_error()) {
            if (!params["inputs"][input.name].valid()) {
                throw MyError(fmt("missing type for model input '%s'", input.name.c_str()));
            } else {
                throw MyError(fmt("invalid type for model input '%s': '%s'",input.name.c_str(), spec.c_str()));
            }
        }
        bind_input(planner, input, input_type);
    }
    planner.prepare_output_types(model);
    for (const auto &output: model.outputs()) {
        auto output_type = make_output(planner, output);
        types.setString(output.name, output_type.to_spec());
    }
    MemoryUsage vm_after = extract_memory_usage();
    root.setLong("vm_size", vm_after.vm_size - vm_before.vm_size);
    root.setLong("vm_rss", vm_after.rss_size - vm_before.rss_size);
    root.setLong("malloc_peak", vm_after.malloc_peak - vm_before.malloc_peak);
    root.setLong("malloc_current", vm_after.malloc_current - vm_before.malloc_current);
    write_compact(result, std_out);
    return 0;
}

int my_main(int argc, char **argv) {
    if (argc < 2) {
        return usage(argv[0]);
    }
    if ((argc == 2) && (vespalib::string(argv[1]) == "--probe-types")) {
        return probe_types();
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
    } catch (const MyError &err) {
        fprintf(stdout, "error: %s\n", err.msg.c_str());
        return 3;
    } catch (const std::exception &ex) {
        fprintf(stdout, "got exception: %s\n", ex.what());
        return 2;
    }
}
