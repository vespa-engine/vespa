// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "utf8strchrfieldsearcher.h"

#include "tokenizereader.h"

#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/searchlib/util/token_extractor.h>

#include <algorithm>

using search::byte;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

std::unique_ptr<FieldSearcher> UTF8StrChrFieldSearcher::duplicate() const {
    return std::make_unique<UTF8StrChrFieldSearcher>(*this);
}

size_t UTF8StrChrFieldSearcher::matchTerms(const FieldRef& f, size_t mintsz) {
    (void)mintsz;
    termcount_t words(0);
    if (f.size() >= _buf->size()) {
        _buf->reserve(f.size() + 1);
    }
    cmptype_t* fn = &(*_buf.get())[0];

    TokenizeReader reader(reinterpret_cast<const byte*>(f.data()), f.size(), fn);
    while (reader.hasNext()) {
        size_t fl = reader.tokenize(normalize_mode());
        for (auto qt : _qtl) {
            const cmptype_t* term;
            termsize_t       tsz = qt->term(term);
            if ((tsz <= fl) && (prefix() || qt->isPrefix() || (tsz == fl))) {
                const cmptype_t *tt = term, *et = term + tsz;
                for (const cmptype_t* fnt = fn; (tt < et) && (*tt == *fnt); tt++, fnt++)
                    ;
                if (tt == et) {
                    addHit(*qt, words);
                }
            }
        }
        words++;
    }
    return words;
}

const AnnotatedTokenCache::Tokens& AnnotatedTokenCache::get(const document::StringFieldValue& value,
                                                            search::Normalizing mode, uint32_t max_field_length) {
    auto [entry, inserted] = _tokens.try_emplace({&value, mode});
    Tokens& tokens = entry->second;
    if (!inserted) {
        return tokens;
    }
    // Same token extraction as the memory index (FieldInverter): terms sharing a span share a position.
    static const std::string                            no_field_name;
    search::linguistics::TokenExtractor                 extractor(no_field_name, max_field_length);
    search::linguistics::TokenExtractor::SpanTermVector terms;
    auto                                                trees = value.getSpanTrees();
    extractor.extract(terms, trees, value.getValueRef(), nullptr);
    std::vector<ucs4_t> buf;
    uint32_t            pos = 0;
    for (auto it = terms.begin(); it != terms.end(); ++pos) {
        auto span = it->span;
        for (; it != terms.end() && it->span == span; ++it) {
            buf.resize(2 * it->word.size() + 1); // folding may expand a character
            TokenizeReader reader(reinterpret_cast<const byte*>(it->word.data()), it->word.size(), buf.data());
            while (reader.hasNext()) {
                size_t len = reader.tokenize(mode);
                if (len > 0) {
                    tokens.chars.insert(tokens.chars.end(), buf.data(), buf.data() + len);
                    tokens.ends.push_back(tokens.chars.size());
                    tokens.positions.push_back(pos);
                }
            }
        }
    }
    tokens.length = pos;
    return tokens;
}

void UTF8StrChrFieldSearcher::onValue(const document::FieldValue& fv) {
    if (_annotations != nullptr && fv.isA(document::FieldValue::Type::STRING)) {
        const auto& value = static_cast<const document::StringFieldValue&>(fv);
        if (value.hasSpanTrees()) {
            match_annotated(value);
            return;
        }
    }
    StrChrFieldSearcher::onValue(fv);
}

void UTF8StrChrFieldSearcher::match_annotated(const document::StringFieldValue& value) {
    const auto& tokens = _annotations->get(value, normalize_mode(), maxFieldLength());
    uint32_t    begin = 0;
    for (size_t i = 0; i < tokens.ends.size(); begin = tokens.ends[i++]) {
        const ucs4_t* token = tokens.chars.data() + begin;
        size_t        len = tokens.ends[i] - begin;
        for (auto qt : _qtl) {
            const cmptype_t* term;
            termsize_t       tsz = qt->term(term);
            if ((tsz <= len) && (prefix() || qt->isPrefix() || (tsz == len)) && std::equal(term, term + tsz, token)) {
                addHit(*qt, tokens.positions[i]);
            }
        }
    }
    set_element_length(tokens.length);
}

size_t UTF8StrChrFieldSearcher::matchTerm(const FieldRef& f, QueryTerm& qt) {
    return matchTermRegular(f, qt);
}

} // namespace vsm
