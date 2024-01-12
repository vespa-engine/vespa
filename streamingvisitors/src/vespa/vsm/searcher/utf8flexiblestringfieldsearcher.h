// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "utf8stringfieldsearcherbase.h"

namespace vsm {

/**
 * This class does utf8 searches based on the query term type.
 * It will choose between regular search strategy (including prefix) and substring search strategy.
 **/
class UTF8FlexibleStringFieldSearcher : public UTF8StringFieldSearcherBase
{
private:
    /**
     * Tries to match the given query term against the content of the given field reference.
     * Search strategy is chosen based on the query term type.
     **/
    size_t matchTerm(const FieldRef & f, search::streaming::QueryTerm & qt) override;

    /**
     * Tries to match each query term in the underlying query against the content of the given field reference.
     * Search strategy is chosen based on the query term type.
     **/
    size_t matchTerms(const FieldRef & f, size_t shortestTerm) override;

    size_t match_regexp(const FieldRef & f, search::streaming::QueryTerm & qt);

public:
    std::unique_ptr<FieldSearcher> duplicate() const override;
    explicit UTF8FlexibleStringFieldSearcher(FieldIdT fId);
};

}

