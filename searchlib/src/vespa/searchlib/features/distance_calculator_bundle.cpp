// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_calculator_bundle.h"
#include "utils.h"
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/query_value.h>
#include <vespa/searchlib/tensor/distance_calculator.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>

using search::fef::ITermData;
using search::fef::IllegalHandle;
using search::fef::InvalidValueTypeException;
using search::fef::QueryValue;
using search::fef::TermFieldHandle;
using search::tensor::DistanceCalculator;
using vespalib::Issue;

namespace search::features {

namespace {

void
prepare_query_tensor(const fef::IQueryEnvironment& env,
                     fef::IObjectStore& store,
                     const vespalib::string& query_tensor_name,
                     const vespalib::string& feature_name)
{
    try {
        auto qvalue = QueryValue::from_config(query_tensor_name, env.getIndexEnvironment());
        qvalue.prepare_shared_state(env, store);
    } catch (const InvalidValueTypeException& ex) {
        Issue::report("%s feature: Query tensor '%s' has invalid type '%s'.",
                      feature_name.c_str(), query_tensor_name.c_str(), ex.type_str().c_str());
    }
}

std::unique_ptr<DistanceCalculator>
make_distance_calculator(const fef::IQueryEnvironment& env,
                         const search::attribute::IAttributeVector& attr,
                         const vespalib::string& query_tensor_name,
                         const vespalib::string& feature_name)
{
    try {
        auto qvalue = QueryValue::from_config(query_tensor_name, env.getIndexEnvironment());
        const auto* query_tensor = qvalue.lookup_value(env.getObjectStore());
        if (query_tensor == nullptr) {
            Issue::report("%s feature: Query tensor '%s' is not found in the object store.",
                          feature_name.c_str(), query_tensor_name.c_str());
            return {};
        }
        return DistanceCalculator::make_with_validation(attr, *query_tensor);
    } catch (const InvalidValueTypeException& ex) {
        Issue::report("%s feature: Query tensor '%s' has invalid type '%s'.",
                      feature_name.c_str(), query_tensor_name.c_str(), ex.type_str().c_str());
    } catch (const vespalib::IllegalArgumentException& ex) {
        Issue::report("%s feature: Could not create distance calculator for attribute '%s' and query tensor '%s': %s.",
                      feature_name.c_str(), attr.getName().c_str(), query_tensor_name.c_str(), ex.getMessage().c_str());
    }
    return {};
}

const search::attribute::IAttributeVector*
resolve_attribute_for_field(const fef::IQueryEnvironment& env,
                            uint32_t field_id,
                            const vespalib::string& feature_name)
{
    const auto* field = env.getIndexEnvironment().getField(field_id);
    if (field != nullptr) {
        const auto* attr = env.getAttributeContext().getAttribute(field->name());
        if (attr == nullptr) {
            Issue::report("%s feature: The attribute vector '%s' for field id '%u' doesn't exist.",
                          feature_name.c_str(), field->name().c_str(), field_id);
        }
        return attr;
    }
    return nullptr;
}

}

DistanceCalculatorBundle::Element::Element(fef::TermFieldHandle handle_in) noexcept
    : handle(handle_in),
      calc()
{
}

DistanceCalculatorBundle::Element::Element(fef::TermFieldHandle handle_in, std::unique_ptr<search::tensor::DistanceCalculator> calc_in) noexcept
    : handle(handle_in),
      calc(std::move(calc_in))
{
}

DistanceCalculatorBundle::Element::~Element() = default;

DistanceCalculatorBundle::DistanceCalculatorBundle(const fef::IQueryEnvironment& env,
                                                   uint32_t field_id,
                                                   const vespalib::string& feature_name)

    : _elems()
{
    _elems.reserve(env.getNumTerms());
    const auto* attr = resolve_attribute_for_field(env, field_id, feature_name);
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        search::fef::TermFieldHandle handle = util::getTermFieldHandle(env, i, field_id);
        if (handle != search::fef::IllegalHandle) {
            const auto* term = env.getTerm(i);
            if (term->query_tensor_name().has_value() && (attr != nullptr)) {
                _elems.emplace_back(handle, make_distance_calculator(env, *attr, term->query_tensor_name().value(), feature_name));
            } else {
                _elems.emplace_back(handle);
            }
        }
    }
}

DistanceCalculatorBundle::DistanceCalculatorBundle(const fef::IQueryEnvironment& env,
                                                   std::optional<uint32_t> field_id,
                                                   const vespalib::string& label,
                                                   const vespalib::string& feature_name)
    : _elems()
{
    const ITermData* term = util::getTermByLabel(env, label);
    if (term != nullptr) {
        // expect numFields() == 1
        for (uint32_t i = 0; i < term->numFields(); ++i) {
            const auto& term_field = term->field(i);
            if (field_id.has_value() && field_id.value() != term_field.getFieldId()) {
                continue;
            }
            TermFieldHandle handle = term_field.getHandle();
            if (handle != IllegalHandle) {
                std::unique_ptr<DistanceCalculator> calc;
                if (term->query_tensor_name().has_value()) {
                    const auto* attr = resolve_attribute_for_field(env, term_field.getFieldId(), feature_name);
                    if (attr != nullptr) {
                        calc = make_distance_calculator(env, *attr, term->query_tensor_name().value(), feature_name);
                    }
                }
                _elems.emplace_back(handle, std::move(calc));
            }
        }
    }
}

void
DistanceCalculatorBundle::prepare_shared_state(const fef::IQueryEnvironment& env,
                                               fef::IObjectStore& store,
                                               uint32_t field_id,
                                               const vespalib::string& feature_name)
{
    for (uint32_t i = 0; i < env.getNumTerms(); ++i) {
        search::fef::TermFieldHandle handle = util::getTermFieldHandle(env, i, field_id);
        if (handle != search::fef::IllegalHandle) {
            const auto* term = env.getTerm(i);
            if (term->query_tensor_name().has_value()) {
                prepare_query_tensor(env, store, term->query_tensor_name().value(), feature_name);
            }
        }
    }
}

void
DistanceCalculatorBundle::prepare_shared_state(const fef::IQueryEnvironment& env,
                                               fef::IObjectStore& store,
                                               const vespalib::string& label,
                                               const vespalib::string& feature_name)
{
    const auto* term = util::getTermByLabel(env, label);
    if ((term != nullptr) && term->query_tensor_name().has_value()) {
        prepare_query_tensor(env, store, term->query_tensor_name().value(), feature_name);
    }
}

}

