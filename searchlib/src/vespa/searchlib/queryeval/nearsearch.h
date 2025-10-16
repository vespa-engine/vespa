// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include "multisearch.h"
#include <optional>

namespace search::queryeval {

class IElementGapInspector;

/**
 * The near search base implements the common logic of the near and o-near search.
 */
class NearSearchBase : public MultiSearch
{
protected:
    uint32_t _data_size;
    uint32_t _window;
    uint32_t _num_negative_terms;
    uint32_t _exclusion_distance;
    bool     _strict;

    using TermFieldMatchDataArray = search::fef::TermFieldMatchDataArray;

    class MatcherBase
    {
    private:
        uint32_t                _window;
        search::fef::ElementGap _element_gap;
        TermFieldMatchDataArray _inputs;
        uint32_t                _num_negative_terms;
        uint32_t                _exclusion_distance;
    protected:
        uint32_t window() const noexcept { return _window; }
        search::fef::ElementGap get_element_gap() const noexcept { return _element_gap; }
        const TermFieldMatchDataArray &inputs() const { return _inputs; }
        uint32_t num_negative_terms() const noexcept { return _num_negative_terms; }
        uint32_t exclusion_distance() const noexcept { return _exclusion_distance; }
    public:
        MatcherBase(uint32_t win, search::fef::ElementGap element_gap, uint32_t fieldId, const TermFieldMatchDataArray &in, uint32_t num_negative_terms, uint32_t exclusion_distance)
            : _window(win),
              _element_gap(element_gap),
              _inputs(),
              _num_negative_terms(num_negative_terms),
              _exclusion_distance(exclusion_distance)
        {
            for (size_t i = 0; i < in.size(); ++i) {
                if (in[i]->getFieldId() == fieldId) {
                    _inputs.add(in[i]);
                }
            }
        }
    };

    /**
     * Typedef the list of positions iterators because it takes far too much space to write out :-)
     */
    using PositionsIteratorList = std::vector<search::fef::TermFieldMatchData::PositionsIterator>;

    /**
     * Returns whether or not given document matches. This should only be called when all child terms are all
     * at the same document.
     *
     * @param docId The document for which we are checking.
     * @return True if the document matches.
     */
    virtual bool match(uint32_t docId) = 0;

    /**
     * Performs seek() on all child terms until a match is found. This method calls setDocId() to signal the
     * document found.
     *
     * @param docId The document id from which to start seeking.
     */
    void seekNext(uint32_t docId);

public:
    /**
     * Constructs a new search for the given term match data with negative terms.
     *
     * @param terms  The iterators for all child terms (positive terms first, then negative terms).
     * @param data   The term match data objects for all child terms.
     * @param window The size of the window in which all positive terms must occur.
     * @param num_negative_terms The number of negative terms (last N children).
     * @param exclusion_distance The "brick size" around negative terms that breaks the window.
     * @param strict Whether or not to skip to next matching document if seek fails.
     */
    NearSearchBase(Children terms,
                   const TermFieldMatchDataArray &data,
                   uint32_t window,
                   uint32_t num_negative_terms,
                   uint32_t exclusion_distance,
                   bool strict);

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void doSeek(uint32_t docId) override;
};

/**
 * The near search matches only when all of its child terms occur within some given window size.
 */
class NearSearch : public NearSearchBase
{
private:
    struct Matcher : public NearSearchBase::MatcherBase
    {
        Matcher(uint32_t win, search::fef::ElementGap element_gap, uint32_t fieldId, const TermFieldMatchDataArray &in, uint32_t num_negative_terms, uint32_t exclusion_distance)
            : MatcherBase(win, element_gap, fieldId, in, num_negative_terms, exclusion_distance) {}
        template <typename MatchResult>
        void match(uint32_t docId, MatchResult& result);
    };

    std::vector<Matcher> _matchers;
    bool match(uint32_t docId) override;

public:
    /**
     * Constructs a new search for the given term match data.
     *
     * @param terms  The iterators for all child terms.
     * @param data   The term match data objects for all child terms.
     * @param window The size of the window in which all terms must occur.
     * @param element_gap_inspector An inspector that retrieves the element gap for a given field.
     * @param strict Whether or not to skip to next matching document if seek fails.
     */
    NearSearch(Children terms,
               const TermFieldMatchDataArray &data,
               uint32_t window,
               const IElementGapInspector& element_gap_inspector,
               bool strict = true);

    /**
     * Constructs a new search for the given term match data with negative terms.
     *
     * @param terms  The iterators for all child terms (positive terms first, then negative terms).
     * @param data   The term match data objects for all child terms.
     * @param window The size of the window in which all positive terms must occur.
     * @param num_negative_terms The number of negative terms (last N children).
     * @param exclusion_distance The "brick size" around negative terms that breaks the window.
     * @param element_gap_inspector An inspector that retrieves the element gap for a given field.
     * @param strict Whether or not to skip to next matching document if seek fails.
     */
    NearSearch(Children terms,
               const TermFieldMatchDataArray &data,
               uint32_t window,
               uint32_t num_negative_terms,
               uint32_t exclusion_distance,
               const IElementGapInspector& element_gap_inspector,
               bool strict = true);

    ~NearSearch() override;
    void get_element_ids(uint32_t docId, std::vector<uint32_t>& element_ids) override;
};

/**
 * The o-near search matches only when all of its child terms occur within some given window size, in the
 * same order as they appear as children of this.
 */
class ONearSearch : public NearSearchBase
{
private:
    struct Matcher : public NearSearchBase::MatcherBase
    {
        Matcher(uint32_t win, search::fef::ElementGap element_gap, uint32_t fieldId, const TermFieldMatchDataArray &in, uint32_t num_negative_terms, uint32_t exclusion_distance)
            : MatcherBase(win, element_gap, fieldId, in, num_negative_terms, exclusion_distance) {}
        template <typename MatchResult, typename WindowFilter>
        void match_impl(uint32_t docId, MatchResult& match_result, WindowFilter&& window_filter);
        template <typename MatchResult>
        void match(uint32_t docId, MatchResult& match_result);
    };

    std::vector<Matcher> _matchers;
    bool match(uint32_t docId) override;

public:
    /**
     * Constructs a new search for the given term match data.
     *
     * @param terms  The iterators for all child terms.
     * @param data   The term match data objects for all child terms.
     * @param window The size of the window in which all terms must occur.
     * @param element_gap_inspector An inspector that retrieves the element gap for a given field.
     * @param strict Whether or not to skip to next matching document if seek fails.
     */
    ONearSearch(Children terms,
                const TermFieldMatchDataArray &data,
                uint32_t window,
                const IElementGapInspector& element_gap_inspector,
                bool strict = true);

    /**
     * Constructs a new search for the given term match data with negative terms.
     *
     * @param terms  The iterators for all child terms (positive terms first, then negative terms).
     * @param data   The term match data objects for all child terms.
     * @param window The size of the window in which all positive terms must occur.
     * @param num_negative_terms The number of negative terms (last N children).
     * @param exclusion_distance The "brick size" around negative terms that breaks the window.
     * @param element_gap_inspector An inspector that retrieves the element gap for a given field.
     * @param strict Whether or not to skip to next matching document if seek fails.
     */
    ONearSearch(Children terms,
                const TermFieldMatchDataArray &data,
                uint32_t window,
                uint32_t num_negative_terms,
                uint32_t exclusion_distance,
                const IElementGapInspector& element_gap_inspector,
                bool strict = true);

    ~ONearSearch() override;

    void get_element_ids(uint32_t docId, std::vector<uint32_t>& element_ids) override;
};

}
