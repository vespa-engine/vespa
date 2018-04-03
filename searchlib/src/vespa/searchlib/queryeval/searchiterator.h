// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "posting_info.h"
#include "begin_and_end_id.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/trinary.h>
#include <memory>
#include <vector>

namespace vespalib { class ObjectVisitor; }

namespace search { class BitVector; }

namespace search::queryeval {

/**
 * This is the abstract superclass of all search objects. Each search
 * object act as an iterator over documents that are results for the
 * subquery represented by that search object. Search objects will be
 * combined into a tree structure to perform query evaluation in
 * parallel. The unpack method is used to unpack match information for
 * a document. The placement and format of this match data is a
 * contract between the application and the leaf search objects and is
 * of no concern to the interface defined by this class.
 **/
class SearchIterator
{
private:
    using BitVectorUP = std::unique_ptr<BitVector>;
    /**
     * The current document id for this search object. This variable
     * will have a value that is either @ref beginId, @ref endId or a
     * document id representing a hit for this search object.
     **/
    uint32_t _docid;

    /**
     * This is the end of the the lidspace this iterator shall consider.
     */
    uint32_t _endid;

    void and_hits_into_strict(BitVector &result, uint32_t begin_id);
    void and_hits_into_non_strict(BitVector &result, uint32_t begin_id);
protected:
    /**
     * This method is used by the @ref doSeek method to indicate that
     * a document is a hit. This method is also used to indicate that
     * no more hits are available by using the @ref endId value.
     *
     * @param id docid for hit
     **/
    void setDocId(uint32_t id) { _docid = id; }

    /**
     * Used to adjust the end of the legal docid range.
     * Used by subclasses instead of a full initRange call.
     *
     * @param end_id the first docid outside the legal iterator range
     */
    void setEndId(uint32_t end_id) { _endid = end_id; }

    /**
     * Will terminate the iterator by setting it past the end.
     * Further calls to isAtEnd() will then return true.
     */
    void setAtEnd() { _docid = search::endDocId; }

public:
    using Trinary=vespalib::Trinary;
    // doSeek and doUnpack are called by templated classes, so making
    // them public to avoid complicated friend requests. Note that if
    // you call doSeek and doUnpack directly instead of using
    // seek/unpack, you are bypassing docid checks and need to know
    // what you are doing.

    /**
     * This method must be overridden to perform the actual seeking
     * for the concrete search class. The task of this method is to
     * check whether the given document id is a hit for this search
     * object. The current document id is changed with the @ref
     * setDocId method. When this method returns, the current document
     * id must have been updated as follows: if the candidate document
     * id was in fact a hit, this is now the new current document
     * id. If the candidate document id was not a hit, the method may
     * choose to either leave the current document id as is, or
     * increase it to indicate the next hit for this search object
     * (@ref endId being a valid value).
     *
     * @param docid hit candidate
     **/
    virtual void doSeek(uint32_t docid) = 0;

    /**
     * This method must be overridden to perform the actual unpacking
     * for the concrete search class. The task of this method is to
     * unpack match information for the given docid. This method can
     * assume that the given document is also the current position of
     * the iterator. This is checked by the @ref unpack method which
     * invokes this method.
     *
     * @param docid what docid to unpack match information for.
     **/
    virtual void doUnpack(uint32_t docid) = 0;

    /**
     * This sets the range the iterator shall work.
     * As soon as it reaches its limit it can stop.
     * Iterators can overload this one and do what it needs to do.
     * It must also rewind if instructed to do so.
     *
     * @param beginId This is the first valid docId and the lowest that will be given to doSeek.
     * @param endId This is the first docid after the valid range.
     */ 
    virtual void initRange(uint32_t begin_id, uint32_t end_id);

    /**
     * Will initialize the full range.
     **/
    void initFullRange() { initRange(1, search::endDocId); }

    /**
     * Find all hits in the currently searched range (specified by
     * initRange) and return them as a bitvector. This function will
     * perform term-at-a-time evaluation and should only be used for
     * terms not needed for ranking. Calling this function will
     * exhaust this iterator and no more results will be available in
     * the currently searched range after this function returns.
     *
     * @return bitvector with hits for this iterator
     * @param begin_id the lowest document id that may be a hit
     *                 (we do not remember beginId from initRange)
     **/
    virtual BitVectorUP get_hits(uint32_t begin_id);

    /**
     * Find all hits in the currently searched range (specified by
     * initRange) and OR them into the given temporary result. This
     * function will perform term-at-a-time evaluation and should only
     * be used for terms not needed for ranking. Calling this function
     * will exhaust this iterator and no more results will be
     * available in the currently searched range after this function
     * returns.
     *
     * @param result result to be augmented by adding hits from this
     *               iterator.
     * @param begin_id the lowest document id that may be a hit
     *                 (we might not remember beginId from initRange)
     **/
    virtual void or_hits_into(BitVector &result, uint32_t begin_id);

