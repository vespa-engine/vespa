// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/simpletermdata.h>
#include <vespa/searchlib/query/querynoderesultbase.h>

namespace storage {

/**
 * This class keeps data for a query term that is used by the ranking framework.
 **/
class QueryTermData : public search::QueryNodeResultBase
{
private:
    search::fef::SimpleTermData   _termData;

public:
    DUPLICATE(QueryTermData); // create duplicate function

    virtual bool evaluate() const { return true; }
    virtual void reset() {}
    virtual bool getRewriteFloatTerms() const { return true; }

    search::fef::SimpleTermData &getTermData() { return _termData; }
};

} // namespace storage

