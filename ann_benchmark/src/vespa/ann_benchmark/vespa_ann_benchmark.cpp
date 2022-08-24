// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <pybind11/pybind11.h>
#include <pybind11/stl.h>
#include <vespa/searchcommon/attribute/hnsw_index_params.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/nearest_neighbor_index.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <iostream>
#include <sstream>
#include <limits>

namespace py = pybind11;

using search::AttributeFactory;
using search::AttributeVector;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::CollectionType;
using search::attribute::DistanceMetric;
using search::attribute::HnswIndexParams;
using search::tensor::NearestNeighborIndex;
using search::tensor::TensorAttribute;
using vespalib::eval::CellType;
using vespalib::eval::DenseValueView;
using vespalib::eval::TypedCells;
using vespalib::eval::ValueType;
using vespalib::eval::Value;

namespace vespa_ann_benchmark {

using TopKResult = std::vector<std::pair<uint32_t, double>>;

namespace {

std::string
make_tensor_spec(uint32_t dim_size)
{
    std::ostringstream os;
    os << "tensor<float>(x[" << dim_size << "])";
    return os.str();
}

constexpr uint32_t lid_bias = 1; // lid 0 is reserved

}

/*
 * Class exposing the Vespa implementation of an HNSW index for nearest neighbor search over data points in a high dimensional vector space.
 *
 * A tensor attribute field (https://docs.vespa.ai/en/reference/schema-reference.html#type:tensor) is used to store the vectors in memory.
 * This class only supports single-threaded access (both for indexing and searching),
 * and should only be used for low-level benchmarking.
 * To use nearest neighbor search in a Vespa application,
 * see https://docs.vespa.ai/en/approximate-nn-hnsw.html for more details.
 */
class HnswIndex
{
    ValueType                        _tensor_type;
    HnswIndexParams                  _hnsw_index_params;
    std::shared_ptr<AttributeVector> _attribute;
    TensorAttribute*                 _tensor_attribute;
    const NearestNeighborIndex*      _nearest_neighbor_index;
    size_t                           _dim_size;
    bool                             _normalize_vectors;

