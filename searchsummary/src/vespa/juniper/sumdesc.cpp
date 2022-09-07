// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sumdesc.h"
#include "juniperdebug.h"
#include "juniper_separators.h"
#include "Matcher.h"
#include "appender.h"
#include <vespa/fastlib/text/unicodeutil.h>

#include <vespa/log/log.h>
LOG_SETUP(".juniper.sumdesc");

using namespace juniper::separators;

/** SummaryDesc: A class of objects describing a query highlight
 *  dynamic summary based on the current state of the provided
 *  matcher.
 */

/* a few utilities: */

namespace {

static constexpr char replacement_char = '.';

char printable_char(char c)
{
    unsigned char uc = (unsigned char) c;
    if (uc >= 0x80 || uc < (unsigned char) ' ') {
        return replacement_char;
    }
    return c;
}

bool wordchar(const unsigned char* s)
{
    unsigned char c = *s;
    if (c & 0x80) {
        ucs4_t u = Fast_UnicodeUtil::GetUTF8Char(s);
        return Fast_UnicodeUtil::IsWordChar(u);
    } else {
        return isalnum(c);
    }
}

bool wordchar_or_il_ann_char(const unsigned char* s, char32_t annotation_char)
{
    unsigned char c = *s;
    if (c & 0x80) {
        ucs4_t u = Fast_UnicodeUtil::GetUTF8Char(s);
        return Fast_UnicodeUtil::IsWordChar(u) ||
            static_cast<char32_t>(u) == annotation_char;
    } else {
        return isalnum(c);
    }
}

bool wordchar_or_il_ann_anchor(const unsigned char* s)
{
    return wordchar_or_il_ann_char(s, interlinear_annotation_anchor);
}

bool wordchar_or_il_ann_terminator(const unsigned char* s)
{
    return wordchar_or_il_ann_char(s, interlinear_annotation_terminator);
}

bool nonwordchar(const unsigned char* s)
{
    unsigned char c = *s;
    if (c & 0x80) {
        ucs4_t u = Fast_UnicodeUtil::GetUTF8Char(s);
        return !Fast_UnicodeUtil::IsWordChar(u);
    } else {
        return !isalnum(c);
    }
}

bool
il_ann_char(const unsigned char* s, char32_t annotation_char)
{
    unsigned char c = *s;
    if (c & 0x80) {
        ucs4_t u = Fast_UnicodeUtil::GetUTF8Char(s);
        return static_cast<char32_t>(u) == annotation_char;
    } else {
        return false;
    }
}

bool
il_ann_anchor_char(const unsigned char* s)
{
    return il_ann_char(s, interlinear_annotation_anchor);
}

bool
il_ann_separator_char(const unsigned char* s)
{
    return il_ann_char(s, interlinear_annotation_separator);
}

bool
il_ann_terminator_char(const unsigned char* s)
{
    return il_ann_char(s, interlinear_annotation_terminator);
}

/* Move backwards/forwards from ptr (no longer than to start) in an
 * UTF8 text until the beginning of the word or (if space, until
 * beginning of the next/last word)
 * @return The number of bytes moved
 */
int complete_word(unsigned char* start, ssize_t length,
                  const unsigned char*& ptr, off_t increment)
{
    bool (*chartest)(const unsigned char*);
    int moved = 0;
    bool whitespace_elim = false;
    const unsigned char* orig_ptr = ptr;

    LOG(spam, "complete_word start 0x%p, length %zd, ptr 0x%p, increment %" PRId64,
        start, length, ptr, static_cast<int64_t>(increment));
    // Make sure we are at the start of a character before doing any
    // comparisons
    int start_off = Fast_UnicodeUtil::UTF8move(start, length, ptr, 0);
    if (start_off) {
        LOG(spam, "Offset %d to start of char", start_off);
    }

    // Figure out if a word needs completion or if we are just going
    // to eliminate whitespace. Consider sequence from interlinear
    // annotation anchor to interlinear annotation terminator to be a
    // word.
    if (!wordchar(ptr)) {
        if (increment > 0 && il_ann_anchor_char(ptr)) {
            chartest = il_ann_terminator_char;
        } else if (increment < 0 && il_ann_terminator_char(ptr)) {
            chartest = il_ann_anchor_char;
        } else {
            whitespace_elim = true;
            // Change direction of scan
            increment = -increment;
            if (increment > 0) {
                chartest = wordchar_or_il_ann_anchor;
            } else {
                chartest = wordchar_or_il_ann_terminator;
            }
        }
    } else {
        // Found a wordchar at pointer
        // If moving forwards, we need to check the previous character
        // for "non-wordness".  Otherwise we might add an extra word
        if (increment > 0) {
            const unsigned char* pre_ptr = ptr;
            int cur_move = Fast_UnicodeUtil::UTF8move(start, length,
                    pre_ptr, -1);
            if (!wordchar(pre_ptr) && !il_ann_terminator_char(pre_ptr)) // Points at start of new word
            {
                whitespace_elim = true;
                // Change direction of scan
                increment = -increment;
                if (increment > 0) {
                    chartest = wordchar_or_il_ann_anchor;
                } else {
                    chartest = wordchar_or_il_ann_terminator;
                }
                ptr = pre_ptr;
                moved += cur_move;
            } else {
                chartest = nonwordchar;
            }
        } else {
            chartest = nonwordchar;
        }
    }

    // move until we reach a space/wordchar or the beginning/end of
    // the read:
    for (;;) {
        LOG(spam, "[%s%d%s%c]", (whitespace_elim ? "^" : ""),
            moved, (increment > 0 ? "+" : "-"), printable_char(*ptr));
        int cur_move = Fast_UnicodeUtil::UTF8move(start, length,
                ptr, increment);

        // give up if past end of read (may still be a successful move
        // ending at the first character outside of the start+length
        // range: (UTF8move guarantees that pointer never gets before
        // start)
        if (ptr >= start + length) {
            LOG(spam, "complete_word: Break at end of text");
            break;
        }

        // Give up if we found a split of a word
        if (cur_move <= 0)  // == 0 to avoid UTF8move bug in fastlib 1.3.3..
        {
            LOG(spam, "complete_word: Failing at char %c/0x%x", printable_char(*ptr), *ptr);
            break;
        }
        if (chartest(ptr)) {
            if (chartest == nonwordchar) {
                if (il_ann_separator_char(ptr)) {
                    if (increment > 0) {
                        chartest = il_ann_terminator_char;
                    } else {
                        chartest = il_ann_anchor_char;
                    }
                    moved += cur_move;
                    continue;
                } else if (il_ann_terminator_char(ptr)) {
                    if (increment < 0) {
                        chartest = il_ann_anchor_char;
                    }
                    moved += cur_move;
                    continue;
                } else if (il_ann_anchor_char(ptr)) {
                    if (increment > 0) {
                        chartest = il_ann_terminator_char;
                    }
                    moved += cur_move;
                    continue;
                }
            } else if ((chartest == il_ann_anchor_char) ||
                       (chartest == il_ann_terminator_char)) {
                chartest = nonwordchar;
                moved += cur_move;
                continue;
            }
               LOG(spam, "complete_word: Breaking at char %c/0x%x (%d)", printable_char(*ptr),
                   *ptr, cur_move);
            // count this character (it is the first blank/wordchar)
            // only if we are going forward and it is a word character
            // since we are then supposed to be pointing to the first
            // char not in the word while going backwards we stop at
            // the start character of the word!
            if (increment > 0)
                moved += cur_move;
            break; // Found first blank/word char..
        }
        moved += cur_move;
        if (moved >= MAX_SCAN_WORD &&
            (chartest != il_ann_anchor_char) &&
            (chartest != il_ann_terminator_char)) {
            LOG(spam, "Word length extended max word length %d, "
                "breaking at char 0x%x", MAX_SCAN_WORD, *ptr);
            break;
        }
    }
    // Adjust for getting to the start of the start character:
    if (start_off)
        moved += increment > 0 ? -start_off : start_off;

       LOG(spam, "complete_word: %s %d bytes",
           (whitespace_elim ? "ws cut"
            : (increment > 0 ? "appended"
               : "prepended")), moved);
    // Make sure pointer is correct as well:
    ptr = orig_ptr + increment*moved;
    moved = (whitespace_elim ? -moved : moved);
    return moved;
}

}

