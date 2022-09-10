// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniperdebug.h"
#include "querymodifier.h"
#include "querynode.h"

#include <vespa/log/log.h>
LOG_SETUP(".juniper.querymodifier");

namespace juniper {

Rewriter::Rewriter(IRewriter* rewriter, bool for_query, bool for_document)
    : _rewriter(rewriter), _for_query(for_query), _for_document(for_document)
{
    LOG(debug, "Creating Rewriter (%s %s)",
        (for_query ? "query" : ""), (for_document ? "document" : ""));
}


QueryModifier::QueryModifier()
    : _rewriters(), _has_expanders(false), _has_reducers(false)
{ }

QueryModifier::~QueryModifier()
{
    FlushRewriters();
}


void QueryModifier::FlushRewriters()
{
    // Delete all Rewriter objects
    _rewriters.delete_second();
    _rewriters.clear();
}


/* See rewriter.h for doc */
void QueryModifier::AddRewriter(const char* index_name, IRewriter* rewriter,
                                bool for_query, bool for_document)
{
    if (for_query || for_document)
        _rewriters.insert(index_name, new Rewriter(rewriter, for_query, for_document));
    if (for_query)    _has_expanders = true;
    if (for_document) _has_reducers = true;
}


/* Return any configured reducer/expander for the index, if any */
Rewriter* QueryModifier::FindRewriter(vespalib::stringref index_name)
{
    return _rewriters.find(index_name);
}

} // end namespace juniper
