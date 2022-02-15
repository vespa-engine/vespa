// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#ifdef __APPLE__
#include <onnxruntime/core/session/onnxruntime_cxx_api.h>
#else
#include <onnxruntime/onnxruntime_cxx_api.h>
#endif
#include <vespa/vespalib/stllike/string.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/value.h>
#include <vector>
#include <map>
#include <set>

namespace vespalib::eval { struct Value; }

namespace vespalib::eval {

/**
 * Wrapper around an ONNX model handeled by onnxruntime.
 *
 * Create an Onnx object that will load your model and extract
 * information about inputs and outputs. Use an Onnx::WirePlanner to
 * bind vespa value types to each of the onnx model inputs. Ask the
 * wire planner about the vespa value types corresponding to each of
 * the model outputs for external wiring. Use the wire planner to make
 * a WireInfo object which is a simple struct indicating the concrete
 * onnx and vespa types to be used when converting inputs and
 * outputs. Create an Onnx::EvalContex based on the model and the wire
 * plan. Bind actual vespa values to the model inputs, invoke eval and
 * inspect the results. See the unit test (tests/tensor/onnx_wrapper)
 * for some examples.
 **/
class Onnx {
public:
    // model optimization
    enum class Optimize { ENABLE, DISABLE };

    // the size of a dimension
    struct DimSize {
        size_t value;
        vespalib::string name;
        DimSize() noexcept : value(0), name() {}
        DimSize(size_t size) noexcept : value(size), name() {}
        DimSize(const vespalib::string &symbol) noexcept : value(0), name(symbol) {}
        bool is_known() const { return (value > 0); }
        bool is_symbolic() const { return !name.empty(); }
        vespalib::string as_string() const;
    };

    // supported onnx element types
    enum class ElementType { INT8, INT16, INT32, INT64, UINT8, UINT16, UINT32, UINT64, BFLOAT16, FLOAT, DOUBLE };

    // information about a single input or output tensor
    struct TensorInfo {
        vespalib::string name;
        std::vector<DimSize> dimensions;
        ElementType elements;
        vespalib::string type_as_string() const;
        ~TensorInfo();
    };

    // concrete tensor type with known dimension sizes
    struct TensorType {
        ElementType elements;
        std::vector<int64_t> dimensions;
        TensorType(ElementType elements_in, std::vector<int64_t> dimensions_in) noexcept
            : elements(elements_in), dimensions(std::move(dimensions_in)) {}
        vespalib::string type_as_string() const;
    };

    // how the model should be wired with inputs/outputs
    struct WireInfo {
        std::vector<ValueType>  vespa_inputs;
        std::vector<Onnx::TensorType> onnx_inputs;
        std::vector<Onnx::TensorType> onnx_outputs;
        std::vector<ValueType>  vespa_outputs;
        ~WireInfo();
    };

    // planning how we should wire the model based on input types
    class WirePlanner {
    private:
        std::map<vespalib::string,ValueType> _input_types;
        std::map<vespalib::string,size_t> _symbolic_sizes;
        std::map<vespalib::string,Onnx::TensorType> _output_types;

        bool need_model_probe(const Onnx &model) const;
        void do_model_probe(const Onnx &model);
    public:
        WirePlanner() : _input_types(), _symbolic_sizes(), _output_types() {}
        ~WirePlanner();
        static CellType best_cell_type(Onnx::ElementType type);
        bool bind_input_type(const ValueType &vespa_in, const TensorInfo &onnx_in);
        std::map<vespalib::string,size_t> get_bound_sizes(const TensorInfo &onnx_in) const;
        void prepare_output_types(const Onnx &model);
        ValueType make_output_type(const TensorInfo &onnx_out) const;
        WireInfo get_wire_info(const Onnx &model) const;
    };

    // evaluation context; use one per thread and keep model/wire_info alive
    // all parameter values are expected to be bound per evaluation
    // output values are pre-allocated and will not change
    class EvalContext {
    private:
        using param_fun_t = void (*)(EvalContext &, size_t i, const Value &);
        using result_fun_t = void (*)(EvalContext &, size_t i);

        const Onnx                  &_model;
        const WireInfo              &_wire_info;
        Ort::MemoryInfo              _cpu_memory;
        std::vector<Ort::Value>      _param_values;
        std::vector<Ort::Value>      _result_values;
        std::vector<Value::UP>       _results;
        std::vector<param_fun_t>     _param_binders;
        std::vector<std::pair<size_t,result_fun_t>> _result_converters;

        template <typename T>
        static void adapt_param(EvalContext &self, size_t idx, const Value &param);

        template <typename SRC, typename DST>
        static void convert_param(EvalContext &self, size_t idx, const Value &param);

        template <typename SRC, typename DST>
        static void convert_result(EvalContext &self, size_t idx);

    public:
        struct SelectAdaptParam;
        struct SelectConvertParam;
        struct SelectConvertResult;

        EvalContext(const Onnx &model, const WireInfo &wire_info);
        ~EvalContext();
        size_t num_params() const { return _param_values.size(); }
        size_t num_results() const { return _result_values.size(); }
        void bind_param(size_t i, const Value &param);
        void eval();
        void clear_results();
        const Value &get_result(size_t i) const;
    };

private:
    // common stuff shared between model sessions
    class Shared {
    private:
        Ort::Env _env;
        Shared();
    public:
        static Shared &get();
        Ort::Env &env() { return _env; }
    };

    static Ort::AllocatorWithDefaultOptions _alloc;

    Shared                   &_shared;
    Ort::SessionOptions       _options;
    Ort::Session              _session;
    std::vector<TensorInfo>   _inputs;
    std::vector<TensorInfo>   _outputs;
    std::vector<const char *> _input_name_refs;
    std::vector<const char *> _output_name_refs;

    void extract_meta_data() __attribute__((noinline));

public:
    Onnx(const vespalib::string &model_file, Optimize optimize);
    ~Onnx();
    const std::vector<TensorInfo> &inputs() const { return _inputs; }
    const std::vector<TensorInfo> &outputs() const { return _outputs; }
};

}