SummaryDesc::highlight_desc::highlight_desc(off_t pos,
        ssize_t len, bool highlight)
    : _pos(pos), _len(len), _highlight(highlight)
{
    LOG(spam, "-- new desc: pos %" PRId64 " len %ld %s",
        static_cast<int64_t>(_pos), _len, (highlight ? "(highlight)" : ""));
    assert(pos >= 0);
}


SummaryDesc::SummaryDesc(Matcher* matcher, ssize_t length, ssize_t min_length,
                         int max_matches, int surround_len)
    : _matcher(matcher),
      _occ(matcher->OccurrenceList()),
      _match_results(matcher->OrderedMatchSet()),
      _length(length),
      _min_length(min_length),
      _remaining(length),
      _surround_len(surround_len),
      _est_len(0),
      _hit_len(0),
      _clist(),
      _plist(),
      _sumconf(),
      _max_matches(max_matches),
      _match_elems(),
      _document_length(matcher->DocumentSize()),
    _fulldoc()
{
    /* Check if the whole document fits within requested length and
     * process this
     */
    if (length + MIN_CONTINUATION*4 > (int)_document_length) {
        build_fulldoc_desc();
        return;
    }

    /* Adjust to sensible values */
    if (_surround_len < MIN_SURROUND_LEN)
        _surround_len = MIN_SURROUND_LEN;

    /* decide what amount of matches to use (stored in _clist) */
    _match_elems = find_matches();

    /* build highlight descriptor list */
    build_highlight_descs();

    /* Done with matches list. Clean up */
    _clist.clear();

    /* Spin through the resulting descriptor list and query term
     * occurrence list to
     *  1. identify (for highlight) accidental query term occurrences
     *     that are not part of the match
     *  2. Split descriptor list where new keyword matches are
     *     found. Extend if necessary due to partially included keywords.
     *  3. identify overlapping regions (possibly created by 2)
     */
    locate_accidential_matches();
}

