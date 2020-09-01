// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onnx_wrapper.h"
#include <vespa/eval/eval/value_type.h>
#include "dense_tensor_view.h"
#include "mutable_dense_tensor_view.h"
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <assert.h>
#include <cmath>
#include <stdlib.h>
#include <stdio.h>

using vespalib::eval::ValueType;
using vespalib::make_string_short::fmt;

namespace vespalib::tensor {

namespace {

vespalib::string to_str(Onnx::TensorInfo::ElementType element_type) {
    if (element_type == Onnx::TensorInfo::ElementType::FLOAT) {
        return "float";
    }
    if (element_type == Onnx::TensorInfo::ElementType::DOUBLE) {
        return "double";
    }
    return "???";
}

ValueType::CellType as_cell_type(Onnx::TensorInfo::ElementType type) {
    if (type == Onnx::TensorInfo::ElementType::FLOAT) {
        return ValueType::CellType::FLOAT;
    }
    if (type == Onnx::TensorInfo::ElementType::DOUBLE) {
        return ValueType::CellType::DOUBLE;
    }
    abort();
}

auto convert_optimize(Onnx::Optimize optimize) {
    if (optimize == Onnx::Optimize::ENABLE) {
        return ORT_ENABLE_ALL;
    } else {
        assert(optimize == Onnx::Optimize::DISABLE);
        return ORT_DISABLE_ALL;
    }
}

class OnnxString {
private:
    static Ort::AllocatorWithDefaultOptions _alloc;
    char *_str;
    void cleanup() {
        if (_str != nullptr) {
            _alloc.Free(_str);
            _str = nullptr;
        }
    }
    OnnxString(char *str) : _str(str) {}
public:
    OnnxString(const OnnxString &rhs) = delete;
    OnnxString &operator=(const OnnxString &rhs) = delete;
    OnnxString(OnnxString &&rhs) : _str(rhs._str) {
        rhs._str = nullptr;
    }
    OnnxString &operator=(OnnxString &&rhs) {
        cleanup();
        _str = rhs._str;
        rhs._str = nullptr;
        return *this;
    }
    const char *get() const { return _str; }
    ~OnnxString() { cleanup(); }
    static OnnxString get_input_name(const Ort::Session &session, size_t idx) {
        return OnnxString(session.GetInputName(idx, _alloc));
    }
    static OnnxString get_output_name(const Ort::Session &session, size_t idx) {
        return OnnxString(session.GetOutputName(idx, _alloc));
    }
};
Ort::AllocatorWithDefaultOptions OnnxString::_alloc;

std::vector<Onnx::DimSize> make_dimensions(const Ort::TensorTypeAndShapeInfo &tensor_info) {
    std::vector<const char *> symbolic_sizes(tensor_info.GetDimensionsCount(), nullptr);
    tensor_info.GetSymbolicDimensions(symbolic_sizes.data(), symbolic_sizes.size());
    auto shape = tensor_info.GetShape();
    std::vector<Onnx::DimSize> result;
    for (size_t i = 0; i < shape.size(); ++i) {
        if (shape[i] > 0) {
            result.emplace_back(shape[i]);
        } else if (symbolic_sizes[i] != nullptr) {
            result.emplace_back(vespalib::string(symbolic_sizes[i]));
        } else {
            result.emplace_back();
        }
    }
    return result;
}

Onnx::TensorInfo::ElementType make_element_type(ONNXTensorElementDataType element_type) {
    if (element_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT) {
        return Onnx::TensorInfo::ElementType::FLOAT;
    } else if (element_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_DOUBLE) {
        return Onnx::TensorInfo::ElementType::DOUBLE;
    } else {
        return Onnx::TensorInfo::ElementType::UNKNOWN;
    }
}

Onnx::TensorInfo make_tensor_info(const OnnxString &name, const Ort::TypeInfo &type_info) {
    auto tensor_info = type_info.GetTensorTypeAndShapeInfo();
    auto element_type = tensor_info.GetElementType();
    return Onnx::TensorInfo{vespalib::string(name.get()), make_dimensions(tensor_info), make_element_type(element_type)};
}

}

vespalib::string
Onnx::DimSize::as_string() const
{
    if (is_known()) {
        return fmt("[%zu]", value);
    } else if (is_symbolic()) {
        return fmt("[%s]", name.c_str());
    } else {
        return "[]";
    }
}

vespalib::string
Onnx::TensorInfo::type_as_string() const
{
    vespalib::string res = to_str(elements);
    for (const auto &dim: dimensions) {
        res += dim.as_string();
    }
    return res;
}

Onnx::TensorInfo::~TensorInfo() = default;

//-----------------------------------------------------------------------------

Onnx::WirePlanner::~WirePlanner() = default;

bool
Onnx::WirePlanner::bind_input_type(const eval::ValueType &vespa_in, const TensorInfo &onnx_in)
{
    const auto &type = vespa_in;
    const auto &name = onnx_in.name;
    const auto &dimensions = onnx_in.dimensions;
    const auto &elements = onnx_in.elements;
    if ((elements == TensorInfo::ElementType::UNKNOWN) || dimensions.empty()) {
        return false;
    }
    if (type.cell_type() != as_cell_type(elements)) {
        return false;
    }
    if (type.dimensions().size() != dimensions.size()) {
        return false;
    }
    for (size_t i = 0; i < dimensions.size(); ++i) {
        if (dimensions[i].is_known()) {
            if (dimensions[i].value != type.dimensions()[i].size) {
                return false;
            }
        } else if (dimensions[i].is_symbolic()) {
            auto &bound_size = _symbolic_sizes[dimensions[i].name];
            if (bound_size == 0) {
                bound_size = type.dimensions()[i].size;
            } else if (bound_size != type.dimensions()[i].size) {
                return false;
            }
        } else {
            _unknown_sizes[std::make_pair(name,i)] = type.dimensions()[i].size;
        }
    }
    return true;
}

eval::ValueType
Onnx::WirePlanner::make_output_type(const TensorInfo &onnx_out) const
{
    const auto &dimensions = onnx_out.dimensions;
    const auto &elements = onnx_out.elements;
    if ((elements == TensorInfo::ElementType::UNKNOWN) || dimensions.empty()) {
        return ValueType::error_type();
    }
    std::vector<ValueType::Dimension> dim_list;
    for (const auto &dim: dimensions) {
        size_t dim_size = dim.value;
        if (dim.is_symbolic()) {
            auto pos = _symbolic_sizes.find(dim.name);
            if (pos != _symbolic_sizes.end()) {
                dim_size = pos->second;
            }
        }
        if ((dim_size == 0) || (dim_list.size() > 9)) {
            return ValueType::error_type();
        }
        dim_list.emplace_back(fmt("d%zu", dim_list.size()), dim_size);
    }
    return ValueType::tensor_type(std::move(dim_list), as_cell_type(elements));
}

Onnx::WireInfo
Onnx::WirePlanner::get_wire_info(const Onnx &model) const
{
    WireInfo info;
    for (const auto &input: model.inputs()) {
        size_t input_idx = 0;
        std::vector<int64_t> sizes;
        for (const auto &dim: input.dimensions) {
            if (dim.is_known()) {
                sizes.push_back(dim.value);
            } else if (dim.is_symbolic()) {
                const auto &pos = _symbolic_sizes.find(dim.name);
                assert(pos != _symbolic_sizes.end());
                sizes.push_back(pos->second);
            } else {
                const auto &pos = _unknown_sizes.find(std::make_pair(input.name, input_idx));
                assert(pos != _unknown_sizes.end());
                sizes.push_back(pos->second);
            }
            ++input_idx;
        }
        info.input_sizes.push_back(sizes);
    }
    for (const auto &output: model.outputs()) {
        info.output_types.push_back(make_output_type(output));
    }
    return info;
}

//-----------------------------------------------------------------------------

Ort::AllocatorWithDefaultOptions Onnx::EvalContext::_alloc;

Onnx::EvalContext::EvalContext(const Onnx &model, const WireInfo &wire_info)
    : _model(model),
      _wire_info(wire_info),
      _cpu_memory(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault)),
      _param_values(),
      _result_values(),
      _result_views()
{
    assert(_wire_info.input_sizes.size() == _model.inputs().size());
    assert(_wire_info.output_types.size() == _model.outputs().size());
    for (const auto &input: _wire_info.input_sizes) {
        (void) input;
        _param_values.push_back(Ort::Value(nullptr));
    }
    std::vector<int64_t> dim_sizes;
    size_t num_cells;
    dim_sizes.reserve(16);
    // NB: output type must be reference inside vector since the view does not copy it
    for (const auto &output: _wire_info.output_types) {
        num_cells = 1;
        dim_sizes.clear();
        for (const auto &dim: output.dimensions()) {
            dim_sizes.push_back(dim.size);
            num_cells *= dim.size;
        }
        if (output.cell_type() == ValueType::CellType::FLOAT) {
            _result_values.push_back(Ort::Value::CreateTensor<float>(_alloc, dim_sizes.data(), dim_sizes.size()));
            ConstArrayRef<float> cells(_result_values.back().GetTensorMutableData<float>(), num_cells);
            _result_views.emplace_back(output, TypedCells(cells));
        } else {
            assert(output.cell_type() == ValueType::CellType::DOUBLE);
            _result_values.push_back(Ort::Value::CreateTensor<double>(_alloc, dim_sizes.data(), dim_sizes.size()));
            ConstArrayRef<double> cells(_result_values.back().GetTensorMutableData<double>(), num_cells);
            _result_views.emplace_back(output, TypedCells(cells));
        }
    }
}

