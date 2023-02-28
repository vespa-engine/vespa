// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "closest_feature.h"
#include "constant_tensor_executor.h"
#include "distance_calculator_bundle.h"
#include "valuefeature.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchlib/fef/parameterdescriptions.h>
#include <vespa/searchlib/fef/test/dummy_dependency_handler.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/searchlib/tensor/fast_value_view.h>
#include <vespa/searchlib/tensor/i_tensor_attribute.h>
#include <vespa/searchlib/tensor/serialized_tensor_ref.h>
#include <vespa/searchlib/tensor/subspace_type.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP(".features.closest_feature");

using search::fef::FeatureType;
using search::fef::FieldInfo;
using search::fef::ParameterDataTypeSet;
using search::tensor::FastValueView;
using search::tensor::ITensorAttribute;
using search::tensor::SubspaceType;
using search::tensor::VectorBundle;
using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TypedCells;
using vespalib::eval::TypifyCellType;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::string_id;
using vespalib::typify_invoke;

using namespace search::fef::indexproperties;

namespace {

struct SetIdentity {
    template <typename T>
    static void invoke(void *space, size_t size) {
        assert(size == sizeof(T));
        *(T *) space = 1.0;
    }
};

void setup_identity_cells(const ValueType& type, std::vector<char>& space, TypedCells& cells)
{
    if (type.is_double()) {
        return;
    }
    space.resize(vespalib::eval::CellTypeUtils::mem_size(type.cell_type(), 1));
    cells = TypedCells(space.data(), type.cell_type(), 1);
    typify_invoke<1,TypifyCellType,SetIdentity>(type.cell_type(), space.data(), space.size());
}

}