SummaryDesc::~SummaryDesc() = default;


void SummaryDesc::locate_accidential_matches()
{
    key_occ_vector::const_iterator kit = _occ.begin();

    for (print_list::iterator pit = _plist.begin();
         pit != _plist.end();
         ++pit)
    {
        highlight_desc* d = &(*pit);

        print_list::iterator nit = pit;
        bool more = (++nit != _plist.end());

        if (d->_highlight)
            continue; // Ignore already found keywords..

        /* Now investigate if there are other matches than the best
         * ones that goes within the selected print context
         */

        /* Advance occurrence iterator until *kit (keyword occurrence)
         * overlap d (current descriptor) or is past d
         */
        while (kit != _occ.end()
               && (*kit)->startpos() + (*kit)->tokenlen <= d->_pos)
            ++kit;

        if (_matcher->UsesValid()) {
            /* If there are subphrases or other restricting subqueries
             * we must continue further on until we are past d or we
             * have found an element that has the valid bit set
             */
            while (kit != _occ.end() &&
                   !(*kit)->valid() &&
                   (*kit)->startpos() + (*kit)->tokenlen <= d->_pos + d->_len)
                ++kit;
        }

        if (kit == _occ.end())
            return;

        /* Turn "token cut at start" into "token contained in" case */
        if ((*kit)->startpos() < d->_pos) {
            off_t offset = d->_pos - (*kit)->startpos();
            LOG(spam, "Convert start cut: offset %" PRId64, static_cast<int64_t>(offset));
            d->_pos -= offset;
            d->_len += offset;
        }

        /* Split descriptors each time a new occurrence is found
         * within current descriptor */
        for (;
             kit != _occ.end()
                 && (*kit)->startpos() + (*kit)->tokenlen <= d->_pos + d->_len;
             ++kit)
        {
            if (_matcher->UsesValid() && !(*kit)->valid())
                continue;
            /* simple split - occurrence contained in (but maybe at
             * start of) descriptor */
            off_t kpos = (*kit)->startpos();
            off_t klen = (*kit)->tokenlen;
            off_t start_len = kpos - d->_pos;
            off_t end_len = (d->_pos + d->_len) - (kpos + klen);

            LOG(spam, "Split: (%" PRId64 ",%" PRId64 ") (%" PRId64 ", %" PRId64 " ) (%" PRId64 ", %" PRId64 ")",
                static_cast<int64_t>(d->_pos), static_cast<int64_t>(start_len),
                static_cast<int64_t>(kpos), static_cast<int64_t>(klen),
                static_cast<int64_t>(kpos + klen), static_cast<int64_t>(end_len));

            if (start_len > 0)
                _plist.insert(pit, highlight_desc(d->_pos, start_len, false));

            // new keyword
            print_list::iterator kwit =
                _plist.insert(pit, highlight_desc(kpos, klen, true));

            if (end_len) {
                LOG(spam, "-- Was: (%" PRId64 ", %" PRId64 ")", static_cast<int64_t>(d->_pos), static_cast<int64_t>(d->_len));
                d->_pos = kpos + klen;
                d->_len  = end_len;
                LOG(spam, "Modifying current to end (%" PRId64 ", %" PRId64 ")",
                    static_cast<int64_t>(d->_pos), static_cast<int64_t>(d->_len));
            } else {
                LOG(spam, "Erasing (%" PRId64 ", %" PRId64 ")", static_cast<int64_t>(d->_pos), static_cast<int64_t>(d->_len));
                pit = _plist.erase(pit);
                // Must ensure that d is valid (as the last descriptor seen)
                // at top of loop and after end!!
                d = &(*kwit);
            }
        }
        if (kit == _occ.end())
            return;

        /* Handle cut end occurrence separately */
        off_t d_end = d->_pos + d->_len;

        if ((*kit)->startpos() < d_end
            && (*kit)->startpos() + (*kit)->tokenlen > d_end)
        {
            off_t kpos = (*kit)->startpos();
            off_t klen = (*kit)->tokenlen;
            off_t offset = (kpos + klen) - d_end;

            /* Detect if the next descriptor held part of this token */
            if (more) {
                highlight_desc& nd = *nit;
                if (nd._pos < kpos) {
                    LOG(spam, "(endsplit) Adjusting next desc %" PRId64 " bytes", static_cast<int64_t>(offset));
                    nd._pos += offset;
                    nd._len -= offset;
                }
            }
            d->_len -= (klen - offset);

            LOG(spam, "[%" PRId64 "] Endsplit: (%" PRId64 ", %" PRId64 ") (%" PRId64 ", %" PRId64 ")",
                static_cast<int64_t>(offset), static_cast<int64_t>(d->_pos), static_cast<int64_t>(d->_len), static_cast<int64_t>(kpos), static_cast<int64_t>(klen));

            /* Insert new desc after the just processed one */
            pit = _plist.insert(++pit, highlight_desc(kpos, klen, true));
            ++kit;
            if (kit == _occ.end())
                return;
        }
        if (pit == _plist.end())
            break;
    } // end for (pit..)
}


