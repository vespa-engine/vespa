// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlated_bindings_feature.h"
#include "constant_tensor_executor.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/searchlib/fef/feature_type.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/queryeval/correlated_bindings.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stash.h>
#include <set>

namespace search::features {

using queryeval::CorrelatedBindingFactory;
using queryeval::CorrelatedBindingReader;
using vespalib::eval::ValueType;
using vespalib::eval::FastValueBuilderFactory;

namespace {

class CorrelatedBindingsExecutor : public fef::FeatureExecutor {
    std::unique_ptr<CorrelatedBindingReader> _reader;
    std::vector<uint32_t> _columns;
    ValueType _type;
    std::unique_ptr<vespalib::eval::Value> _output;

public:
    CorrelatedBindingsExecutor(std::unique_ptr<CorrelatedBindingReader> reader, std::vector<uint32_t> columns,
                               ValueType type)
        : _reader(std::move(reader)), _columns(std::move(columns)), _type(std::move(type)), _output() {}

    void execute(uint32_t docid) override {
        std::set<std::vector<std::string>> addresses;
        for (const auto& row : _reader->read(docid, _columns)) {
            std::vector<std::string> address;
            address.reserve(_columns.size());
            for (const auto& value : row) {
                address.push_back(std::holds_alternative<int64_t>(value) ? std::to_string(std::get<int64_t>(value))
                                                                       : std::string(std::get<std::string_view>(value)));
            }
            addresses.insert(std::move(address));
        }
        auto builder = FastValueBuilderFactory::get().create_value_builder<double>(_type, _columns.size(), 1, addresses.size());
        std::vector<std::string_view> labels;
        labels.reserve(_columns.size());
        for (const auto& address : addresses) {
            labels.assign(address.begin(), address.end());
            builder->add_subspace(labels)[0] = 1.0;
        }
        _output = builder->build(std::move(builder));
        outputs().set_object(0, *_output);
    }
};

}

CorrelatedBindingsBlueprint::CorrelatedBindingsBlueprint()
    : Blueprint("correlatedBindings"), _label(), _value_type(ValueType::error_type()) {}
CorrelatedBindingsBlueprint::~CorrelatedBindingsBlueprint() = default;

bool CorrelatedBindingsBlueprint::setup(const fef::IIndexEnvironment&, const fef::ParameterList& params) {
    _label = params[0].getValue();
    if (_label.empty()) return fail("correlatedBindings requires a query label");
    std::set<std::string> variables;
    std::vector<ValueType::Dimension> dimensions;
    for (size_t i = 1; i < params.size(); ++i) {
        const auto& variable = params[i].getValue();
        if (variable.empty() || !variables.insert(variable).second)
            return fail("correlatedBindings requires distinct nonempty variables");
        dimensions.emplace_back(variable);
    }
    _value_type = ValueType::from_spec(ValueType::make_type(vespalib::eval::CellType::DOUBLE, dimensions).to_spec());
    if (_value_type.is_error()) return fail("correlatedBindings variables must be valid dimension names");
    describeOutput("tensor", "One cell with value 1 for each distinct tuple of matched correlation bindings",
                   fef::FeatureType::object(_value_type));
    return true;
}

fef::FeatureExecutor& CorrelatedBindingsBlueprint::createExecutor(const fef::IQueryEnvironment& env,
                                                                 vespalib::Stash& stash) const {
    const auto* factory = dynamic_cast<const CorrelatedBindingFactory*>(env.getObjectStore().get(
            CorrelatedBindingFactory::store_key(_label)));
    // Rank profiles can also serve queries without this labeled correlation.
    if (!factory) return ConstantTensorExecutor::createEmpty(_value_type, stash);
    std::vector<uint32_t> columns;
    for (const auto& dimension : _value_type.dimensions()) {
        auto column = factory->columns().find(dimension.name);
        if (column == factory->columns().end()) {
            vespalib::Issue::report("correlatedBindings refers to an unbound variable '%s' in query '%s'",
                                    dimension.name.c_str(), _label.c_str());
            return ConstantTensorExecutor::createEmpty(_value_type, stash);
        }
        columns.push_back(column->second);
    }
    return stash.create<CorrelatedBindingsExecutor>(factory->create(), std::move(columns), _value_type);
}

} // namespace search::features
