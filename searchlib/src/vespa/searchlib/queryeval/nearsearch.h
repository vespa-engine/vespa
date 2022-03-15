// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include "andsearch.h"

namespace search::queryeval {

/**
 * The near search base implements the common logic of the near and o-near search.
 */
class NearSearchBase : public AndSearch
{
protected:
    uint32_t _data_size;
    uint32_t _window;
    bool     _strict;

    typedef search::fef::TermFieldMatchDataArray TermFieldMatchDataArray;

    class MatcherBase
    {
    private:
        uint32_t                _window;
        TermFieldMatchDataArray _inputs;
    protected:
        uint32_t window() const { return _window; }
        const TermFieldMatchDataArray &inputs() const { return _inputs; }
    public:
        MatcherBase(uint32_t win, uint32_t fieldId, const TermFieldMatchDataArray &in)
            : _window(win),
              _inputs()
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
    typedef std::vector<search::fef::TermFieldMatchData::PositionsIterator> PositionsIteratorList;

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
     * Constructs a new search for the given term match data.
     *
     * @param terms  The iterators for all child terms.
     * @param data   The term match data objects for all child terms.
     * @param window The size of the window in which all terms must occur.
     * @param strict Whether or not to skip to next matching document if seek fails.
     */
    NearSearchBase(Children terms,
                   const TermFieldMatchDataArray &data,
                   uint32_t window,
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
        Matcher(uint32_t win, uint32_t fieldId, const TermFieldMatchDataArray &in)
            : MatcherBase(win, fieldId, in) {}
        bool match(uint32_t docId);
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
     * @param strict Whether or not to skip to next matching document if seek fails.
     */
    NearSearch(Children terms,
               const TermFieldMatchDataArray &data,
               uint32_t window,
               bool strict = true);
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
        Matcher(uint32_t win, uint32_t fieldId, const TermFieldMatchDataArray &in)
            : MatcherBase(win, fieldId, in) {}
        bool match(uint32_t docId);
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
     * @param strict Whether or not to skip to next matching document if seek fails.
     */
    ONearSearch(Children terms,
                const TermFieldMatchDataArray &data,
                uint32_t window,
                bool strict = true);

};

}