/* find a proper amount of matches */

int SummaryDesc::find_matches()
{
    int match_len = 0;
    int match_count = 0;
    int match_elems = 0;
    int adjust_len = 0;
    _est_len = 0;

    // Find enough proper matches (without overlap)
    for (match_candidate_set::iterator it = _match_results.begin();
         it != _match_results.end();
         ++it)
    {
        MatchCandidate* m = (*it);
        if (overlap(m))
            continue;

        ssize_t size = m->size();

        assert(size >= 0);
        m->make_keylist();
        keylist& klist = m->_klist;
        assert(klist.size() > 0);
        (void) klist;

        _clist.insert(m);

        /* Adjust length in case of lack of prefix context */
        int pre = m->starttoken() - m->ctxt_startpos();
        if (pre < _surround_len)
            adjust_len += _surround_len - pre;

        match_len += size;

        if (LOG_WOULD_LOG(spam)) {
            std::string s; m->dump(s);
            LOG(spam, "MatchCandidate(%s) size %ld, tot.len %d", s.c_str(), size, match_len);
        }
        assert(match_len > 0);
        match_count++;
        match_elems += m->elems();

        _est_len = match_len - adjust_len
                   + (2*(_surround_len)+MIN_CONTINUATION)*match_count;
        if (_est_len >= (int)_min_length
            && match_count >= _max_matches)
            break;
    }
    LOG(spam, "QHL: %d matches, raw len %d, estimated len %d, elements %d",
        match_count, match_len, _est_len, match_elems);

    // Quick estimate of the query word length
    _hit_len = 5*match_elems;
    return match_elems;
}


/** Check if a character is a configured connector character
 */
