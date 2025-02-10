// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/handle.h>
#include <vespa/vespalib/util/trinary.h>
#include <optional>
#include <string>

namespace search::fef {

struct DocumentFrequency;
class IQueryEnvironment;
class ITermData;
class ITermFieldData;
class Properties;
class TermFieldMatchData;

}

namespace search::features {

/*
 * Class containing shared code between bm25 ranking feature and elementwise bm25 ranking feature.
 */
class Bm25Utils {
    std::string _property_key_prefix;
    const fef::Properties& _properties;
    static std::string _average_element_length;
    static std::string _average_field_length;
    static std::string _b;
    static std::string _k1;
public:
    struct QueryTerm {
        fef::TermFieldHandle handle;
        const fef::TermFieldMatchData* tfmd;
        double idf_mul_k1_plus_one;
        double degraded_score;
        QueryTerm(fef::TermFieldHandle handle_, double inverse_doc_freq, double k1_param) noexcept
            : handle(handle_),
              tfmd(nullptr),
              idf_mul_k1_plus_one(inverse_doc_freq * (k1_param + 1)),
              degraded_score(inverse_doc_freq)
        {}
    };

    Bm25Utils(const std::string& property_key_prefix, const fef::Properties& properties);
    ~Bm25Utils();
    vespalib::Trinary lookup_param(const std::string& param, double& result) const;
    vespalib::Trinary lookup_param(const std::string& param, std::optional<double>& result) const;
    static double calculate_inverse_document_frequency(search::fef::DocumentFrequency doc_freq) noexcept;
    static double get_inverse_document_frequency(const fef::ITermFieldData &term_field,
                                                 const fef::IQueryEnvironment &env,
                                                 const fef::ITermData &term);
    static const std::string& average_element_length() noexcept { return _average_element_length; }
    static const std::string& average_field_length() noexcept { return _average_field_length; }
    static const std::string& b() noexcept { return _b; }
    static const std::string& k1() noexcept { return _k1; }
};

}
