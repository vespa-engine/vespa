// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/trinary.h>
#include <optional>
#include <string>

namespace search::fef {

struct DocumentFrequency;
class IQueryEnvironment;
class ITermData;
class ITermFieldData;
class Properties;

}

namespace search::features {

/*
 * Class containing shared code between bm25 feature and bm25PerElement feature.
 */
class Bm25Utils {
    std::string _property_key_prefix;
    const fef::Properties& _properties;
    static std::string _average_field_length;
    static std::string _b;
    static std::string _k1;
public:
     Bm25Utils(const std::string& base_name, const std::string& field_name, const fef::Properties& properties);
     ~Bm25Utils();
    vespalib::Trinary lookup_param(const std::string& param, double& result) const;
    vespalib::Trinary lookup_param(const std::string& param, std::optional<double>& result) const;
    static double calculate_inverse_document_frequency(search::fef::DocumentFrequency doc_freq) noexcept;
    static double get_inverse_document_frequency(const fef::ITermFieldData &term_field,
                                                 const fef::IQueryEnvironment &env,
                                                 const fef::ITermData &term);
    static const std::string& average_field_length() noexcept { return _average_field_length; }
    static const std::string& b() noexcept { return _b; }
    static const std::string& k1() noexcept { return _k1; }
};

}
