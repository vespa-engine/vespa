// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/parsequery/item_creator.h>
#include <cstddef>
#include <cstdint>

#ifndef JUNIPER_RPIF
#define JUNIPER_RPIF 1
#endif

/** @file query.h
 *  This file describes describes Juniper's expected interface for
 *  advanced query processing. Clients of Juniper wishing to receive optimal
 *  teasers based on the original query should use this interface.
 *  Design principle: visitor pattern - adapted to allow minimal overhead
 *    and opaque implementation (at binary level) of the IQueryItem class.
 *    Query provider (such as fsearch/qrserver/DS doc.proc.pipeline) should implement
 *    IQuery such that the appropriate Visit* functions of IQueryVisitor gets called
 *    Proper IQueryVisitor instance(s) is implemented by Juniper.
 *
 *  Note that Juniper v.1.0.x also provides a more low level simple query
 *  interface through SimpleDynamicSummary. This interface only supports a query on
 *  the (abstract) form  ((phrase|keyword) OR)* by means of a
 *  single string of null or space separated words and is depreciated as of Juniper v.2.0.x
 */

namespace juniper {

using ItemCreator = search::parseitem::ItemCreator;

// For debugging purposes: return a text string with the creator enum name
const char* creator_text(ItemCreator);

class IQueryVisitor;

// Interface class for juniper query items
class QueryItem;

/** This is the basic query type, implemented by the query provider
 */
class IQuery
{
public:
    virtual ~IQuery() { }

    /** Traverse the query.
     *  This will lead to a prefix depth first traversal of the complete query
     *  and calls to the appropriate Visitor functions.
     */
    virtual bool Traverse(IQueryVisitor* v) const = 0;

    /** Check if the index specification associated with the query item is useful from
     *  a Juniper perspective (see fsearchrc, highlightindexes parameter)
     *  @param item A query item to check
     *  @return true if this index is valid for Juniper, false otherwise
     */
    virtual bool UsefulIndex(const QueryItem* item) const = 0;
};


/** IQueryVisitor is implemented by Juniper to enable Juniper to traverse the
 *  structure of an input query (Visitor pattern)
 */
class IQueryVisitor
{
public:

    /** To be called upon by IQuery::Traverse visiting an AND query item
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param arity The number of children of this item
     * @return if false, caller should skip calling this element's children visitors,
     *   otherwise caller should proceed as normal
     */
    virtual bool VisitAND(const QueryItem* item, int arity) = 0;

    /** To be called upon by IQuery::Traverse visiting an OR query item
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param arity The number of children of this item
     * @return if false, caller should skip calling this element's children visitors,
     *   otherwise caller should proceed as normal
     */
    virtual bool VisitOR(const QueryItem* item, int arity) = 0;

    /** To be called upon by IQuery::Traverse visiting an AND query item
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param arity The number of children of this item
     * @return if false, caller should skip calling this element's children visitors,
     *   otherwise caller should proceed as normal
     */
    virtual bool VisitANY(const QueryItem* item, int arity) = 0;

    /** To be called upon by IQuery::Traverse visiting a NEAR query item
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param arity The number of children of this item
     * @param limit The number of words that defines the nearness wanted
     * @return if false, caller should skip calling this element's children visitors,
     *   otherwise caller should proceed as normal
     */
    virtual bool VisitNEAR(const QueryItem* item, int arity, int limit) = 0;

    /** To be called upon by IQuery::Traverse visiting a WITHIN query item
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param arity The number of children of this item
     * @param limit The number of words that defines the nearness wanted
     * @return if false, caller should skip calling this element's children visitors,
     *   otherwise caller should proceed as normal
     */
    virtual bool VisitWITHIN(const QueryItem* item, int arity, int limit) = 0;

    /** To be called upon by IQuery::Traverse visiting a RANK query item
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param arity The number of children of this item
     * @return if false, caller should skip calling this element's children visitors,
     *   otherwise caller should proceed as normal
     */
    virtual bool VisitRANK(const QueryItem* item, int arity) = 0;

    /** To be called upon by IQuery::Traverse visiting a PHRASE query item
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param arity The number of children of this item
     * @return if false, caller should skip calling this element's children visitors,
     *   otherwise caller should proceed as normal
     */
    virtual bool VisitPHRASE(const QueryItem* item, int arity) = 0;

    /** To be called upon by IQuery::Traverse visiting an ANDNOT query item
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param arity The number of children of this item
     * @return if false, caller should skip calling this element's children visitors,
     *   otherwise caller should proceed as normal
     */
    virtual bool VisitANDNOT(const QueryItem* item, int arity) = 0;

    /** To be called upon by IQuery::Traverse visiting any other query item
     *  than the ones handled by Juniper (to avoid inconsistency in the
     *  traversal wrt. arities)
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param arity The number of children of this item (may be 0 if leaf node)
     * @return typically false to denote that caller should skip calling this
     *   element's children visitors,
     */
    virtual bool VisitOther(const QueryItem* item, int arity) = 0;

    /** Visit callback for the terminal type, to be called by IQuery::Traverse
     * when encountering individual keywords
     * @param item The (opaque to IQueryVisitor) item that is visited
     * @param keyword Textual representation of the query keyword in question
     * @param length Length of the keyword. If 0, it means keyword length is defined by
     *    null termination
     * @param prefix true if prefix match with this term is desired
     *   otherwise caller should proceed as normal
     * @param specialToken true if this term is treated as a special token
     */
    virtual void VisitKeyword(const QueryItem* item,
                              const char* keyword, const size_t length = 0,
                              bool prefix = false, bool specialToken = false) = 0;

    virtual ~IQueryVisitor() { }
};

}