bool SummaryDesc::word_connector(const unsigned char* s)
{
    unsigned char c = *s;
    if (c & 0x80) {
        ucs4_t u = Fast_UnicodeUtil::GetUTF8Char(s);
        return (u <= 255 ? _sumconf->connector(u) : false);
    }
    return _sumconf->connector(c);
}


/* Move backwards/forwards from ptr (no longer than to start) in an
 * UTF8 text until the beginning of an extended token or (if space,
 * until beginning of the next/last word)
 * A token in this function means some combination of words linked
 * together with a single character of any of the configured set of
 * legal connector characters.
 * @return The number of bytes moved
 */
int SummaryDesc::complete_extended_token(unsigned char* start, ssize_t length,
        const unsigned char*& ptr, off_t increment)
{
    int moved = 0;
    const unsigned char *old_ptr = NULL;
    for (;;) {
        // Start by moving to the start/end of the word..
        moved += complete_word(start, length, ptr, increment);

        // Ensure that there is a quick way out of this at the end:
        if (start >= ptr || start + length <= ptr || ptr == old_ptr)
            return moved;

        // If we end up at the same place as last iteration, we need
        // to bail (done above) to avoid an infinite loop.
        old_ptr = ptr;

        // Store a pointer to the found break:
        const unsigned char* preptr = ptr;

        int prelen;
        // Position to previous/next character to check if this is a
        // "real" break:
        if (increment < 0) {
            prelen = Fast_UnicodeUtil::UTF8move(start, length,
                                                preptr, increment);
            if (!prelen)
                return moved;
        } else {
            prelen = 0;
        }

        // Handle default case ("ordinary" space)
        if (!word_connector(preptr)) {
            LOG(spam, "Not a word connector case (%c)", printable_char(*preptr));
            return moved;
        }
        char wconn = *preptr;
        (void) wconn;
        LOG(spam, "Found word connector case candidate (%c)", printable_char(wconn));

        // Read the character before/after the connector character:
        int addlen = Fast_UnicodeUtil::UTF8move(start, length,
                                                preptr, increment);
        if (!addlen)
            return moved; // Not possible to extend anything here

        // Only a single connector character that connects word
        // characters should lead us to include more words in the
        // normal sense:
        if (!wordchar(preptr) &&
            !(increment > 0 && il_ann_anchor_char(preptr)) &&
            !(increment < 0 && il_ann_terminator_char(preptr))) {
            return moved;
        }

	// If a block of chinese data does not contain any spaces we have to return
	// here in order to avoid searching all the way to the start/end.
	return moved;

        // Ok, found a separator case, include another word..

        moved += prelen + addlen;
        // If going forward, the word completer will look at the
        // previous char to see if we are at the start of a word, so
        // we have to move forward once here:
        if (increment > 0) {
            addlen = Fast_UnicodeUtil::UTF8move(start, length,
                                                preptr, increment);
            if (!addlen)
                return moved;
            moved += addlen;
        }
        ptr = preptr;

        LOG(spam, "Found proper word connector case (%c,%c) yet moved %d",
            printable_char(wconn), printable_char(*preptr), moved);
    }
}



/* Return a highlight tagged summary string from this summary
 * description
 */