    /**
     * Find all hits in the currently searched range (specified by
     * initRange) and OR them into the given temporary result. This
     * function will perform term-at-a-time evaluation and should only
     * be used for terms not needed for ranking. Calling this function
     * will exhaust this iterator and no more results will be
     * available in the currently searched range after this function
     * returns.
     *
     * @param result result to be augmented by adding hits from this
     *               iterator.
     * @param begin_id the lowest document id that may be a hit
     *                 (we might not remember beginId from initRange)
     **/
    virtual void and_hits_into(BitVector &result, uint32_t begin_id);

public:
    typedef std::unique_ptr<SearchIterator> UP;

    /**
     * The constructor sets the current document id to @ref beginId.
     **/
    SearchIterator();
    SearchIterator(const SearchIterator &) = delete;
    SearchIterator &operator=(const SearchIterator &) = delete;


    /**
     * Special value indicating that this searcher has not yet started
     * seeking through documents.  This must match beginId() in
     * search::fef::TermFieldMatchData class.
     *
     * @return constant
     **/
    static uint32_t beginId() { return beginDocId; }

    /**
     * Tell if the iterator has reached the end.
     *
     * @return true if the iterator has reached its end.
     **/
    bool isAtEnd() const { return isAtEnd(_docid); }
    bool isAtEnd(uint32_t docid) const { 
        if (__builtin_expect(docid >= _endid, false)) {
            return true;
        }
        return false;
    }

    /**
     * Obtain the current document id for this search object. The
     * value is either @ref beginId, @ref endId or a document id
     * representing a hit for this search object.
     *
     * @return current document id
     **/
    uint32_t getDocId() const { return _docid; }

    uint32_t getEndId() const { return _endid; }

    /**
     * Check if the given document id is a hit. If it is a hit, the
     * current document id of this search object is set to the given
     * document id. If it is not a hit, the current document id is
     * either unchanged, set to the next hit, or set to @ref endId.
     *
     * @return true if the given document id is a hit.
     * @param docid hit candidate
     **/
    bool seek(uint32_t docid) {
        if (__builtin_expect(docid > _docid, true)) {
            doSeek(docid);
        }
        return (docid == _docid);
    }

    /**
     * Seek to the next docid and return it. Start with the one given.
     * With protection for going backWards.
     * Note that this requires the iterator to be strict.
     *
     * @return the first matching docid
     * @param docid hit candidate
     **/ 
    uint32_t seekFirst(uint32_t docid) {
        if (__builtin_expect(docid > _docid, true)) {
            doSeek(docid);
        }
        return _docid;
    }

    /**
     * Seek to the next docid and return it. Start with the one given.
     * Without protection for going backWards.
     * Note that this requires the iterator to be strict.
     *
     * @return the first matching docid
     * @param docid hit candidate
     **/ 
    uint32_t seekNext(uint32_t docid) {
        doSeek(docid);
        return _docid;
    }

    /**
     * Unpack hit information for the given docid if available. This
     * method may also change the current docid for this iterator.
     *
     * @param docid what docid to unpack match information for.
     **/
    void unpack(uint32_t docid) {
        if (__builtin_expect(seek(docid), true)) {
            doUnpack(docid);
        }
    }

    /**
     * Return global posting info associated with this search iterator.
     *
     * @return global posting info or NULL if no info is available.
     **/
    virtual const PostingInfo *getPostingInfo() const { return nullptr; }

    /**
     * Create a human-readable representation of this object. This
     * method will use object visitation internally to capture the
     * full structure of this object.
     *
     * @return structured human-readable representation of this object
     **/
    vespalib::string asString() const;

    /**
     * Obtain the fully qualified name of the concrete class for this
     * object. The default implementation will perform automatic name
     * resolving. There is only a need to override this function if
     * you want to impersonate another class.
     *
     * @return fully qualified class name
     **/
    virtual vespalib::string getClassName() const;

    /**
     * Visit each of the members of this object. This method should be
     * overridden by subclasses and should present all appropriate
     * internal structure of this object to the given visitor. Note
     * that while each level of a class hierarchy may cooperate to
     * visit all object members (invoking superclass method within
     * method), this method, as implemented in the SearchIterator class
     * should not be invoked, since its default implementation is
     * there to signal about the method not being overridden.
     *
     * @param visitor the visitor of this object
     **/
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;

    /**
     * Empty, just defined to make it virtual.
     **/
    virtual ~SearchIterator() { }

    /**
     * @return true if it is a bitvector
     */
    virtual bool isBitVector() const { return false; }
    /**
     * @return true if it is a source blender
     */
    virtual bool isSourceBlender() const { return false; }
    /**
     * @return true if it is a multi search
     */
    virtual bool isMultiSearch() const { return false; }

    /**
     * This is used for adding an extra filter. If it is accepted it will return an empty UP.
     * If not you will get in in return. Currently it will only be accepted by a
     * MultiBitVector<And> with a pure 'and' path down if it is an BitVector,
     * or by a strict AND with a pure 'and' path. Be careful if you you plan to steal the filter.
     *
     * @param filter the searchiterator that is an extra filter.
     * @param estimate is the number of hits this filter is expected to produce.
     * @return the given filter or empty if it has been consumed.
     **/
    virtual UP andWith(UP filter, uint32_t estimate);

    virtual Trinary is_strict() const { return Trinary::Undefined; }

};

}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::SearchIterator &obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::SearchIterator *obj);

