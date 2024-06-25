// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "utils.hpp"
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/issue.h>
#include <charconv>
#include <cmath>
#include <ostream>

#include <vespa/log/log.h>
LOG_SETUP(".features.utils");

using vespalib::Issue;
using namespace search::fef;

namespace search::features::util {

template <typename T>
T strToInt(vespalib::stringref str)
{
    T retval = 0;
    if ((str.size() > 2) && (str[0] == '0') && ((str[1] | 0x20) == 'x')) {
        std::from_chars(str.data()+2, str.data()+str.size(), retval, 16);
    } else {
        std::from_chars(str.data(), str.data()+str.size(), retval, 10);
    }

    return retval;
}

template <>
uint8_t
strToNum<uint8_t>(vespalib::stringref str) {
    return strToInt<uint16_t>(str);
}

template <>
int8_t
strToNum<int8_t>(vespalib::stringref str) {
    return strToInt<int16_t>(str);
}

template double   strToNum<double>(vespalib::stringref str);
template float    strToNum<float>(vespalib::stringref str);

template <> uint16_t strToNum<uint16_t>(vespalib::stringref str) { return strToInt<uint16_t>(str); }
template <> uint32_t strToNum<uint32_t>(vespalib::stringref str) { return strToInt<uint32_t>(str); }
template <> uint64_t strToNum<uint64_t>(vespalib::stringref str) { return strToInt<uint64_t>(str); }
template <> int16_t  strToNum<int16_t>(vespalib::stringref str) { return strToInt<int16_t>(str); }
template <> int32_t  strToNum<int32_t>(vespalib::stringref str) { return strToInt<int32_t>(str); }
template <> int64_t  strToNum<int64_t>(vespalib::stringref str) { return strToInt<int64_t>(str); }

feature_t
lookupConnectedness(const search::fef::IQueryEnvironment& env, uint32_t termId, feature_t fallback)
{
    if (termId == 0) {
        return fallback; // no previous term
    }

    const ITermData * data = env.getTerm(termId);
    const ITermData * prev = env.getTerm(termId - 1);
    if (data == nullptr || prev == nullptr) {
        return fallback; // default value
    }
    return lookupConnectedness(env, data->getUniqueId(), prev->getUniqueId(), fallback);
}

feature_t
lookupConnectedness(const search::fef::IQueryEnvironment& env,
                    uint32_t currUniqueId, uint32_t prevUniqueId, feature_t fallback)
{
    // Connectedness of 0.5 between term with unique id 2 and term with unique id 1 is represented as:
    // [vespa.term.2.connexity: "1", vespa.term.2.connexity: "0.5"]
    vespalib::asciistream os;
    os << "vespa.term." << currUniqueId << ".connexity";
    Property p = env.getProperties().lookup(os.str());
    if (p.size() == 2) {
        // we have a defined connectedness with the previous term
        if (strToNum<uint32_t>(p.getAt(0)) == prevUniqueId) {
            return strToNum<feature_t>(p.getAt(1));
        }
    }
    return fallback;
}

feature_t
lookupSignificance(const search::fef::IQueryEnvironment& env, const ITermData& term, feature_t fallback)
{
    // Significance of 0.5 for term with unique id 1 is represented as:
    // [vespa.term.1.significance: "0.5"]
    vespalib::asciistream os;
    os << "vespa.term." << term.getUniqueId() << ".significance";
    Property p = env.getProperties().lookup(os.str());
    if (p.found()) {
        return strToNum<feature_t>(p.get());
    }
    return fallback;
}

feature_t
lookupSignificance(const search::fef::IQueryEnvironment& env, uint32_t termId, feature_t fallback)
{
    const ITermData* term = env.getTerm(termId);
    if (term == nullptr) {
        return fallback;
    }
    return lookupSignificance(env, *term, fallback);
}

static const double N = 1000000.0;

feature_t
calculate_legacy_significance(double docFreq)
{
    if (docFreq < (1.0/N)) {
      docFreq = 1.0/N;
    }
    if (docFreq > 1.0) {
      docFreq = 1.0;
    }
    double d = std::log(docFreq)/std::log(1.0/N);
    return 0.5 + 0.5 * d;
}

feature_t
calculate_legacy_significance(const search::fef::ITermData& termData)
{
    using FRA = search::fef::ITermFieldRangeAdapter;
    double df = 0;
    for (FRA iter(termData); iter.valid(); iter.next()) {
        df = std::max(df, iter.get().getDocFreq());
    }

    feature_t signif = calculate_legacy_significance(df);
    LOG(debug, "calculate_legacy_significance %e %f [ %e %f ] = %e", df, df, df * N, df * N, signif);
    return signif;
}

const search::fef::Table *
lookupTable(const search::fef::IIndexEnvironment & env, const vespalib::string & featureName,
            const vespalib::string & table, const vespalib::string & fieldName, const vespalib::string & fallback)
{
    vespalib::string tn1 = env.getProperties().lookup(featureName, table).get(fallback);
    vespalib::string tn2 = env.getProperties().lookup(featureName, table, fieldName).get(tn1);
    const search::fef::Table * retval = env.getTableManager().getTable(tn2);
    if (retval == nullptr) {
        LOG(warning, "Could not find the %s '%s' to be used for field '%s' in feature '%s'",
            table.c_str(), tn2.c_str(), fieldName.c_str(), featureName.c_str());
    }
    return retval;
}

const search::fef::ITermData *
getTermByLabel(const search::fef::IQueryEnvironment &env, const vespalib::string &label)
{
    // Labeling the query item with unique id '5' with the label 'foo'
    // is represented as: [vespa.label.foo.id: "5"]
    vespalib::asciistream os;
    os << "vespa.label." << label << ".id";
    Property p = env.getProperties().lookup(os.str());
    if (!p.found()) {
        return 0;
    }
    uint32_t uid = strToNum<uint32_t>(p.get());
    if (uid == 0) {
        Issue::report("Query label '%s' was attached to invalid unique id: '%s'",
                      label.c_str(), p.get().c_str());
        return 0;
    }
    for (uint32_t i(0), m(env.getNumTerms()); i < m; ++i) {
        const ITermData *term = env.getTerm(i);
        if (term->getUniqueId() == uid) {
            return term;
        }
    }
    Issue::report("Query label '%s' was attached to non-existing unique id: '%s'",
                  label.c_str(), p.get().c_str());
    return 0;
}

std::optional<DocumentFrequency>
lookup_document_frequency(const search::fef::IQueryEnvironment& env, const ITermData& term)
{
    vespalib::asciistream os;
    auto unique_id = term.getUniqueId();
    if (unique_id != 0) {
        os << "vespa.term." << unique_id << ".docfreq";
        Property p = env.getProperties().lookup(os.str());
        if (p.size() == 2) {
            // we have a defined document frequency
            auto document_frequency = strToNum<uint64_t>(p.getAt(0));
            auto document_count = strToNum<uint64_t>(p.getAt(1));
            return DocumentFrequency(document_frequency, document_count);
        }
    }
    return {};
}

std::optional<DocumentFrequency>
lookup_document_frequency(const search::fef::IQueryEnvironment& env, uint32_t termId)
{
    const ITermData* term = env.getTerm(termId);
    if (term == nullptr) {
        return {};
    }
    return lookup_document_frequency(env, *term);
}

}
