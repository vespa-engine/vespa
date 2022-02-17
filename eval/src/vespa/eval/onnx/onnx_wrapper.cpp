// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "onnx_wrapper.h"
#include <vespa/eval/eval/cell_type.h>
#include <vespa/eval/eval/dense_cells_value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/util/classname.h>
#include <assert.h>
#include <cmath>
#include <stdlib.h>
#include <stdio.h>
#include <type_traits>

#include <vespa/log/log.h>
LOG_SETUP(".eval.onnx_wrapper");

using vespalib::ArrayRef;
using vespalib::ConstArrayRef;

using vespalib::make_string_short::fmt;

// as documented in onnxruntime_cxx_api.h :
namespace Ort {
template <>
struct TypeToTensorType<vespalib::BFloat16> { static constexpr ONNXTensorElementDataType type = ONNX_TENSOR_ELEMENT_DATA_TYPE_BFLOAT16; };
template <>
struct TypeToTensorType<vespalib::eval::Int8Float> { static constexpr ONNXTensorElementDataType type = ONNX_TENSOR_ELEMENT_DATA_TYPE_INT8; };
}

namespace vespalib::eval {

namespace {

struct TypifyOnnxElementType {
    template <typename T> using Result = TypifyResultType<T>;
    template <typename F> static decltype(auto) resolve(Onnx::ElementType value, F &&f) {
        switch(value) {
        case Onnx::ElementType::INT8:     return f(Result<Int8Float>());
        case Onnx::ElementType::INT16:    return f(Result<int16_t>());
        case Onnx::ElementType::INT32:    return f(Result<int32_t>());
        case Onnx::ElementType::INT64:    return f(Result<int64_t>());
        case Onnx::ElementType::UINT8:    return f(Result<uint8_t>());
        case Onnx::ElementType::UINT16:   return f(Result<uint16_t>());
        case Onnx::ElementType::UINT32:   return f(Result<uint32_t>());
        case Onnx::ElementType::UINT64:   return f(Result<uint64_t>());
        case Onnx::ElementType::BFLOAT16: return f(Result<BFloat16>());
        case Onnx::ElementType::FLOAT:    return f(Result<float>());
        case Onnx::ElementType::DOUBLE:   return f(Result<double>());
        }
        abort();
    }
};

using MyTypify = TypifyValue<TypifyCellType,TypifyOnnxElementType>;

//-----------------------------------------------------------------------------

struct TypeToString {
    template <typename T> static vespalib::string invoke() { return getClassName<T>(); }
};

struct IsSameType {
    template <typename A, typename B> static bool invoke() { return std::is_same<A,B>(); }
};

struct CreateOnnxTensor {
    template <typename T> static Ort::Value invoke(const std::vector<int64_t> &sizes, OrtAllocator *alloc) {
        return Ort::Value::CreateTensor<T>(alloc, sizes.data(), sizes.size());
    }
    Ort::Value operator()(const Onnx::TensorType &type, OrtAllocator *alloc) {
        return typify_invoke<1,MyTypify,CreateOnnxTensor>(type.elements, type.dimensions, alloc);
    }
};

struct CreateEmptyOnnxTensor {
    template <typename T> static Ort::Value invoke(const std::vector<int64_t> &sizes, size_t num_cells, OrtAllocator *alloc) {
        auto value = Ort::Value::CreateTensor<T>(alloc, sizes.data(), sizes.size());
        T *cells = value.template GetTensorMutableData<T>();
        for (size_t i = 0; i < num_cells; ++i) {
            cells[i] = T{};
        }
        return value;
    }
    Ort::Value operator()(Onnx::ElementType elements, const std::vector<int64_t> &sizes, size_t num_cells, OrtAllocator *alloc) {
        return typify_invoke<1,MyTypify,CreateEmptyOnnxTensor>(elements, sizes, num_cells, alloc);
    }
};
CreateEmptyOnnxTensor create_empty_onnx_tensor;

struct CreateVespaTensorRef {
    template <typename T> static Value::UP invoke(const ValueType &type_ref, Ort::Value &value) {
        size_t num_cells = type_ref.dense_subspace_size();
        ConstArrayRef<T> cells(value.GetTensorMutableData<T>(), num_cells);
        return std::make_unique<DenseValueView>(type_ref, TypedCells(cells));
    }
    Value::UP operator()(const ValueType &type_ref, Ort::Value &value) {
        return typify_invoke<1,MyTypify,CreateVespaTensorRef>(type_ref.cell_type(), type_ref, value);
    }
};

struct CreateVespaTensor {
    template <typename T> static Value::UP invoke(const ValueType &type) {
        size_t num_cells = type.dense_subspace_size();
        std::vector<T> cells(num_cells, T{});
        return std::make_unique<DenseCellsValue<T>>(type, std::move(cells));
    }
    Value::UP operator()(const ValueType &type) {
        return typify_invoke<1,MyTypify,CreateVespaTensor>(type.cell_type(), type);
    }
};

struct ClearVespaTensor {
    template <typename CT> static void invoke(const Value &value) {
        auto cells = unconstify(value.cells().typify<CT>());
        std::fill(cells.begin(), cells.end(), CT{});
    }
    void operator()(const Value &value) {
        return typify_invoke<1,TypifyCellType,ClearVespaTensor>(value.type().cell_type(), value);
    }
};
ClearVespaTensor clear_vespa_tensor;

//-----------------------------------------------------------------------------

template <typename E> vespalib::string type_name(E enum_value) {
    return typify_invoke<1,MyTypify,TypeToString>(enum_value);
}

template <typename E1, typename E2> bool is_same_type(E1 e1, E2 e2) {
    return typify_invoke<2,MyTypify,IsSameType>(e1, e2);
}

//-----------------------------------------------------------------------------

auto convert_optimize(Onnx::Optimize optimize) {
    switch (optimize) {
    case Onnx::Optimize::ENABLE:  return ORT_ENABLE_ALL;
    case Onnx::Optimize::DISABLE: return ORT_DISABLE_ALL;
    }
    abort();
}

Onnx::ElementType make_element_type(ONNXTensorElementDataType element_type) {
    switch (element_type) {
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_INT8:     return Onnx::ElementType::INT8;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_INT16:    return Onnx::ElementType::INT16;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_INT32:    return Onnx::ElementType::INT32;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64:    return Onnx::ElementType::INT64;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT8:    return Onnx::ElementType::UINT8;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT16:   return Onnx::ElementType::UINT16;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT32:   return Onnx::ElementType::UINT32;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT64:   return Onnx::ElementType::UINT64;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_BFLOAT16: return Onnx::ElementType::BFLOAT16;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT:    return Onnx::ElementType::FLOAT;
    case ONNX_TENSOR_ELEMENT_DATA_TYPE_DOUBLE:   return Onnx::ElementType::DOUBLE;
    default:
        throw Ort::Exception(fmt("[onnx wrapper] unsupported element type: %d", element_type), ORT_FAIL);
    }
}

//-----------------------------------------------------------------------------

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

Onnx::TensorInfo make_tensor_info(const OnnxString &name, const Ort::TypeInfo &type_info) {
    auto tensor_info = type_info.GetTensorTypeAndShapeInfo();
    auto element_type = tensor_info.GetElementType();
    return Onnx::TensorInfo{vespalib::string(name.get()), make_dimensions(tensor_info), make_element_type(element_type)};
}

Onnx::TensorType get_type_of(const Ort::Value &value) {
    auto tensor_info = value.GetTensorTypeAndShapeInfo();
    auto element_type = tensor_info.GetElementType();
    auto shape = tensor_info.GetShape();
    for (int64_t dim_size: shape) {
        if (dim_size < 1) {
            throw Ort::Exception("[onnx wrapper] actual value has unknown dimension size", ORT_FAIL);
        }
    }
    return Onnx::TensorType(make_element_type(element_type), shape);
}

std::vector<int64_t> extract_sizes(const ValueType &type) {
    std::vector<int64_t> sizes;
    for (const auto &dim: type.dimensions()) {
        sizes.push_back(dim.size);
    }
    return sizes;
}

} // <unnamed>

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
    vespalib::string res = type_name(elements);
    for (const auto &dim: dimensions) {
        res += dim.as_string();
    }
    return res;
}

