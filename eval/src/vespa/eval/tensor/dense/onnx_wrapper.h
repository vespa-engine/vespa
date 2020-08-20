// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <onnxruntime/onnxruntime_cxx_api.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/eval/eval/value_type.h>
#include <vector>

namespace vespalib::tensor {

class DenseTensorView;
class MutableDenseTensorView;

/**
 * Wrapper around an ONNX model handeled by onnxruntime.
 **/
class OnnxWrapper {
public:
    // model optimization
    enum class Optimize { ENABLE, DISABLE };

    // information about a single input or output tensor
    struct TensorInfo {
        enum class ElementType { FLOAT, DOUBLE, UNKNOWN };
        vespalib::string name;
        std::vector<size_t> dimensions;
        ElementType elements;
        bool is_compatible(const eval::ValueType &type) const;
        eval::ValueType make_compatible_type() const;
        ~TensorInfo();
    };

    // used to build model parameters
    class Params {
        friend class OnnxWrapper;
    private:
        std::vector<Ort::Value> values;
    public:
        Params() : values() {}
        void bind(size_t idx, const DenseTensorView &src);
    };

    // used to inspect model results
    class Result {
        friend class OnnxWrapper;
    private:
        std::vector<Ort::Value> values;
        Result(std::vector<Ort::Value> values_in) : values(std::move(values_in)) {}
    public:
        size_t num_values() const { return values.size(); }
        void get(size_t idx, MutableDenseTensorView &dst);
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

    Shared                   &_shared;
    Ort::SessionOptions       _options;
    Ort::Session              _session;
    std::vector<TensorInfo>   _inputs;
    std::vector<TensorInfo>   _outputs;
    std::vector<const char *> _input_name_refs;
    std::vector<const char *> _output_name_refs;

    void extract_meta_data();

public:
    OnnxWrapper(const vespalib::string &model_file, Optimize optimize);
    ~OnnxWrapper();
    const std::vector<TensorInfo> &inputs() const { return _inputs; }
    const std::vector<TensorInfo> &outputs() const { return _outputs; }
    Result eval(const Params &params); // NB: Run requires non-const session
};

}