std::string SummaryDesc::get_summary(const char* buffer, size_t bytes,
                                     const SummaryConfig* sumconf,
                                     size_t& char_size)
{
    std::vector<char> s;
    ssize_t prev_end = 0;
    bool start_cont = false; // Set if this segment has been continued at the start

    LOG(debug, "start get_summary, substrings: %ld, est. length: %d",
        _plist.size(), _est_len);
    // Set the current summary config.  Implies that get_summary is
    // not MT safe wrt. this SummaryDesc (not a very heavy
    // restriction..)
    _sumconf = sumconf;

    juniper::Appender a(sumconf);

    int reserve_len = static_cast<int>(_est_len * 1.1);
    if (reserve_len)
        s.reserve(reserve_len);
    print_list::iterator it = _plist.begin();
    print_list::iterator nit = it;

    /** Add continuation dots if not at the start of doc and not empty
     * config */
    if (it != _plist.end() && (*it)._pos > 0) {
        start_cont = true;
        s.insert(s.end(), sumconf->dots().begin(), sumconf->dots().end());
    }

    /** Loop through all highlight_desc's in this SummaryDesc and
     *  build up the result string
     */
    for (; it != _plist.end(); ++it) {
        if (nit != _plist.end())
            ++nit;
        highlight_desc& d = *it;
        off_t next_pos = (nit == _plist.end() ? 0x7fffffff : (*nit)._pos);

        ssize_t len = d._len;
        off_t pos = d._pos;

        if (pos < prev_end) {
            // In spite of precautions keyword hits came so tight that
            // we got ourselves an overlap after all. Just skip
            // whatever needed..
            LOG(spam, "Overlap elim during string buildup: "
                "previous end %" PRId64 ", current pos %" PRId64,
                static_cast<uint64_t>(prev_end), static_cast<uint64_t>(pos));
            if (pos + len <= prev_end) {
                continue;
            } else {
                off_t adj_len = prev_end - pos;
                pos = prev_end;
                len -= adj_len;
            }
        }

        // Actual work on the string to present:

        if (prev_end > 0 && prev_end < pos) {
            start_cont = true;
            s.insert(s.end(), sumconf->dots().begin(), sumconf->dots().end());
        }
        if (d._highlight)
            s.insert(s.end(), sumconf->highlight_on().begin(), sumconf->highlight_on().end());

        /* Point to current startpoint to check for split
         * word/starting space tokens (only if previous segment is not
         * adjacent!)
         */
        const unsigned char* ptr =
            reinterpret_cast<const unsigned char*>(&buffer[pos]);
        if (!d._highlight && start_cont && prev_end < pos) {
            // Complete beginning word by extending the prefix
            unsigned char* b =
                reinterpret_cast<unsigned char*>(const_cast<char*>(buffer));
            int moved = complete_extended_token(b, bytes, ptr, -1);
            pos -= moved;
            len += moved;
        } else if (!d._highlight) {
            LOG(spam, "Not completing word at "
                "char %c/0x%x, prev_end %" PRId64 ", pos %" PRId64,
                printable_char(*ptr), *ptr, static_cast<int64_t>(prev_end), static_cast<int64_t>(pos));
        }

        /* Point to "current" endpos to check for split word/ending
         * space tokens but only in the cases where the next segment
         * is not adjacent.
         */
        ptr = reinterpret_cast<const unsigned char*>(&buffer[pos+len]);
        if (!d._highlight && next_pos > pos + len &&
            pos + len < static_cast<ssize_t>(bytes)) {
            int max_len = std::min(static_cast<off_t>(bytes), next_pos);
            // complete word at the end (these strings are either
            // ... in the start or the end or not at all, but overlap
            // is taken care of in the next loop..  Complete end of
            // word by appending at the end
            unsigned char* b =
                reinterpret_cast<unsigned char*>(const_cast<char*>(buffer));
            int moved = complete_extended_token(b, max_len, ptr, +1);
            len += moved;
            if ((pos + len) >= next_pos) {
                LOG(spam, "Word completion: no space char found - "
                    "joining at pos %" PRId64, static_cast<int64_t>(next_pos));
            }
        } else if (!d._highlight) {
            LOG(spam, "Not completing word at "
                "char %c/0x%x, next_pos %" PRId64,
                printable_char(*ptr), *ptr, static_cast<int64_t>(next_pos));
        }

        JD_INVAR(JD_DESC, len >= 0, len = 0,
                 LOG(error,
                     "get_summary: Invariant failed, len = %ld",
                     static_cast<long>(len)));
        int add_len = ((int)bytes > len ? len : bytes);

        LOG(spam, "bytes %zd pos %" PRId64 " len %" PRId64 " %s",
            bytes, static_cast<int64_t>(pos), static_cast<int64_t>(len), (d._highlight ? "(highlight)" : ""));

        a.append(s, &buffer[pos], add_len);
        len -= add_len;
        pos += add_len;

        if (d._highlight) {
            s.insert(s.end(), sumconf->highlight_off().begin(), sumconf->highlight_off().end());
        }
        prev_end = pos + len;
    }
    if (s.size() > 0 && prev_end < (int)_document_length)
        s.insert(s.end(), sumconf->dots().begin(), sumconf->dots().end());
    LOG(debug, "get_summary: Length of summary %ld bytes %ld chars",
               s.size(), a.charLen());
    _sumconf = NULL; // Not valid after this call.
    char_size = a.charLen();
    return std::string(&s[0], s.size());
}