Onnx::TensorInfo::~TensorInfo() = default;

vespalib::string
Onnx::TensorType::type_as_string() const
{
    vespalib::string res = type_name(elements);
    for (const auto &size: dimensions) {
        res += DimSize(size).as_string();
    }
    return res;
}

//-----------------------------------------------------------------------------

Onnx::WireInfo::~WireInfo() = default;

bool
Onnx::WirePlanner::need_model_probe(const Onnx &model) const
{
    for (const auto &output: model.outputs()) {
        for (const auto &dim: output.dimensions) {
            if (dim.is_symbolic()) {
                if (_symbolic_sizes.find(dim.name) == _symbolic_sizes.end()) {
                    // symbolic output dimension with unknown size
                    return true;
                }
            } else if (dim.value == 0) {
                // non-symbolic output dimension with unknown size
                return true;
            }
        }
    }
    return false;
}

void
Onnx::WirePlanner::do_model_probe(const Onnx &model)
{
    try {
        std::vector<Ort::Value> param_values;
        param_values.reserve(model.inputs().size());
        for (const auto &input: model.inputs()) {
            const auto &pos = _input_types.find(input.name);
            assert(pos != _input_types.end());
            auto vespa_type = pos->second;
            auto sizes = extract_sizes(vespa_type);
            size_t num_cells = vespa_type.dense_subspace_size();
            param_values.push_back(create_empty_onnx_tensor(input.elements, sizes, num_cells, _alloc));
        }
        std::vector<Ort::Value> result_values;
        result_values.reserve(model.outputs().size());
        for (size_t i = 0; i < model.outputs().size(); ++i) {
            result_values.emplace_back(nullptr);
        }
        Ort::RunOptions run_opts(nullptr);
        Ort::Session &session = const_cast<Ort::Session&>(model._session);
        session.Run(run_opts,
                    model._input_name_refs.data(), param_values.data(), param_values.size(),
                    model._output_name_refs.data(), result_values.data(), result_values.size());
        for (size_t i = 0; i < model.outputs().size(); ++i) {
            _output_types.emplace(model.outputs()[i].name, get_type_of(result_values[i]));
        }
    } catch (const Ort::Exception &ex) {
        _output_types.clear();
        LOG(warning, "model probe failed: %s", ex.what());
    }
}

