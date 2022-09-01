// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "children_iterators.h"

struct MultiSearchRemoveTest;

namespace search::queryeval {

class MultiBitVectorIteratorBase;

/**
 * A virtual intermediate class that serves as the basis for combining searches
 * like AND, OR, RANK or others that take a list of children.
 **/
class MultiSearch : public SearchIterator
{
    friend struct ::MultiSearchRemoveTest;
    friend class ::search::queryeval::MultiBitVectorIteratorBase;
    friend class MySearch;
public:
    /**
     * Defines how to represent the children iterators.
     */
    using Children = std::vector<SearchIterator::UP>;

    /**
     * Create a new Multi Search with the given children.
     *
     * @param children the search objects we are and'ing
     *        this object takes ownership of the children.
     **/
    explicit MultiSearch(Children children);
    ~MultiSearch() override;
    const Children & getChildren() const { return _children; }
    virtual bool isAnd() const { return false; }
    virtual bool isAndNot() const { return false; }
    virtual bool isOr() const { return false; }
    void insert(size_t index, SearchIterator::UP search);
    virtual bool needUnpack(size_t index) const { (void) index; return true; }
    void initRange(uint32_t beginId, uint32_t endId) override;
protected:
    MultiSearch();
    void doUnpack(uint32_t docid) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
private:
    SearchIterator::UP remove(size_t index); // friends only
    /**
     * Call back when children are removed / inserted after the Iterator has been constructed.
     * This is to support code that make assumptions that iterators do not move around or disappear.
     * These are invoked after the child has been removed.
     */
    virtual void onRemove(size_t index) { (void) index; }
    virtual void onInsert(size_t index) { (void) index; }

    bool isMultiSearch() const override { return true; }
    Children _children;
};

}