    bool check_lid(uint32_t lid);
    bool check_value(const char *op, const std::vector<float>& value);
    TypedCells get_typed_cells(const std::vector<float>& value, std::vector<float>& normalized_value);
public:
    HnswIndex(uint32_t dim_size, const HnswIndexParams &hnsw_index_params, bool normalize_vectors);
    virtual ~HnswIndex();
    void set_vector(uint32_t lid, const std::vector<float>& value);
    std::vector<float> get_vector(uint32_t lid);
    void clear_vector(uint32_t lid);
    TopKResult find_top_k(uint32_t k, const std::vector<float>& value, uint32_t explore_k);
};

HnswIndex::HnswIndex(uint32_t dim_size, const HnswIndexParams &hnsw_index_params, bool normalize_vectors)
    : _tensor_type(ValueType::error_type()),
      _hnsw_index_params(hnsw_index_params),
      _attribute(),
      _tensor_attribute(nullptr),
      _nearest_neighbor_index(nullptr),
      _dim_size(0u),
      _normalize_vectors(normalize_vectors)
{
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    _tensor_type = ValueType::from_spec(make_tensor_spec(dim_size));
    assert(_tensor_type.is_dense());
    assert(_tensor_type.count_indexed_dimensions() == 1u);
    _dim_size = _tensor_type.dimensions()[0].size;
    cfg.setTensorType(_tensor_type);
    cfg.set_distance_metric(hnsw_index_params.distance_metric());
    cfg.set_hnsw_index_params(hnsw_index_params);
    _attribute = AttributeFactory::createAttribute("tensor", cfg);
    _tensor_attribute = dynamic_cast<TensorAttribute *>(_attribute.get());
    assert(_tensor_attribute != nullptr);
    _nearest_neighbor_index = _tensor_attribute->nearest_neighbor_index();
    assert(_nearest_neighbor_index != nullptr);
}

HnswIndex::~HnswIndex() = default;

bool
HnswIndex::check_lid(uint32_t lid)
{
    if (lid >= std::numeric_limits<uint32_t>::max() - lid_bias) {
        std::cerr << "lid is too high" << std::endl;
        return false;
    }
    return true;
}

bool
HnswIndex::check_value(const char *op, const std::vector<float>& value)
{
    if (value.size() != _dim_size) {
        std::cerr << op << " failed, expected vector with size " << _dim_size << ", got vector with size " << value.size() << std::endl;
        return false;
    }
    return true;
}

TypedCells
HnswIndex::get_typed_cells(const std::vector<float>& value, std::vector<float>& normalized_value)
{
    if (!_normalize_vectors) {
        return {&value[0], CellType::FLOAT, value.size()};
    }
    double sum_of_squared = 0.0;
    for (auto elem : value) {
        double delem = elem;
        sum_of_squared += delem * delem;
    }
    double factor = 1.0 / (sqrt(sum_of_squared) + 1e-40);
    normalized_value.reserve(value.size());
    normalized_value.clear();
    for (auto elem : value) {
        normalized_value.emplace_back(elem * factor);
    }
    return {&normalized_value[0], CellType::FLOAT, normalized_value.size()};
}

void
HnswIndex::set_vector(uint32_t lid, const std::vector<float>& value)
{
    if (!check_lid(lid)) {
        return;
    }
    if (!check_value("set_vector", value)) {
        return;
    }
    /*
     * Not thread safe against concurrent set_vector().
     */
    std::vector<float> normalized_value;
    auto typed_cells = get_typed_cells(value, normalized_value);
    DenseValueView tensor_view(_tensor_type, typed_cells);
    while (size_t(lid + lid_bias) >= _attribute->getNumDocs()) {
        uint32_t new_lid = 0;
        _attribute->addDoc(new_lid);
    }
    _tensor_attribute->setTensor(lid + lid_bias, tensor_view); // lid 0 is special in vespa
    _attribute->commit();
}

std::vector<float>
HnswIndex::get_vector(uint32_t lid)
{
    if (!check_lid(lid)) {
        return {};
    }
    TypedCells typed_cells = _tensor_attribute->extract_cells_ref(lid + lid_bias);
    assert(typed_cells.size == _dim_size);
    const float* data = static_cast<const float* >(typed_cells.data);
    return {data, data + _dim_size};
    return {};
}

void
HnswIndex::clear_vector(uint32_t lid)
{
    if (!check_lid(lid)) {
        return;
    }
    if (size_t(lid + lid_bias) < _attribute->getNumDocs()) {
        _attribute->clearDoc(lid + lid_bias);
        _attribute->commit();
    }
}

TopKResult
HnswIndex::find_top_k(uint32_t k, const std::vector<float>& value, uint32_t explore_k)
{
    if (!check_value("find_top_k", value)) {
        return {};
    }
    /*
     * Not thread safe against concurrent set_vector() since attribute
     * read guard is not taken here.
     */
    TopKResult result;
    std::vector<float> normalized_value;
    auto typed_cells = get_typed_cells(value, normalized_value);
    auto raw_result = _nearest_neighbor_index->find_top_k(k, typed_cells, explore_k, std::numeric_limits<double>::max());
    result.reserve(raw_result.size());
    switch (_hnsw_index_params.distance_metric()) {
    case DistanceMetric::Euclidean:
        for (auto &raw : raw_result) {
            result.emplace_back(raw.docid - lid_bias, sqrt(raw.distance));
        }
        break;
    default:
        for (auto &raw : raw_result) {
            result.emplace_back(raw.docid - lid_bias, raw.distance);
        }
    }
    // Results are sorted by lid, not by distance
    return result;
}

}

using vespa_ann_benchmark::HnswIndex;

PYBIND11_MODULE(vespa_ann_benchmark, m) {
    m.doc() = "vespa_ann_benchmark plugin";

    py::enum_<DistanceMetric>(m, "DistanceMetric")
        .value("Euclidean", DistanceMetric::Euclidean)
        .value("Angular", DistanceMetric::Angular)
        .value("InnerProduct", DistanceMetric::InnerProduct);

    py::class_<HnswIndexParams>(m, "HnswIndexParams")
        .def(py::init<uint32_t, uint32_t, DistanceMetric, bool>());

    py::class_<HnswIndex>(m, "HnswIndex")
        .def(py::init<uint32_t, const HnswIndexParams&, bool>())
        .def("set_vector", &HnswIndex::set_vector)
        .def("get_vector", &HnswIndex::get_vector)
        .def("clear_vector", &HnswIndex::clear_vector)
        .def("find_top_k", &HnswIndex::find_top_k);
}