namespace search::features {

class ClosestExecutor : public fef::FeatureExecutor {
protected:
    DistanceCalculatorBundle _bundle;
    Value&                   _empty_output;
    TypedCells               _identity;
    const ITensorAttribute&  _attr;
    std::unique_ptr<Value>   _output;
public:
    ClosestExecutor(DistanceCalculatorBundle&& bundle, Value& empty_output, TypedCells identity, const ITensorAttribute& attr);
    ~ClosestExecutor() override;
    static fef::FeatureExecutor& make(DistanceCalculatorBundle&& bundle, Value& empty_output, TypedCells identity, const ITensorAttribute& attr, vespalib::Stash& stash);
};

/**
 * Implements the executor for the closest feature for SerializedFastValueAttribute.
 */
class ClosestSerializedExecutor : public ClosestExecutor {
public:
    ClosestSerializedExecutor(DistanceCalculatorBundle&& bundle, Value& empty_output, TypedCells identity, const ITensorAttribute& attr);
    ~ClosestSerializedExecutor() override;
    void execute(uint32_t docId) override;
};

/**
 * Implements the executor for the closest feature for DirectTensorAttribute.
 */
class ClosestDirectExecutor : public ClosestExecutor {
    SubspaceType            _subspace_type;
    std::vector<string_id>  _labels;
    std::vector<string_id*> _label_ptrs;
public:
    ClosestDirectExecutor(DistanceCalculatorBundle&& bundle, Value& empty_output, TypedCells identity, const ITensorAttribute& attr);
    ~ClosestDirectExecutor() override;
    void execute(uint32_t docId) override;
};

ClosestExecutor::ClosestExecutor(DistanceCalculatorBundle&& bundle, Value& empty_output, TypedCells identity, const ITensorAttribute& attr)
    : _bundle(std::move(bundle)),
      _empty_output(empty_output),
      _identity(identity),
      _attr(attr),
      _output()
{
}

ClosestExecutor::~ClosestExecutor() = default;

fef::FeatureExecutor&
ClosestExecutor::make(DistanceCalculatorBundle&& bundle, Value& empty_output, TypedCells identity, const ITensorAttribute& attr, vespalib::Stash& stash)
{
    if (attr.supports_get_serialized_tensor_ref()) {
        return stash.create<ClosestSerializedExecutor>(std::move(bundle), empty_output, identity, attr);
    } else if (attr.supports_get_tensor_ref()) {
        return stash.create<ClosestDirectExecutor>(std::move(bundle), empty_output, identity, attr);
    } else {
        return ConstantTensorExecutor::createEmpty(empty_output.type(), stash);
    }
}

ClosestSerializedExecutor::ClosestSerializedExecutor(DistanceCalculatorBundle&& bundle, Value& empty_output, TypedCells identity, const ITensorAttribute& attr)
    : ClosestExecutor(std::move(bundle), empty_output, identity, attr)
{
}

ClosestSerializedExecutor::~ClosestSerializedExecutor() = default;

void
ClosestSerializedExecutor::execute(uint32_t docId)
{
    double best_distance = 0.0;
    std::optional<uint32_t> closest_subspace;
    auto ref = _attr.get_serialized_tensor_ref(docId);
    for (const auto& elem : _bundle.elements()) {
        elem.calc->calc_closest_subspace(ref.get_vectors(), closest_subspace, best_distance);
    }
    if (closest_subspace.has_value()) {
        auto labels = ref.get_labels(closest_subspace.value());
        _output = std::make_unique<FastValueView>(_empty_output.type(), labels, _identity, labels.size(), 1);
        outputs().set_object(0, *_output);
    } else {
        outputs().set_object(0, _empty_output);
    }
}

ClosestDirectExecutor::ClosestDirectExecutor(DistanceCalculatorBundle&& bundle, Value& empty_output, TypedCells identity, const ITensorAttribute& attr)
    : ClosestExecutor(std::move(bundle), empty_output, identity, attr),
      _subspace_type(attr.getTensorType()),
      _labels(1),
      _label_ptrs(_labels.size())
{
    for (size_t i = 0; i < _labels.size(); ++i) {
        _label_ptrs[i] = &_labels[i];
    }
}

ClosestDirectExecutor::~ClosestDirectExecutor() = default;

void
ClosestDirectExecutor::execute(uint32_t docId)
{
    double best_distance = 0.0;
    std::optional<uint32_t> closest_subspace;
    auto& tensor = _attr.get_tensor_ref(docId);
    VectorBundle vectors(tensor.cells().data, tensor.index().size(), _subspace_type);
    for (const auto& elem : _bundle.elements()) {
        elem.calc->calc_closest_subspace(vectors, closest_subspace, best_distance);
    }
    if (closest_subspace.has_value()) {
        size_t subspace_id = 0;
        auto view = tensor.index().create_view({});
        view->lookup({});
        while (view->next_result(_label_ptrs, subspace_id)) {
            if (subspace_id == closest_subspace.value()) {
                _output = std::make_unique<FastValueView>(_empty_output.type(), _labels, _identity, _labels.size(), 1);
                outputs().set_object(0, *_output);
                return;
            }
        }
    }
    outputs().set_object(0, _empty_output);
}

ClosestBlueprint::ClosestBlueprint()
    : Blueprint("closest"),
      _field_name(),
      _field_tensor_type(ValueType::error_type()),
      _output_tensor_type(ValueType::error_type()),
      _field_id(search::index::Schema::UNKNOWN_FIELD_ID),
      _item_label(),
      _empty_output(),
      _identity_space(),
      _identity_cells()
{
}

ClosestBlueprint::~ClosestBlueprint() = default;

void
ClosestBlueprint::visitDumpFeatures(const fef::IIndexEnvironment&, fef::IDumpFeatureVisitor&) const
{
}

std::unique_ptr<fef::Blueprint>
ClosestBlueprint::createInstance() const
{
    return std::make_unique<ClosestBlueprint>();
}

fef::ParameterDescriptions
ClosestBlueprint::getDescriptions() const
{
    auto data_type_set = ParameterDataTypeSet::tensor_type_set();
    return fef::ParameterDescriptions().
        desc().attribute(data_type_set, fef::ParameterCollection::SINGLE).
        desc().attribute(data_type_set, fef::ParameterCollection::SINGLE).string();
}

bool
ClosestBlueprint::setup(const fef::IIndexEnvironment & env, const fef::ParameterList & params)
{
    if (params.size() < 1 || params.size() > 2) {
        LOG(error, "%s: Wrong number of parameters, was %d, must be 1 or 2", getName().c_str(), (int) params.size());
        return false;
    }
    _field_name = params[0].getValue();
    if (params.size() == 2) {
        _item_label = params[1].getValue();
    }
    auto fi = env.getFieldByName(_field_name);
    assert(fi != nullptr);
    vespalib::string attr_type_spec = type::Attribute::lookup(env.getProperties(), _field_name);
    if (attr_type_spec.empty()) {
        LOG(error, "%s: Field %s lacks a type in index properties", getName().c_str(), _field_name.c_str());
        return false;
    }
    _field_tensor_type = ValueType::from_spec(attr_type_spec);
    if (_field_tensor_type.is_error() || _field_tensor_type.is_double() || _field_tensor_type.count_mapped_dimensions() != 1 || _field_tensor_type.count_indexed_dimensions() != 1) {
        LOG(error, "%s: Field %s has invalid type: '%s'", getName().c_str(), _field_name.c_str(), attr_type_spec.c_str());
        return false;
    }
    _output_tensor_type = ValueType::make_type(_field_tensor_type.cell_type(), _field_tensor_type.mapped_dimensions());
    assert(!_output_tensor_type.is_double());
    FeatureType output_type = FeatureType::object(_output_tensor_type);
    describeOutput("out", "The closest tensor subspace.", output_type);
    _field_id = fi->id();
    _empty_output = vespalib::eval::value_from_spec(_output_tensor_type.to_spec(), FastValueBuilderFactory::get());
    setup_identity_cells(_output_tensor_type, _identity_space, _identity_cells);
    return true;
}

void
ClosestBlueprint::prepareSharedState(const fef::IQueryEnvironment& env, fef::IObjectStore& store) const
{
    if (_item_label.has_value()) {
        DistanceCalculatorBundle::prepare_shared_state(env, store, _item_label.value(), "closest");
    } else {
        DistanceCalculatorBundle::prepare_shared_state(env, store, _field_id, "closest");
    }
}

fef::FeatureExecutor&
ClosestBlueprint::createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const
{
    auto bundle = _item_label.has_value() ? DistanceCalculatorBundle(env, _field_id, _item_label.value(), "closest") : DistanceCalculatorBundle(env, _field_id, "closest");
    if (bundle.elements().empty()) {
        return ConstantTensorExecutor::createEmpty(_output_tensor_type, stash);
    } else {
        for (const auto& elem : bundle.elements()) {
            if (!elem.calc) {
                return ConstantTensorExecutor::createEmpty(_output_tensor_type, stash);
            }
        }
        auto& attr = bundle.elements().front().calc->attribute_tensor();
        return ClosestExecutor::make(std::move(bundle), *_empty_output, _identity_cells, attr, stash);
    }
}

}
