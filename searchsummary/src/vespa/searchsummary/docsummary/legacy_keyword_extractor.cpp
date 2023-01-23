// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "legacy_keyword_extractor.h"
#include "idocsumenvironment.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/vespalib/stllike/hashtable.hpp>
#include <vespa/vespalib/util/size_literals.h>

/** Tell us what parts of the query we are interested in */

namespace search::docsummary {


bool useful(search::ParseItem::ItemCreator creator)
{
    return creator == search::ParseItem::ItemCreator::CREA_ORIG;
}


LegacyKeywordExtractor::LegacyKeywordExtractor()
    : IKeywordExtractor(),
      _legalPrefixes(),
      _legalIndexes()
{
}


LegacyKeywordExtractor::~LegacyKeywordExtractor() = default;

bool
LegacyKeywordExtractor::isLegalIndexName(const char *idxName) const
{
    return _legalIndexes.find(idxName) != _legalIndexes.end();
}

LegacyKeywordExtractor::IndexPrefix::IndexPrefix(const char *prefix) noexcept
    : _prefix(prefix)
{
}

LegacyKeywordExtractor::IndexPrefix::~IndexPrefix() = default;

bool
LegacyKeywordExtractor::IndexPrefix::Match(const char *idxName) const
{
    return vespalib::starts_with(idxName, _prefix);
}

void
LegacyKeywordExtractor::addLegalIndexSpec(const char *spec)
{
    if (spec == nullptr)
        return;

    vespalib::string toks(spec); // tokens
    vespalib::string tok; // single token
    size_t           offset; // offset into tokens buffer
    size_t           seppos; // separator position

    offset = 0;
    while ((seppos = toks.find(';', offset)) != vespalib::string::npos) {
        if (seppos == offset) {
            offset++; // don't want empty tokens
        } else {
            tok = toks.substr(offset, seppos - offset);
            offset = seppos + 1;
            if (tok[tok.size() - 1] == '*') {
                tok.resize(tok.size() - 1);
                addLegalIndexPrefix(tok.c_str());
            } else {
                addLegalIndexName(tok.c_str());
            }
        }
    }
    if (toks.size() > offset) { // catch last token
        tok = toks.substr(offset);
        if (tok[tok.size() - 1] == '*') {
            tok.resize(tok.size() - 1);
            addLegalIndexPrefix(tok.c_str());
        } else {
            addLegalIndexName(tok.c_str());
        }
    }
}


vespalib::string
LegacyKeywordExtractor::getLegalIndexSpec()
{
    vespalib::string spec;

    if (!_legalPrefixes.empty()) {
        for (auto& prefix : _legalPrefixes) {
            if (!spec.empty()) {
                spec.append(';');
            }
            spec.append(prefix.get_prefix());
            spec.append('*');
        }
    }

    for (const auto & index : _legalIndexes) {
        if (!spec.empty()) {
            spec.append(';');
        }
        spec.append(index);
    }
    return spec;
}


bool
LegacyKeywordExtractor::isLegalIndex(vespalib::stringref idx) const
{
    vespalib::string resolvedIdxName;

    if ( ! idx.empty() ) {
        resolvedIdxName = idx;
    } else {
        resolvedIdxName = "__defaultindex";
    }

    if (resolvedIdxName.empty())
        return false;

    return (isLegalIndexPrefix(resolvedIdxName.c_str()) ||
            isLegalIndexName(resolvedIdxName.c_str()));
}

}