Onnx::WirePlanner::~WirePlanner() = default;

CellType
Onnx::WirePlanner::best_cell_type(Onnx::ElementType type)
{
    switch (type) {
    case Onnx::ElementType::INT8:     return CellType::INT8;
    case Onnx::ElementType::BFLOAT16: return CellType::BFLOAT16;
    case Onnx::ElementType::UINT8:    [[fallthrough]];
    case Onnx::ElementType::INT16:    [[fallthrough]];
    case Onnx::ElementType::UINT16:   [[fallthrough]];
    case Onnx::ElementType::FLOAT:    return CellType::FLOAT;
    case Onnx::ElementType::INT32:    [[fallthrough]];
    case Onnx::ElementType::INT64:    [[fallthrough]];
    case Onnx::ElementType::UINT32:   [[fallthrough]];
    case Onnx::ElementType::UINT64:   [[fallthrough]];
    case Onnx::ElementType::DOUBLE:   return CellType::DOUBLE;
    }
    abort();
}

bool
Onnx::WirePlanner::bind_input_type(const ValueType &vespa_in, const TensorInfo &onnx_in)
{
    const auto &type = vespa_in;
    const auto &name = onnx_in.name;
    const auto &dimensions = onnx_in.dimensions;
    if (type.dimensions().size() != dimensions.size()) {
        return false;
    }
    for (size_t i = 0; i < dimensions.size(); ++i) {
        if (dimensions[i].is_known()) {
            if (dimensions[i].value != type.dimensions()[i].size) {
                return false;
            }
        } else {
            if (dimensions[i].is_symbolic()) {
                auto &bound_size = _symbolic_sizes[dimensions[i].name];
                if (bound_size == 0) {
                    bound_size = type.dimensions()[i].size;
                } else if (bound_size != type.dimensions()[i].size) {
                    return false;
                }
            }
        }
    }
    _input_types.emplace(name, type);
    return true;
}

std::map<vespalib::string,size_t>
Onnx::WirePlanner::get_bound_sizes(const TensorInfo &onnx_in) const
{
    std::map<vespalib::string,size_t> result;
    for (const auto &dim: onnx_in.dimensions) {
        if (dim.is_symbolic()) {
            auto pos = _symbolic_sizes.find(dim.name);
            if (pos != _symbolic_sizes.end()) {
                assert(pos->second != 0);
                result.emplace(dim.name, pos->second);
            }
        }
    }
    return result;
}

void
Onnx::WirePlanner::prepare_output_types(const Onnx &model)
{
    if (need_model_probe(model)) {
        do_model_probe(model);
    }
}

