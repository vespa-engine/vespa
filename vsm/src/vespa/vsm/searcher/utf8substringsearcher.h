// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vsm/searcher/utf8strchrfieldsearcher.h>

namespace vsm {

/**
 * This class does substring utf8 searches.
 **/
class UTF8SubStringFieldSearcher : public UTF8StringFieldSearcherBase
{
public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    UTF8SubStringFieldSearcher()             : UTF8StringFieldSearcherBase() { }
    UTF8SubStringFieldSearcher(FieldIdT fId) : UTF8StringFieldSearcherBase(fId) { }
protected:
    size_t matchTerm(const FieldRef & f, search::streaming::QueryTerm & qt) override;
    size_t matchTerms(const FieldRef & f, const size_t shortestTerm) override;
};

}

