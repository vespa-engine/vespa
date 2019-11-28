// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_decoder.h"
#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/vespalib/util/exceptions.h>

namespace search {

using namespace search::streaming;

QueryTermSimple::UP
QueryTermDecoder::decodeTerm(QueryPacketT term)
{
    QueryTermSimple::UP result;
    QueryNodeResultFactory factory;
    Query query(factory, term);
    if (query.valid() && (dynamic_cast<const QueryTerm *>(&query.getRoot()))) {
        result.reset(static_cast<QueryTerm *>(Query::steal(std::move(query)).release()));
    } else {
        throw vespalib::IllegalStateException("Failed decoding query term");
    }
    return result;
}

}