Onnx::EvalContext::~EvalContext() = default;

void
Onnx::EvalContext::bind_param(size_t i, const eval::Value &param)
{
    // NB: dense tensors are always (sub)classes of DenseTensorView
    const auto &cells_ref = static_cast<const DenseTensorView &>(param).cellsRef();
    const auto &input_sizes = _wire_info.input_sizes;
    if (cells_ref.type == ValueType::CellType::FLOAT) {
        // NB: create requires non-const input
        auto cells = unconstify(cells_ref.typify<float>());
        _param_values[i] = Ort::Value::CreateTensor<float>(_cpu_memory, cells.begin(), cells.size(), input_sizes[i].data(), input_sizes[i].size());
    } else {
        assert(cells_ref.type == ValueType::CellType::DOUBLE);
        // NB: create requires non-const input
        auto cells = unconstify(cells_ref.typify<double>());
        _param_values[i] = Ort::Value::CreateTensor<double>(_cpu_memory, cells.begin(), cells.size(), input_sizes[i].data(), input_sizes[i].size());
    }
}

void
Onnx::EvalContext::eval()
{
    // NB: Run requires non-const session
    Ort::Session &session = const_cast<Ort::Session&>(_model._session);
    Ort::RunOptions run_opts(nullptr);
    session.Run(run_opts,
                _model._input_name_refs.data(), _param_values.data(), _param_values.size(),
                _model._output_name_refs.data(), _result_values.data(), _result_values.size());
}

