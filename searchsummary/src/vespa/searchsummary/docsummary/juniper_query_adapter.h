// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/juniper/query.h>
#include <vespa/vespalib/stllike/string.h>

namespace search { class SimpleQueryStackDumpIterator; }
namespace search::fef { class Properties; }

namespace search::docsummary {

class KeywordExtractor;

/*
 * Class implementing an adapter used by juniper to examine the current
 * query.
 */
class JuniperQueryAdapter : public juniper::IQuery
{
private:
    KeywordExtractor *_kwExtractor;
    const vespalib::stringref _buf;
    const search::fef::Properties *_highlightTerms;

public:
    JuniperQueryAdapter(const JuniperQueryAdapter&) = delete;
    JuniperQueryAdapter operator= (const JuniperQueryAdapter&) = delete;
    JuniperQueryAdapter(KeywordExtractor *kwExtractor, vespalib::stringref buf,
                        const search::fef::Properties *highlightTerms = nullptr);
    ~JuniperQueryAdapter() override;
    bool skipItem(search::SimpleQueryStackDumpIterator *iterator) const;
    bool Traverse(juniper::IQueryVisitor *v) const override;
    bool UsefulIndex(const juniper::QueryItem* item) const override;
};

}
