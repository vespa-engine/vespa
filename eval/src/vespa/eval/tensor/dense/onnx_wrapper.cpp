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

ValueType::CellType as_cell_type(OnnxWrapper::TensorInfo::ElementType type) {
    if (type == OnnxWrapper::TensorInfo::ElementType::FLOAT) {
        return ValueType::CellType::FLOAT;
    }
    if (type == OnnxWrapper::TensorInfo::ElementType::DOUBLE) {
        return ValueType::CellType::DOUBLE;
    }
    abort();
}

auto convert_optimize(OnnxWrapper::Optimize optimize) {
    if (optimize == OnnxWrapper::Optimize::ENABLE) {
        return ORT_ENABLE_ALL;
    } else {
        assert(optimize == OnnxWrapper::Optimize::DISABLE);
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

std::vector<size_t> make_dimensions(const std::vector<int64_t> &shape) {
    std::vector<size_t> result;
    for (int64_t size: shape) {
        result.push_back(std::max(size, 0L));
    } 
    return result;
}

OnnxWrapper::TensorInfo::ElementType make_element_type(ONNXTensorElementDataType element_type) {
    if (element_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT) {
        return OnnxWrapper::TensorInfo::ElementType::FLOAT;
    } else if (element_type == ONNX_TENSOR_ELEMENT_DATA_TYPE_DOUBLE) {
        return OnnxWrapper::TensorInfo::ElementType::DOUBLE;
    } else {
        return OnnxWrapper::TensorInfo::ElementType::UNKNOWN;
    }
}

OnnxWrapper::TensorInfo make_tensor_info(const OnnxString &name, const Ort::TypeInfo &type_info) {
    auto tensor_info = type_info.GetTensorTypeAndShapeInfo();
    auto shape = tensor_info.GetShape();
    auto element_type = tensor_info.GetElementType();
    return OnnxWrapper::TensorInfo{vespalib::string(name.get()), make_dimensions(shape), make_element_type(element_type)};
}

}

bool
OnnxWrapper::TensorInfo::is_compatible(const eval::ValueType &type) const
{
    if ((elements == ElementType::UNKNOWN) || dimensions.empty()) {
        return false;
    }
    if (type.cell_type() != as_cell_type(elements)) {
        return false;
    }
    if (type.dimensions().size() != dimensions.size()) {
        return false;
    }
    for (size_t i = 0; i < dimensions.size(); ++i) {
        if (type.dimensions()[i].size != dimensions[i]) {
            return false;
        }
    }
    return true;
}

eval::ValueType
OnnxWrapper::TensorInfo::make_compatible_type() const
{
    if ((elements == ElementType::UNKNOWN) || dimensions.empty()) {
        return ValueType::error_type();
    }
    std::vector<ValueType::Dimension> dim_list;
    for (size_t dim_size: dimensions) {
        if ((dim_size == 0) || (dim_list.size() > 9)) {
            return ValueType::error_type();
        }
        dim_list.emplace_back(fmt("d%zu", dim_list.size()), dim_size);
    }
    return ValueType::tensor_type(std::move(dim_list), as_cell_type(elements));
}

OnnxWrapper::TensorInfo::~TensorInfo() = default;

OnnxWrapper::Shared::Shared()
    : _env(ORT_LOGGING_LEVEL_WARNING, "vespa-onnx-wrapper")
{
}

void
OnnxWrapper::Params::bind(size_t idx, const DenseTensorView &src)
{
    assert(idx == values.size());
    std::vector<int64_t> dim_sizes;
    for (const auto &dim: src.fast_type().dimensions()) {
        dim_sizes.push_back(dim.size);
    }
    auto memory_info = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    if (src.fast_type().cell_type() == ValueType::CellType::FLOAT) {
        // NB: create requires non-const input
        auto cells = unconstify(src.cellsRef().typify<float>());
        values.push_back(Ort::Value::CreateTensor<float>(memory_info, cells.begin(), cells.size(), dim_sizes.data(), dim_sizes.size()));
    } else if (src.fast_type().cell_type() == ValueType::CellType::DOUBLE) {
        // NB: create requires non-const input
        auto cells = unconstify(src.cellsRef().typify<double>());
        values.push_back(Ort::Value::CreateTensor<double>(memory_info, cells.begin(), cells.size(), dim_sizes.data(), dim_sizes.size()));
    }
}

void
OnnxWrapper::Result::get(size_t idx, MutableDenseTensorView &dst)
{
    assert(values[idx].IsTensor());
    auto meta = values[idx].GetTensorTypeAndShapeInfo();
    if (dst.fast_type().cell_type() == ValueType::CellType::FLOAT) {
        assert(meta.GetElementType() == ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT);
        ConstArrayRef<float> cells(values[idx].GetTensorMutableData<float>(), meta.GetElementCount());
        dst.setCells(TypedCells(cells));
    } else if (dst.fast_type().cell_type() == ValueType::CellType::DOUBLE) {
        assert(meta.GetElementType() == ONNX_TENSOR_ELEMENT_DATA_TYPE_DOUBLE);
        ConstArrayRef<double> cells(values[idx].GetTensorMutableData<double>(), meta.GetElementCount());
        dst.setCells(TypedCells(cells));
    }
}

OnnxWrapper::Shared &
OnnxWrapper::Shared::get() {
    static Shared shared;
    return shared;
}

void
OnnxWrapper::extract_meta_data()
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

OnnxWrapper::OnnxWrapper(const vespalib::string &model_file, Optimize optimize)
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

OnnxWrapper::~OnnxWrapper() = default;

OnnxWrapper::Result
OnnxWrapper::eval(const Params &params)
{
    assert(params.values.size() == _inputs.size());
    Ort::RunOptions run_opts(nullptr);
    return Result(_session.Run(run_opts, _input_name_refs.data(), params.values.data(), _inputs.size(),
                               _output_name_refs.data(), _outputs.size()));
}

}