const eval::Value &
Onnx::EvalContext::get_result(size_t i) const
{
    return _result_views[i];
}

//-----------------------------------------------------------------------------

Onnx::Shared::Shared()
    : _env(ORT_LOGGING_LEVEL_WARNING, "vespa-onnx-wrapper")
{
}

Onnx::Shared &
Onnx::Shared::get() {
    static Shared shared;
    return shared;
}

//-----------------------------------------------------------------------------

void
Onnx::extract_meta_data()
{
    Ort::AllocatorWithDefaultOptions allocator;
    size_t num_inputs = _session.GetInputCount();
    for (size_t i = 0; i < num_inputs; ++i) {
        _inputs.push_back(make_tensor_info(OnnxString::get_input_name(_session, i), _session.GetInputTypeInfo(i)));
    }
    size_t num_outputs = _session.GetOutputCount();
    for (size_t i = 0; i < num_outputs; ++i) {
        _outputs.push_back(make_tensor_info(OnnxString::get_output_name(_session, i), _session.GetOutputTypeInfo(i)));
    }
    for (const auto &input: _inputs) {
        _input_name_refs.push_back(input.name.c_str());
    }
    for (const auto &output: _outputs) {
        _output_name_refs.push_back(output.name.c_str());
    }
}

Onnx::Onnx(const vespalib::string &model_file, Optimize optimize)
    : _shared(Shared::get()),
      _options(),
      _session(nullptr),
      _inputs(),
      _outputs(),
      _input_name_refs(),
      _output_name_refs()
{
    _options.SetIntraOpNumThreads(1);
    _options.SetInterOpNumThreads(1);
    _options.SetGraphOptimizationLevel(convert_optimize(optimize));
    _session = Ort::Session(_shared.env(), model_file.c_str(), _options);
    extract_meta_data();
}

Onnx::~Onnx() = default;

}