bool SummaryDesc::overlap(MatchCandidate* m)
{
    // Walk through previous matches - exit if overlap
    for (cand_list::iterator it = _clist.begin();
         it != _clist.end();
         ++it)
    {
        MatchCandidate *m1, *m2;

        if ((*it)->starttoken() < m->starttoken()) {
            m1 = *it;
            m2 = m;
        } else {
            m2 = *it;
            m1 = m;
        }
        if (m1->endpos() > m2->starttoken()) {
            LOG(spam, "overlap: [%" PRId64 ", %" PRId64 "] <-> [%" PRId64 ", %" PRId64 "]",
                static_cast<int64_t>(m->starttoken()), static_cast<int64_t>(m->endpos()),
                static_cast<int64_t>((*it)->starttoken()), static_cast<int64_t>((*it)->endpos()));
            return true;
        }
    }
    return false;
}


int SummaryDesc::recompute_estimate(int len_per_elem)
{
    int new_est = 0;
    int affected_segments = 0;
    _hit_len = 0;

    cand_list::iterator cit = _clist.begin();
    /* prefix */
    assert(cit != _clist.end());

    bool prefix = true;
    MatchCandidate* m = *cit;
    off_t prev_pos = m->ctxt_startpos();

    for (; cit != _clist.end(); ++cit) {
        /* look at each keyword within match */
        m = *cit;
        keylist& klist = (*cit)->_klist;
        for (keylist::iterator kit = klist.begin();
             kit != klist.end();
             ++kit)
        {
            int seglen = (*kit)->startpos() - prev_pos;
            if (seglen <= 0) {
                LOG(spam, "recompute_estimate: Skipped additional match "
                    "at pos %" PRId64,
                    static_cast<int64_t>((*kit)->startpos()));
                continue; // skip multiple matches of same occurrence
            }
            _hit_len += (*kit)->tokenlen;
            if (prefix) {
                // Only fit one elem at start
                if (len_per_elem < seglen) {
                    affected_segments++;
                    LOG(spam, "recompute_estimate prefix "
                        "(dist %d): len %d (affected)",
                        seglen, len_per_elem);
                    seglen = len_per_elem;
                } else {
                    LOG(spam, "recompute_estimate: prefix len %d",
                        seglen);
                }
                prefix = false;
            } else if ((len_per_elem << 1) < seglen) {
                affected_segments +=2;
                LOG(spam, "recompute_estimate(dist %d): "
                    "len %d (affected*2)",
                    seglen, len_per_elem*2 + MIN_CONTINUATION);
                seglen = len_per_elem * 2 + MIN_CONTINUATION;
            } else {
                LOG(spam, "recompute_estimate: mid len %d",
                    seglen);
            }
            new_est += seglen;
            prev_pos = (*kit)->startpos() + (*kit)->tokenlen;
        }
    }

    /* postfix */
    int xlen = _matcher->DocumentSize() - m->endpos();
    if (xlen < len_per_elem) {
        new_est += xlen;
        LOG(spam, "recompute_estimate: end len %d", xlen);
    } else {
        affected_segments++;
        LOG(spam, "recompute_estimate: end len %d (affected)",
            len_per_elem);
        new_est += len_per_elem;
    }

    LOG(spam, "recompute_estimate(%d): %d -> %d, affected %d",
        len_per_elem, _est_len, new_est, affected_segments);
    _est_len = new_est;

    /* Re-set available print length per element (prefix or postfix) */
    len_per_elem = (_length - _hit_len) / (_match_elems*2);

    // Adjust element length to sensible values
    len_per_elem = std::max(MIN_SURROUND_LEN, len_per_elem);

    LOG(spam, "recompute_estimate --> %d", len_per_elem);

    if (affected_segments > 0 && _length > _est_len + MIN_SURROUND_LEN) {
        int adj = (_length  - _hit_len
                   - (_est_len + MIN_SURROUND_LEN)) / affected_segments;

        // Again re-adjust element length to sensible values
        if (len_per_elem + adj < MIN_SURROUND_LEN) {
            LOG(spam, "recompute_estimate(%d) "
                "(below MIN_SURROUND_LEN threshold)",
                len_per_elem);
            adj = (MIN_SURROUND_LEN - len_per_elem);
            len_per_elem = MIN_SURROUND_LEN;
        } else {
            len_per_elem += adj;
        }
        _est_len += adj * affected_segments;
        LOG(spam, "recompute_estimate (adj %d) el.len %d new est_len %d",
            adj, len_per_elem, _est_len);
    }
    return len_per_elem;
}

