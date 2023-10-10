// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "emptysearch.h"
#include <vector>

namespace search::queryeval {

    namespace sourceselector { class Iterator; }
/**
 * A simple implementation of the source blender operation. This class
 * is used to blend results from multiple sources. Each source is
 * represented with a separate search iterator. A source selector
 * iterator is used to select the appropriate source for each
 * document. The source blender will make sure to only propagate
 * unpack requests to one of the sources below, enabling them to use
 * the same target location for detailed match data unpacking.
 **/
class SourceBlenderSearch : public SearchIterator
{
public:
    /**
     * Small wrapper used to specify the underlying searches to be
     * blended.
     **/
    struct Child {
        SearchIterator *search;
        uint32_t    sourceId;
        Child() : search(nullptr), sourceId(0) { }
        Child(SearchIterator *s, uint32_t id) noexcept : search(s), sourceId(id) {}
    };
    using Children = std::vector<Child>;

private:
    SourceBlenderSearch(const SourceBlenderSearch &);
    SourceBlenderSearch &operator=(const SourceBlenderSearch &);
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    bool isSourceBlender() const override { return true; }
    static EmptySearch _emptySearch;
protected:
    using Iterator = sourceselector::Iterator;
    using Source = uint8_t;
    using SourceIndex = std::vector<Source>;
    SearchIterator            * _matchedChild;
    std::unique_ptr<Iterator>   _sourceSelector;
    SourceIndex                 _children;
    uint32_t                    _docIdLimit;
    SearchIterator            * _sources[256];

    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;
    Trinary is_strict() const override { return Trinary::False; }
    SourceBlenderSearch(std::unique_ptr<Iterator> sourceSelector, const Children &children);
    SearchIterator * getSearch(Source source) const { return _sources[source]; }
public:
    /**
     * Create a new SourceBlender Search with the given children and
     * strictness. A strict blender can assume that all children below
     * are also strict. A non-strict blender has no strictness
     * assumptions about its children.
     *
     * @param sourceSelector This is an iterator that provide you with the
     *                       the correct source to use.
     * @param children the search objects we are blending
     *        this object takes ownership of the children.
     * @param strict whether this search is strict
     * (a strict search will locate its next hit when seeking fails)
     **/
    static SearchIterator::UP create(std::unique_ptr<Iterator> sourceSelector,
                                     const Children &children,
                                     bool strict);
    ~SourceBlenderSearch() override;
    size_t getNumChildren() const { return _children.size(); }
    SearchIterator::UP steal(size_t index) {
        SearchIterator::UP retval(_sources[_children[index]]);
        _sources[_children[index]] = nullptr;
        return retval;
    }
    void setChild(size_t index, SearchIterator::UP child);
    void initRange(uint32_t beginId, uint32_t endId) override;
};

}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::SourceBlenderSearch::Child &obj);