ValueType
Onnx::WirePlanner::make_output_type(const TensorInfo &onnx_out) const
{
    const auto &dimensions = onnx_out.dimensions;
    const auto &elements = onnx_out.elements;
    auto probed = _output_types.find(onnx_out.name);
    std::vector<ValueType::Dimension> dim_list;
    for (size_t i = 0; i < dimensions.size(); ++i) {
        const auto &dim = dimensions[i];
        size_t dim_size = dim.value;
        if (dim.is_symbolic()) {
            auto pos = _symbolic_sizes.find(dim.name);
            if (pos != _symbolic_sizes.end()) {
                dim_size = pos->second;
            }
        }
        if (probed != _output_types.end()) {
            const auto &type = probed->second;
            if (type.dimensions.size() != dimensions.size()) {
                LOG(warning, "probed output '%s' does not have the same number of dimensions as "
                    "the output declared by the model (probed: %zu, declared: %zu)", onnx_out.name.c_str(),
                    type.dimensions.size(), dimensions.size());
                return ValueType::error_type();
            }
            size_t probed_size = type.dimensions[i];
            if (dim_size == 0) {
                dim_size = probed_size;
            } else if (probed_size != dim_size) {
                LOG(warning, "probed dimension size for output '%s' dimension %zu does not match symbolic "
                    "dimension size inferred from inputs (probed: %zu, inferred: %zu)", onnx_out.name.c_str(),
                    i, probed_size, dim_size);
                return ValueType::error_type();
            }
        }
        if ((dim_size == 0) || (dim_list.size() > 9)) {
            return ValueType::error_type();
        }
        dim_list.emplace_back(fmt("d%zu", dim_list.size()), dim_size);
    }
    return ValueType::make_type(best_cell_type(elements), std::move(dim_list));
}

Onnx::WireInfo
Onnx::WirePlanner::get_wire_info(const Onnx &model) const
{
    WireInfo info;
    for (const auto &input: model.inputs()) {
        const auto &pos = _input_types.find(input.name);
        assert(pos != _input_types.end());
        auto vespa_type = pos->second;
        info.onnx_inputs.emplace_back(input.elements, extract_sizes(vespa_type));
        info.vespa_inputs.push_back(std::move(vespa_type));
        if (!is_same_type(info.vespa_inputs.back().cell_type(),
                          info.onnx_inputs.back().elements))
        {
            LOG(warning, "input '%s' with element type '%s' is bound to vespa value with cell type '%s'; "
                "adding explicit conversion step (this conversion might be lossy)",
                input.name.c_str(), type_name(info.onnx_inputs.back().elements).c_str(),
                type_name(info.vespa_inputs.back().cell_type()).c_str());
        }
    }
    for (const auto &output: model.outputs()) {
        auto vespa_type = make_output_type(output);
        info.onnx_outputs.emplace_back(output.elements, extract_sizes(vespa_type));
        info.vespa_outputs.push_back(std::move(vespa_type));
        if (!is_same_type(info.vespa_outputs.back().cell_type(),
                          info.onnx_outputs.back().elements))
        {
            LOG(warning, "output '%s' with element type '%s' is bound to vespa value with cell type '%s'; "
                "adding explicit conversion step (this conversion might be lossy)",
                output.name.c_str(), type_name(info.onnx_outputs.back().elements).c_str(),
                type_name(info.vespa_outputs.back().cell_type()).c_str());
        }
    }
    return info;
}

//-----------------------------------------------------------------------------

template <typename T>
void
Onnx::EvalContext::adapt_param(EvalContext &self, size_t idx, const Value &param)
{
    const auto &cells_ref = param.cells();
    auto cells = unconstify(cells_ref.typify<T>());
    const auto &sizes = self._wire_info.onnx_inputs[idx].dimensions;
    self._param_values[idx] = Ort::Value::CreateTensor<T>(self._cpu_memory, cells.begin(), cells.size(), sizes.data(), sizes.size());
}

template <typename SRC, typename DST>
void
Onnx::EvalContext::convert_param(EvalContext &self, size_t idx, const Value &param)
{
    auto cells = param.cells().typify<SRC>();
    size_t n = cells.size();
    const SRC *src = cells.begin();
    DST *dst = self._param_values[idx].GetTensorMutableData<DST>();
    for (size_t i = 0; i < n; ++i) {
        dst[i] = DST(src[i]);
    }
}

template <typename SRC, typename DST>
void
Onnx::EvalContext::convert_result(EvalContext &self, size_t idx)
{
    const auto &cells_ref = (*self._results[idx]).cells();
    auto cells = unconstify(cells_ref.typify<DST>());
    size_t n = cells.size();
    DST *dst = cells.begin();
    const SRC *src = self._result_values[idx].GetTensorMutableData<SRC>();
    for (size_t i = 0; i < n; ++i) {
        dst[i] = DST(src[i]);
    }
}

struct Onnx::EvalContext::SelectAdaptParam {
    template <typename ...Ts> static auto invoke() { return adapt_param<Ts...>; }
    auto operator()(CellType ct) {
        return typify_invoke<1,MyTypify,SelectAdaptParam>(ct);
    }
};

