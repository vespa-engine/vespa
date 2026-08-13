// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "utils.hpp"

#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/searchlib/fef/featurenameparser.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <algorithm>
#include <cassert>
#include <charconv>
#include <cmath>
#include <ostream>

#include <vespa/log/log.h>
LOG_SETUP(".features.utils");

using vespalib::Issue;
using namespace search::fef;

namespace search::features::util {

template <typename T> T strToInt(std::string_view str) {
    T retval = 0;
    if ((str.size() > 2) && (str[0] == '0') && ((str[1] | 0x20) == 'x')) {
        std::from_chars(str.data() + 2, str.data() + str.size(), retval, 16);
    } else {
        std::from_chars(str.data(), str.data() + str.size(), retval, 10);
    }

    return retval;
}

template <> uint8_t strToNum<uint8_t>(std::string_view str) {
    return strToInt<uint16_t>(str);
}

template <> int8_t strToNum<int8_t>(std::string_view str) {
    return strToInt<int16_t>(str);
}

template double strToNum<double>(std::string_view str);
template float strToNum<float>(std::string_view str);

template <> uint16_t strToNum<uint16_t>(std::string_view str) {
    return strToInt<uint16_t>(str);
}
template <> uint32_t strToNum<uint32_t>(std::string_view str) {
    return strToInt<uint32_t>(str);
}
template <> uint64_t strToNum<uint64_t>(std::string_view str) {
    return strToInt<uint64_t>(str);
}
template <> int16_t strToNum<int16_t>(std::string_view str) {
    return strToInt<int16_t>(str);
}
template <> int32_t strToNum<int32_t>(std::string_view str) {
    return strToInt<int32_t>(str);
}
template <> int64_t strToNum<int64_t>(std::string_view str) {
    return strToInt<int64_t>(str);
}

feature_t lookupConnectedness(const search::fef::IQueryEnvironment& env, uint32_t termId, feature_t fallback) {
    if (termId == 0) {
        return fallback; // no previous term
    }

    const ITermData* data = env.getTerm(termId);
    const ITermData* prev = env.getTerm(termId - 1);
    if (data == nullptr || prev == nullptr) {
        return fallback; // default value
    }
    return lookupConnectedness(env, data->getUniqueId(), prev->getUniqueId(), fallback);
}

feature_t lookupConnectedness(const search::fef::IQueryEnvironment& env, uint32_t currUniqueId, uint32_t prevUniqueId,
                              feature_t fallback) {
    // Connectedness of 0.5 between term with unique id 2 and term with unique id 1 is represented as:
    // [vespa.term.2.connexity: "1", vespa.term.2.connexity: "0.5"]
    vespalib::asciistream os;
    os << "vespa.term." << currUniqueId << ".connexity";
    Property p = env.getProperties().lookup(os.view());
    if (p.size() == 2) {
        // we have a defined connectedness with the previous term
        if (strToNum<uint32_t>(p.getAt(0)) == prevUniqueId) {
            return strToNum<feature_t>(p.getAt(1));
        }
    }
    return fallback;
}

feature_t lookupSignificance(const search::fef::IQueryEnvironment& env, const ITermData& term, feature_t fallback) {
    // Significance of 0.5 for term with unique id 1 is represented as:
    // [vespa.term.1.significance: "0.5"]
    vespalib::asciistream os;
    os << "vespa.term." << term.getUniqueId() << ".significance";
    Property p = env.getProperties().lookup(os.view());
    if (p.found()) {
        return strToNum<feature_t>(p.get());
    }
    return fallback;
}

static const double N = 1000000.0;

feature_t calculate_legacy_significance(DocumentFrequency doc_freq) {
    if (doc_freq.count == 0) {
        return 0.5; // Corner case, no documents
    }
    double frequency = doc_freq.frequency;
    double count = doc_freq.count;
    // Rescale frequency and count to corpus of N documents.
    frequency = std::min(std::max(1.0, frequency * N / count), N);
    count = N;
    double logcount = std::log(count);
    double logfrequency = std::log(frequency);
    // Using traditional formula for inverse document frequency, see
    // https://en.wikipedia.org/wiki/Tf%E2%80%93idf#Inverse_document_frequency
    double idf = logcount - logfrequency;
    // We normalize against document frequency 1 in corpus of N documents.
    double normalized_idf = idf / logcount;               // normalized to range [0;1]
    double renormalized_idf = 0.5 + 0.5 * normalized_idf; // normalized to range [0.5;1]
    return renormalized_idf;
}

DocumentFrequency aggregate_max(DocumentFrequency lhs, DocumentFrequency rhs) {
    return {std::max(lhs.frequency, rhs.frequency), std::max(lhs.count, rhs.count)};
}

feature_t calculate_legacy_significance(const ITermData& termData) {
    using FRA = search::fef::ITermFieldRangeAdapter;
    DocumentFrequency df(0, 0);
    for (FRA iter(termData); iter.valid(); iter.next()) {
        df = aggregate_max(df, iter.get().get_doc_freq());
    }

    feature_t signif = calculate_legacy_significance(df);
    LOG(debug, "calculate_legacy_significance %" PRIu64 " %" PRIu64 " = %e", df.frequency, df.count, signif);
    return signif;
}

const search::fef::Table* lookupTable(const search::fef::IIndexEnvironment& env, const std::string& featureName,
                                      const std::string& table, const std::string& fieldName,
                                      const std::string& fallback) {
    auto                      alt_name = FeatureNameBuilder().baseName(featureName).parameter(fieldName).buildName();
    std::string               tn1 = env.getProperties().lookup(featureName, table).get(fallback);
    std::string               tn2 = env.getProperties().lookup(alt_name, table).get(tn1);
    std::string               tn3 = env.getProperties().lookup(featureName, table, fieldName).get(tn2);
    const search::fef::Table* retval = env.getTableManager().getTable(tn3);
    if (retval == nullptr) {
        LOG(warning, "Could not find the %s '%s' to be used for field '%s' in feature '%s'", table.c_str(),
            tn3.c_str(), fieldName.c_str(), featureName.c_str());
    }
    return retval;
}

const ITermData* getTermByLabel(const search::fef::IQueryEnvironment& env, const std::string& label) {
    // Labeling the query item with unique id '5' with the label 'foo'
    // is represented as: [vespa.label.foo.id: "5"]
    vespalib::asciistream os;
    os << "vespa.label." << label << ".id";
    Property p = env.getProperties().lookup(os.view());
    if (!p.found()) {
        return nullptr;
    }
    uint32_t uid = strToNum<uint32_t>(p.get());
    if (uid == 0) {
        Issue::report("Query label '%s' was attached to invalid unique id: '%s'", label.c_str(), p.get().c_str());
        return nullptr;
    }
    for (uint32_t i(0), m(env.getNumTerms()); i < m; ++i) {
        const ITermData* term = env.getTerm(i);
        if (term->getUniqueId() == uid) {
            return term;
        }
    }
    Issue::report("Query label '%s' was attached to non-existing unique id: '%s'", label.c_str(), p.get().c_str());
    return nullptr;
}

namespace {

// The unique ids a label property names, deduplicated. Invalid ids are reported and skipped.
vespalib::hash_set<uint32_t> parse_label_uids(const std::string& label, const Property& p) {
    vespalib::hash_set<uint32_t> uids;
    for (uint32_t i(0), m(p.size()); i < m; ++i) {
        uint32_t uid = strToNum<uint32_t>(p.getAt(i));
        if (uid == 0) {
            Issue::report("Query label '%s' was attached to invalid unique id: '%s'", label.c_str(),
                          p.getAt(i).c_str());
        } else {
            uids.insert(uid);
        }
    }
    return uids;
}

void report_missing_uids(const std::string& label, const vespalib::hash_set<uint32_t>& missing_uids) {
    for (uint32_t uid : missing_uids) {
        Issue::report("Query label '%s' was attached to non-existing unique id: '%u'", label.c_str(), uid);
    }
}

// Collects the 'vespa.label.<label>.id' properties. Keys are visited with the namespace stripped.
struct LabelIdVisitor : IPropertiesVisitor {
    std::vector<std::pair<std::string, Property>> labels;
    void visitProperty(const Property::Value& key, const Property& values) override {
        std::string_view suffix(".id");
        if (!key.ends_with(suffix)) {
            return;
        }
        labels.emplace_back(key.substr(0, key.size() - suffix.size()), values);
    }
};

} // namespace

std::vector<const ITermData*> getTermsByLabel(const search::fef::IQueryEnvironment& env, const std::string& label) {
    // Labeling the query items with unique ids '5' and '7' with the label 'foo'
    // is represented as: [vespa.label.foo.id: "5", vespa.label.foo.id: "7"]
    vespalib::asciistream os;
    os << "vespa.label." << label << ".id";
    Property p = env.getProperties().lookup(os.view());
    std::vector<const ITermData*> terms;
    if (!p.found()) {
        return terms;
    }
    vespalib::hash_set<uint32_t> uids = parse_label_uids(label, p);
    vespalib::hash_set<uint32_t> missing_uids(uids);
    for (uint32_t i(0), m(env.getNumTerms()); i < m; ++i) {
        const ITermData* term = env.getTerm(i);
        if (uids.contains(term->getUniqueId())) {
            terms.push_back(term);
            missing_uids.erase(term->getUniqueId());
        }
    }
    report_missing_uids(label, missing_uids);
    return terms;
}

std::vector<std::pair<std::string, std::vector<const ITermData*>>>
getTermsByAllLabels(const search::fef::IQueryEnvironment& env) {
    LabelIdVisitor visitor;
    env.getProperties().visitNamespace("vespa.label", visitor);
    // Properties are visited in hash map order; sort to make the result (and the reported issues) reproducible.
    std::sort(visitor.labels.begin(), visitor.labels.end(),
              [](const auto& lhs, const auto& rhs) { return lhs.first < rhs.first; });

    std::vector<std::pair<std::string, std::vector<const ITermData*>>> result;
    result.reserve(visitor.labels.size());
    // A unique id may be claimed by several labels, so map it to all the labels claiming it.
    vespalib::hash_map<uint32_t, std::vector<uint32_t>> label_indexes_by_uid;
    std::vector<vespalib::hash_set<uint32_t>>           missing_uids;
    for (const auto& [label, p] : visitor.labels) {
        vespalib::hash_set<uint32_t> uids = parse_label_uids(label, p);
        if (uids.empty()) {
            continue;
        }
        uint32_t label_index = result.size();
        result.emplace_back(label, std::vector<const ITermData*>());
        for (uint32_t uid : uids) {
            label_indexes_by_uid[uid].push_back(label_index);
        }
        missing_uids.push_back(std::move(uids));
    }
    for (uint32_t i(0), m(env.getNumTerms()); i < m; ++i) {
        const ITermData* term = env.getTerm(i);
        auto             itr = label_indexes_by_uid.find(term->getUniqueId());
        if (itr != label_indexes_by_uid.end()) {
            for (uint32_t label_index : itr->second) {
                result[label_index].second.push_back(term);
                missing_uids[label_index].erase(term->getUniqueId());
            }
        }
    }
    for (uint32_t i(0), m(result.size()); i < m; ++i) {
        report_missing_uids(result[i].first, missing_uids[i]);
    }
    std::erase_if(result, [](const auto& entry) noexcept { return entry.second.empty(); });
    return result;
}


std::optional<DocumentFrequency> lookup_document_frequency(const search::fef::IQueryEnvironment& env,
                                                           const ITermData&                      term) {
    vespalib::asciistream os;
    auto                  unique_id = term.getUniqueId();
    if (unique_id != 0) {
        os << "vespa.term." << unique_id << ".docfreq";
        Property p = env.getProperties().lookup(os.view());
        if (p.size() == 2) {
            // we have a defined document frequency
            auto document_frequency = strToNum<uint64_t>(p.getAt(0));
            auto document_count = strToNum<uint64_t>(p.getAt(1));
            return DocumentFrequency(document_frequency, document_count);
        }
    }
    return {};
}

feature_t get_legacy_significance(const IQueryEnvironment& env, const ITermData& term) {
    auto docfreq = lookup_document_frequency(env, term);
    if (docfreq.has_value()) {
        return calculate_legacy_significance(docfreq.value());
    }
    feature_t fallback = calculate_legacy_significance(term);
    return lookupSignificance(env, term, fallback);
}

} // namespace search::features::util
