// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_term_decoder.h"
#include "query.h"
#include <vespa/vespalib/util/exceptions.h>

namespace search {

QueryTermSimple::UP
QueryTermDecoder::decodeTerm(QueryPacketT term)
{
    QueryTermSimple::UP result;
    QueryNodeResultFactory factory;
    Query query(factory, term);
    if (query.valid() && (dynamic_cast<QueryTerm *>(query.getRoot().get()))) {
        result.reset(static_cast<QueryTerm *>(query.getRoot().release()));
    } else {
        throw vespalib::IllegalStateException("Failed decoding query term");
    }
    return result;
}

}
