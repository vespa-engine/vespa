// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "utf8stringfieldsearcherbase.h"

#include <map>
#include <utility>
#include <vector>

namespace document {
class StringFieldValue;
}

namespace vsm {

/**
 * Normalized tokens of annotated string values of one document, kept while many queries are matched
 * against that document (reverse matching), so span trees are deserialized and tokens normalized once.
 * Values are keyed by address: the document must outlive the cache. Not thread safe.
 **/
class AnnotatedTokenCache {
public:
    struct Tokens {
        std::vector<ucs4_t>   chars;     // all tokens, back to back
        std::vector<uint32_t> ends;      // end of each token in chars
        std::vector<uint32_t> positions; // word position of each token
        uint32_t              length = 0;
    };
    const Tokens& get(const document::StringFieldValue& value, search::Normalizing mode, uint32_t max_field_length);

private:
    std::map<std::pair<const document::StringFieldValue*, search::Normalizing>, Tokens> _tokens;
};

/**
 * This class does normal utf8 searches.
 * This class uses an highly optimized version of the tokenize method in fastlib.
 **/
class UTF8StrChrFieldSearcher : public UTF8StringFieldSearcherBase {
public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    explicit UTF8StrChrFieldSearcher(FieldIdT fId) : UTF8StringFieldSearcherBase(fId), _annotations(nullptr) {}
    /**
     * Take tokens from linguistics annotations (when present) instead of tokenizing the raw text,
     * giving the same tokens (stemming, segmentation) as indexed mode. Used by reverse matching.
     **/
    void use_annotations(AnnotatedTokenCache& cache) noexcept { _annotations = &cache; }
    void onValue(const document::FieldValue& fv) override;

protected:
    size_t matchTerm(const FieldRef& f, search::streaming::QueryTerm& qt) override;
    size_t matchTerms(const FieldRef& f, size_t shortestTerm) override;

private:
    AnnotatedTokenCache* _annotations;
    void match_annotated(const document::StringFieldValue& value);
};

} // namespace vsm
