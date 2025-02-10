// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm25_utils.h"
#include "utils.h"
#include <vespa/searchlib/fef/document_frequency.h>
#include <vespa/searchlib/fef/properties.h>
#include <algorithm>
#include <cmath>
#include <stdexcept>

#include <vespa/log/log.h>
LOG_SETUP(".features.bm25_utils");

using search::fef::DocumentFrequency;
using search::fef::IQueryEnvironment;
using search::fef::ITermData;
using search::fef::ITermFieldData;
using vespalib::Trinary;

namespace search::features {

std::string Bm25Utils::_average_element_length("averageElementLength");
std::string Bm25Utils::_average_field_length("averageFieldLength");
std::string Bm25Utils::_b("b");
std::string Bm25Utils::_k1("k1");

Bm25Utils::Bm25Utils(const std::string& property_key_prefix, const fef::Properties& properties)
    : _property_key_prefix(property_key_prefix),
      _properties(properties)
{
}

Bm25Utils::~Bm25Utils() = default;

Trinary
Bm25Utils::lookup_param(const std::string& param, double& result) const
{
    std::string key = _property_key_prefix + param;
    auto value = _properties.lookup(key);
    if (value.found()) {
        try {
            result = std::stod(value.get());
            return Trinary::True;
        } catch (const std::invalid_argument& ex) {
            LOG(warning, "Not able to convert rank property '%s': '%s' to a double value",
                key.c_str(), value.get().c_str());
            return Trinary::Undefined;
        }
    }
    return Trinary::False;
}

Trinary
Bm25Utils::lookup_param(const std::string& param, std::optional<double>& result) const
{
    double tmp_result;
    auto lres = lookup_param(param, tmp_result);
    if (lres == Trinary::True) {
        result = tmp_result;
    }
    return lres;
}

double
Bm25Utils::calculate_inverse_document_frequency(DocumentFrequency doc_freq) noexcept
{
    double frequency = doc_freq.frequency;
    double count = doc_freq.count;
    count = std::max(1.0, count);
    frequency = std::min(std::max(1.0, frequency), count);
    return std::log(1 + ((count - frequency + 0.5) / (frequency + 0.5)));
}

double
Bm25Utils::get_inverse_document_frequency(const ITermFieldData& term_field, const IQueryEnvironment& env,
                                          const ITermData &term)
{
    auto doc_freq = util::lookup_document_frequency(env, term);
    if (doc_freq.has_value()) {
        return calculate_inverse_document_frequency(doc_freq.value());
    }
    double fallback = calculate_inverse_document_frequency(term_field.get_doc_freq());
    return util::lookupSignificance(env, term, fallback);
}

}
