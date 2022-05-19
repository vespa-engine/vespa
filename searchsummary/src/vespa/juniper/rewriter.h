// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$ */

#pragma once

#include "query.h"

/** @file rewriter.h
 *  This file describes describes Juniper's expected interface for
 *  query word rewrite (typically language dependent)
 *  Rewrite of certain query words into a list of words
 *  are enabled by issuing an AddRewriter call.
 *
 *  The AddRewriter call is implemented by Juniper.
 *  The IRewriter interface must be implemented by the caller module
 *  to serve calls from juniper when the particular ItemCreator is found.
 *  Subsequent AddRewriter calls using the same ItemCreator will override
 *  the previous setting (rewrite and/or reduction for that creator.
 *  Multiple AddRewriter calls with different creator values are accepted.
 */

namespace juniper
{

// Opaque handle only used by implementer:
struct RewriteHandle;


class IRewriter
{
public:
    virtual ~IRewriter() {}

    /** return the name of this particular rewriter (for debugging purposes) */
    virtual const char* Name() const = 0;

    /** Map the given term to its rewritten form(s) wrt. the given language
     *  represented with language identifiers compatible with the
     *  ones used in the Analyse calls (rpinterface.h)
     *  @return a handle that can be used to retrieve words
     *  representing the rewritten forms. A NULL return value means
     *  that no rewrites exist and that the original form should be used.
     */
    virtual RewriteHandle* Rewrite(uint32_t langid, const char* term) = 0;
    virtual RewriteHandle* Rewrite(uint32_t langid, const char* term, size_t length) = 0;

    /** Retrieve the next term from the RewriteHandle object
     *  To be used repeatedly by Juniper until NULL is returned to
     *  signal that there are no more rewrites.
     *  At this point the RewriteHandle object and all the returned terms
     *  may become invalid. Juniper will either retrieve all terms returned
     *  by a Map call OR call the ReleaseHandle call:
     */
    virtual const char* NextTerm(RewriteHandle* exp, size_t& length) = 0;
};

} // end namespace juniper

