// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

/* $Id$ */

#include <list>
#include "juniperdebug.h"
#include "mcand.h"
#define _NEED_SUMMARY_CONFIG_IMPL 1
#include "SummaryConfig.h"

/** A class of objects describing a query highlight dynamic summary based on a
 *  given state of the matcher.
 *  This module defines the teaser appearance given the matches as input.
 */

/* The minimal distance to introduce a continuation symbol for */
#define MIN_CONTINUATION 8

/* The minimal surround length to ever set */
#define MIN_SURROUND_LEN 10

/* Allow word split if word longer than this */
#define MAX_SCAN_WORD 0x40

class Matcher;
class IDocumentFeeder;

class SummaryDesc
{
public:
    // Constructor that builds a description that can later be used to create
    // a suitable query in context / query highlight for the given matcher
    // in its current status:
    SummaryDesc(Matcher* matcher, ssize_t length, ssize_t min_length, int max_matches,
                int surround_len);

    /* Return a highlight tagged summary string
     * from this summary description
     */
    std::string get_summary(IDocumentFeeder* feeder, SummaryConfig* sumconf, size_t& char_size);

    /* Return a highlight tagged summary string
     * from this summary description
     */
    std::string get_summary(const char* buffer, size_t len,
                            const SummaryConfig* sumconf, size_t& char_size);

protected:

    /** A simple object that describes the contiguous elements of the generated summary
     */
    class highlight_desc
    {
    public:
        highlight_desc(off_t pos, ssize_t len, bool highlight);
        off_t _pos;      /* Start pos of item within document */
        ssize_t _len;     /* Length of print item */
        bool _highlight; /* Whether to highlight item or not */
    };

    void add_desc(off_t pos, ssize_t len, bool highlight);

    typedef std::set<MatchCandidate*,sequential_elem<MatchCandidate*> > cand_list;
    typedef std::list<highlight_desc> print_list;

    /** Helper function to build a simple query highlight of the complete document */
    void build_fulldoc_desc();

    /** Helper functions to build a dynamic teaser extract */
    int find_matches();
    int recompute_estimate(int len_per_elem);
    void build_highlight_descs();
    void locate_accidential_matches();

    bool overlap(MatchCandidate* m);
    bool word_connector(const unsigned char* s);
    int complete_extended_token(unsigned char* start, ssize_t length,
                                const unsigned char*& ptr, off_t increment);

private:
    /* desired net printout length */
    Matcher* _matcher;
    const key_occ_vector& _occ; // Reference to the matcher's occurrence list
    /* Reference to the matchers ordered set of matches (match result set) */
    match_candidate_set& _match_results;
    ssize_t _length;    // desired length of the generated summary
    ssize_t _min_length; // desired minimum length of the generated summary
    int _remaining;    // What's left to generate
    int _surround_len; // how much context to put around
    int _est_len;      // Estimated length of the generated summary
    int _hit_len;      // Estimated/computed total length of all query hit terms

    /* Temporary sequentially ordered match list used during computation */
    cand_list _clist;
    /* The resulting list of print descriptions */
    print_list _plist;
    const SummaryConfig* _sumconf; // The current config from a running get_summary call
    int _max_matches; // The maximal number of matches to try as long as within _min_length
    int _match_elems;  // Total number of keywords found in matches
    size_t _document_length; // Length of original document
    bool _fulldoc;     // Set if requesting a full document (to avoid cuts)

    SummaryDesc(SummaryDesc &);
    SummaryDesc &operator=(SummaryDesc &);
};