void SummaryDesc::build_highlight_descs()
{
    /* Set available print length per element (prefix or postfix) */
    int len_per_elem;

    if (_est_len > (int)_length) {
        len_per_elem = (_length - _hit_len) / (_match_elems*2);

        // Adjust element length to sensible values
        len_per_elem = std::max(MIN_SURROUND_LEN, len_per_elem);

        /* Check that this does not yield a too long/too short teaser */
        len_per_elem = recompute_estimate(len_per_elem);
    } else {
        len_per_elem = _surround_len;
    }

    // Max length to allow before a split is required: Note that we
    // allow an extra MIN_CONTINUATION extra bytes to the total those
    // times where matches are close
    int middle_len = len_per_elem * 2 + MIN_CONTINUATION;

    int len = len_per_elem; // Max running length to update pointer with

    LOG(spam, "length pr. elem %d", len_per_elem);

    /* build the ordered highlight description list (stored in _plist)
     * based on our collected info about the best matches available
     * and the estimated length (len_per_elem) of a triple
     * (pre-context, highlight keyword, post-context) (len_per_elem
     * assumes no overlap). Identify a line segment at a time..
     */

    off_t pos  = 0;
    off_t startpos = 0;

    for (cand_list::iterator cit = _clist.begin();
         cit != _clist.end();
         ++cit)
    {
        /* look at each keyword within match */
        keylist& klist = (*cit)->_klist;

        for (keylist::iterator kit = klist.begin();
             kit != klist.end();
             ++kit)
        {
            key_occ* k = *kit;
            int max_len = k->startpos() - pos;
            // the same occurrence may appear twice in a match, in
            // which case length will be < 0
            if (max_len < 0)
                continue;

            if (pos == 0) {
                // Adding initial segment:
                if (len < max_len) {
                    startpos = pos = max_len - len;
                } else {
                    len = max_len;
                }
                add_desc(pos, len, false);
            } else if (max_len <= middle_len) {
                // Context in between fits completely
                len = max_len;
                add_desc(pos, len, false);
            } else {
                if (LOG_WOULD_LOG(spam)) {
                    int dist = (k->startpos() - len_per_elem) - (pos + len_per_elem);
                    LOG(spam, "Middle split case, distance: %d", dist);
                }
                len = max_len;
                add_desc(pos, len_per_elem, false);
                add_desc(k->startpos() - len_per_elem, len_per_elem, false);
            }
            // Finally add the keyword itself:
            add_desc(k->startpos(), k->tokenlen, true);
            pos += (k->tokenlen + len);
        }
    }

    if (pos > 0) {
        // Adding final segment, ensure that there is enough text available..
        int max_len = std::min(len_per_elem,
                               static_cast<int>(_matcher->DocumentSize() - pos));
        add_desc(pos, max_len, false);
    }
    LOG(debug, "Summary: start %" PRId64 " end: %" PRId64, static_cast<int64_t>(startpos), static_cast<int64_t>(pos));
}


/* create description for the complete document */

void SummaryDesc::build_fulldoc_desc()
{
    LOG(debug, "Generating query highlights for complete document");
    off_t pos = 0;
    for (key_occ_vector::const_iterator kit = _occ.begin();
         kit != _occ.end(); ++kit)
    {
        int klen = (*kit)->tokenlen;
        int kpos = (*kit)->startpos();
        add_desc(pos, kpos - pos, false);
        // Use valid() info to filter out non-phrase terms if this is
        // a phrase search:
        add_desc(kpos, klen, (!_matcher->UsesValid()) || (*kit)->valid());
        pos = kpos + klen;
    }
    add_desc(pos, _matcher->DocumentSize() - pos, false);
    _est_len = _matcher->DocumentSize();
}


void SummaryDesc::add_desc(off_t pos, ssize_t len, bool highlight)
{
    if (len == 0)
        return;
    JD_INVAR(JD_DUMP, len > 0, return,
             LOG(info, "add_desc len %ld, %s", static_cast<long>(len),
                 (highlight ? "highlight" : "")); assert(false));
    _plist.push_back(highlight_desc(pos, len, highlight));
}