struct Onnx::EvalContext::SelectConvertParam {
    template <typename ...Ts> static auto invoke() { return convert_param<Ts...>; }
    auto operator()(CellType ct, Onnx::ElementType et) {
        return typify_invoke<2,MyTypify,SelectConvertParam>(ct, et);
    }
};

struct Onnx::EvalContext::SelectConvertResult {
    template <typename ...Ts> static auto invoke() { return convert_result<Ts...>; }
    auto operator()(Onnx::ElementType et, CellType ct) {
        return typify_invoke<2,MyTypify,SelectConvertResult>(et, ct);
    }
};

//-----------------------------------------------------------------------------

Onnx::EvalContext::EvalContext(const Onnx &model, const WireInfo &wire_info)
    : _model(model),
      _wire_info(wire_info),
      _cpu_memory(Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeDefault)),
      _param_values(),
      _result_values(),
      _results(),
      _param_binders(),
      _result_converters()
{
    assert(_wire_info.vespa_inputs.size()  == _model.inputs().size());
    assert(_wire_info.onnx_inputs.size()   == _model.inputs().size());
    assert(_wire_info.onnx_outputs.size()  == _model.outputs().size());
    assert(_wire_info.vespa_outputs.size() == _model.outputs().size());
    _param_values.reserve(_model.inputs().size());
    _result_values.reserve(_model.outputs().size());
    _results.reserve(_model.outputs().size());
    auto result_guard = _result_values.begin();
    for (size_t i = 0; i < _model.inputs().size(); ++i) {
        const auto &vespa = _wire_info.vespa_inputs[i];
        const auto &onnx = _wire_info.onnx_inputs[i];
        if (is_same_type(vespa.cell_type(), onnx.elements)) {
            _param_values.push_back(Ort::Value(nullptr));
            _param_binders.push_back(SelectAdaptParam()(vespa.cell_type()));
        } else {
            _param_values.push_back(CreateOnnxTensor()(onnx, _alloc));
            _param_binders.push_back(SelectConvertParam()(vespa.cell_type(), onnx.elements));
        }
    }
    for (size_t i = 0; i < _model.outputs().size(); ++i) {
        const auto &vespa = _wire_info.vespa_outputs[i];
        const auto &onnx = _wire_info.onnx_outputs[i];
        _result_values.push_back(CreateOnnxTensor()(onnx, _alloc));
        if (is_same_type(vespa.cell_type(), onnx.elements)) {
            _results.push_back(CreateVespaTensorRef()(vespa, _result_values.back()));
        } else {
            _results.push_back(CreateVespaTensor()(vespa));
            _result_converters.emplace_back(i, SelectConvertResult()(onnx.elements, vespa.cell_type()));
        }
    }
    // make sure references to Ort::Value inside _result_values are safe
    assert(result_guard == _result_values.begin());
}

Onnx::EvalContext::~EvalContext() = default;

void
Onnx::EvalContext::bind_param(size_t i, const Value &param)
{
    _param_binders[i](*this, i, param);
}

void
Onnx::EvalContext::eval()
{
    Ort::Session &session = const_cast<Ort::Session&>(_model._session);
    Ort::RunOptions run_opts(nullptr);
    session.Run(run_opts,
                _model._input_name_refs.data(), _param_values.data(), _param_values.size(),
                _model._output_name_refs.data(), _result_values.data(), _result_values.size());
    for (const auto &entry: _result_converters) {
        entry.second(*this, entry.first);
    }
}

void
Onnx::EvalContext::clear_results()
{
    for (const Value::UP &result: _results) {
        clear_vespa_tensor(*result);
    }
}

const Value &
Onnx::EvalContext::get_result(size_t i) const
{
    return *_results[i];
}

//-----------------------------------------------------------------------------

Ort::AllocatorWithDefaultOptions Onnx::_alloc;

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
        if (_inputs.back().dimensions.empty()) {
            throw Ort::Exception(fmt("[onnx wrapper] input '%s' has unspecified type, this is not supported", _inputs.back().name.c_str()), ORT_FAIL);
        }
    }
    size_t num_outputs = _session.GetOutputCount();
    for (size_t i = 0; i < num_outputs; ++i) {
        _outputs.push_back(make_tensor_info(OnnxString::get_output_name(_session, i), _session.GetOutputTypeInfo(i)));
        if (_outputs.back().dimensions.empty()) {
            throw Ort::Exception(fmt("[onnx wrapper] output '%s' has unspecified type, this is not supported", _outputs.back().name.c_str()), ORT_FAIL);
        }
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
    _options.DisableCpuMemArena();
    _session = Ort::Session(_shared.env(), model_file.c_str(), _options);
    extract_meta_data();
}

Onnx::~Onnx() = default;

}
