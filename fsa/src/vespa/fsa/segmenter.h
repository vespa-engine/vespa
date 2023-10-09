// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/13
 * @version $Id$
 * @file    segmenter.h
 * @brief   Query segmenter based on %FSA (%Finite %State %Automaton)
 *
 */

#pragma once

#include <string>
#include <map>
#include <vector>
#include <list>

#include <stdio.h>

#include "fsa.h"
#include "ngram.h"
#include "detector.h"


namespace fsa {

// {{{ class Segmenter

/**
 * @class Segmenter
 * @brief Query segmenter based on %FSA.
 */
class Segmenter {

public:

  // {{{ enum Segmenter::SegmentationMethod

  /**
   * @brief Enumerated type of supported segmentation method IDs
   *
   * The segmentation methods currently supported are the following:
   *   - SEGMENTATION_WEIGHTED - gives the segmentation where the sum
   *     of the scores of nontrivial (more than one word) segments is
   *     the highest
   *   - SEGMENTATION_WEIGHTED_BIASxx - (xx can be 10,20,50 or 100)
   *     gives the segmentation where the sum of the scores of
   *     nontrivial (more than one word) segments is the highest. The
   *     scores are biased based on segment length, xx% extra for each
   *     term over 2
   *   - SEGMENTATION_WEIGHTED_LEFTMOST - picks the segment with
   *     highest score first, if there are several possibilities, picks
   *     the leftmost, then repeats for the rest of the query
   *   - SEGMENTATION_WEIGHTED_RIGHTMOST - picks the segment with
   *     highest score first, if there are several possibilities, picks
   *     the rightmost, then repeats for the rest of the query
   *   - SEGMENTATION_WEIGHTED_LONGEST - picks the segment with
   *     highest score first, if there are several possibilities, picks
   *     the longest, then repeats for the rest of the query
   *   - SEGMENTATION_LEFTMOST_LONGEST - picks the leftmost segment
   *     first, if there are several possibilities, picks the longest,
   *     then repeats for the rest of the query
   *   - SEGMENTATION_LEFTMOST_WEIGHTED - picks the leftmost segment
   *     first, if there are several possibilities, picks the one with
   *     highest score, then repeats for the rest of the query
   *   - SEGMENTATION_RIGHTMOST_LONGEST - picks the rightmost segment
   *     first, if there are several possibilities, picks the longest,
   *     then repeats for the rest of the query
   *   - SEGMENTATION_RIGHTMOST_WEIGHTED - picks the rightmost segment
   *     first, if there are several possibilities, picks the one with
   *     highest score, then repeats for the rest of the query
   *   - SEGMENTATION_LONGEST_WEIGHTED - picks the longest segment
   *     first, if there are several possibilities, picks the one with
   *     highest score, then repeats for the rest of the query
   *   - SEGMENTATION_LONGEST_LEFTMOST - picks the longest segment
   *     first, if there are several possibilities, picks leftmost,
   *     then repeats for the rest of the query
   *   - SEGMENTATION_LONGEST_RIGHTMOST - picks the longest segment
   *     first, if there are several possibilities, picks the rightmost,
   *     then repeats for the rest of the query
   */
  enum SegmentationMethod {
    SEGMENTATION_WEIGHTED,
    SEGMENTATION_WEIGHTED_BIAS10,
    SEGMENTATION_WEIGHTED_BIAS20,
    SEGMENTATION_WEIGHTED_BIAS50,
    SEGMENTATION_WEIGHTED_BIAS100,
    SEGMENTATION_WEIGHTED_LEFTMOST,
    SEGMENTATION_WEIGHTED_RIGHTMOST,
    SEGMENTATION_WEIGHTED_LONGEST,
    SEGMENTATION_LEFTMOST_LONGEST,
    SEGMENTATION_LEFTMOST_WEIGHTED,
    SEGMENTATION_RIGHTMOST_LONGEST,
    SEGMENTATION_RIGHTMOST_WEIGHTED,
    SEGMENTATION_LONGEST_WEIGHTED,
    SEGMENTATION_LONGEST_LEFTMOST,
    SEGMENTATION_LONGEST_RIGHTMOST,
    SEGMENTATION_METHODS };

  // }}}

  // {{{ typedef Segmenter::Segmentation

  /** %Segmentation type */
  using Segmentation = std::list<int>;
  /** Iterator for %segmentation type */
  using SegmentationIterator = std::list<int>::iterator;
  /** Const iterator for %segmentation type */
  using SegmentationConstIterator = std::list<int>::const_iterator;

  // }}}

  // {{{ class Segmenter::Segments

  /**
   * @class Segments
   * @brief Class for storing segmentation results.
   *
   * Class for storing segmentation results. It is a subclass of
   * Detector::Hits, so it can be used directly by a Detector.
   */
  class Segments : public Detector::Hits {

  private:

    // {{{ class Segmenter::Segments::Segment

    /**
     * @class Segment
     * @brief Simple segment class.
     *
     * Simple segment class. A segment is defined by its beginning and
     * end, and it has a connexity. Beginning and end refer to indices
     * in the original text.
     */
    class Segment {

    private:
      unsigned int  _beg;    /**< Beginning of the segment. */
      unsigned int  _end;    /**< End of the segment. */
      unsigned int  _conn;   /**< Connexity of the segment. */

    public:

      /**
       * @brief Default constructor.
       *
       * Null segment at postion zero.
       */
      Segment() noexcept : _beg(0), _end(0), _conn(0) {}

      /**
       * @brief Constructor.
       *
       * @param b Beginning of the segment.
       * @param e End of the segment (the position after the last term).
       * @param c Connexity of the segment.
       */
      Segment(unsigned int b, unsigned int e, unsigned int c) noexcept :
        _beg(b), _end(e), _conn(c) {}

      /**
       * @brief Copy constructor.
       *
       * @param s Segment object to copy.
       */
      Segment(const Segment &s) noexcept : _beg(s._beg), _end(s._end), _conn(s._conn) {}

      /**
       * @brief Destructor.
       */
      ~Segment() = default;

      /**
       * @brief Set the segment parameters.
       *
       * @param b Beginning of the segment.
       * @param e End of the segment (the position after the last term).
       * @param c Connexity of the segment.
       */
      void set(unsigned int b, unsigned int e, unsigned int c)
      {
        _beg=b;
        _end=e;
        _conn=c;
      }

    public:
      /**
       * @brief Get the beginning of the segment.
       *
       * @return Beginning of the segment.
       */
      unsigned int beg()  const { return _beg; }

      /**
       * @brief Get the end of the segment.
       *
       * @return End of the segment. (Position after last term.)
       */
      unsigned int end()  const { return _end; }

      /**
       * @brief Get the length of the segment.
       *
       * @return Length of the segment (number of terms).
       */
      unsigned int len()  const { return _end-_beg; }

      /**
       * @brief Get the connexity of the segment.
       *
       * @return Connexity of the segment.
       */
      unsigned int conn() const { return _conn; }
    };

    // }}}

    // {{{ class Segmenter::Segments::SegmentMap

    /**
     * @class SegmentMap
     * @brief Class for mapping (beg,end) pairs to segment idx.
     */
    class SegmentMap {

    private:
      /** Size of current map. */
      unsigned int       _size;
      /** %Segment map */
      std::vector<int>   _map;

    public:
      /** Default constructor, creates empty map of zero size. */
      SegmentMap() : _size(0), _map() {}

      /**
       * @brief Constructor.
       *
       * Creates an empty map of given size.
       *
       * @param n Map size.
       */
      SegmentMap(unsigned int n) : _size(n+1), _map(_size*_size,-1) {}

      /** Destructor */
      ~SegmentMap() {}

      /**
       * @brief Initialize the map.
       *
       * Initialize the map to an empty map of given size.
       *
       * @param n Map size.
       */
      void init(unsigned int n)
      {
        _size = n+1;
        _map.assign(_size*_size,-1);
      }

      /**
       * @brief Clear the map.
       *
       * Reset the map to an empty map of zero size.
       */
      void clear()
      {
        _size = 0;
        _map.clear();
      }

      /**
       * @brief Get current map size.
       *
       * @return Map size.
       */
      unsigned int size() const { return _size; }

      /**
       * @brief Set an element in the map.
       *
       * @param i Beginning of the segment.
       * @param j End of the segment.
       * @param idx %Segment index.
       */
      void set(unsigned int i, unsigned int j, int idx)
      {
        if(i<_size && j<_size)
          _map[i*_size+j] = idx;
      }

      /**
       * @brief Get an element from the map.
       *
       * @param i Beginning of the segment.
       * @param j End of the segment.
       * @return %Segment index (-1 if segment does not exist).
       */
      int get(unsigned int i, unsigned int j) const
      {
        if(i<_size && j<_size)
          return _map[i*_size+j];
        return -1;
      }

      /**
       * @brief Check if a segment exists.
       *
       * @param i Beginning of the segment.
       * @param j End of the segment.
       * @return True if segment exists.
       */
      bool isValid(unsigned int i, unsigned int j) const
      {
        return i<_size && j<_size && _map[i*_size+j]!=-1;
      }
    };

    // }}}

  private:
    NGram                          _text;             /**< Tokenized text (e.g. query). */
    std::vector<Segment>           _segments;         /**< Detected segments.           */
    SegmentMap                     _map;              /**< Map of segments.             */
    std::vector<Segmentation*>     _segmentation;     /**< Pre-built segmentations.     */


    /**
     * @brief Insert all single term segments.
     *
     * Insert all single term segments as detected with zero
     * connexity. This is important for some of the segentation
     * algorithms.
     */
    void initSingles();

    /**
     * @brief Build a segmentation.
     *
     * @param method %Segmentation method.
     */
    void buildSegmentation(Segmenter::SegmentationMethod method);

    /**
     * @brief Build a segmentation recursively.
     *
     * Some of the segmentation methods are implemented
     * recursively.
     *
     * @param method %Segmentation method.
     * @param segmentation Segmentation object which holds results.
     * @param beg Beginning of the subquery to process.
     * @param end End the subquery to process.
     */
    void buildSegmentationRecursive(Segmenter::SegmentationMethod method,
                                    Segmentation& segmentation,
                                    unsigned int beg,
                                    unsigned int end);

  public:
    Segments();
    ~Segments();

    /**
     * @brief Set input text, and clear all results.
     *
     * @param text Input text.
     */
    void setText(const NGram &text)
    {
      _text.set(text);
      clear();
    }

    /**
     * @brief Set input text, and clear all results.
     *
     * @param text Input text.
     */
    void setText(const std::string &text)
    {
      _text.set(text);
      clear();
    }

    /**
     * @brief Set input text, and clear all results.
     *
     * @param text Input text.
     */
    void setText(const char *text)
    {
      _text.set(text);
      clear();
    }

    /**
     * @brief Get a reference to the input text.
     *
     * Get a reference to the input text. Valid as long as the
     * Segments object is valid and not modified.
     *
     * return Reference to input text.
     */
    const NGram& getText() const { return _text; }

    /**
     * @brief Clear all detected segments and built segmentations.
     */
    void clear();

    /**
     * @brief Insert a detected segment.
     *
     * This method will be called by the detector for each detected
     * segment.
     *
     * @param text Input text.
     * @param from Index of first token.
     * @param length Number of tokens.
     * @param state Final state after detected phrase.
     */
    void add(const NGram &text,
             unsigned int from, int length,
             const FSA::State &state) override
    {
      (void)text;
      unsigned int to=from+length;
      int id=_map.get(from,to);
      if(id==-1){
        _map.set(from,to,_segments.size());
        _segments.push_back(Segment(from,to,state.nData()));
      }
      else{
        _segments[id].set(from,to,state.nData());
      }
    }

    /**
     * @brief Get the size (number of segments).
     *
     * @return Number of segments.
     */
    unsigned int size() const { return _segments.size(); }

    /**
     * @brief Get a segment as a string.
     *
     * @param i %Segment index.
     * @return %Segment string.
     */
    const std::string operator[](unsigned int i) const { return sgm(i); }

    /**
     * @brief Get a segment as a string.
     *
     * @param i %Segment index.
     * @return %Segment string.
     */
    const std::string sgm(unsigned int i) const
    {
      if(i<_segments.size())
        return _text.join(" ",_segments[i].beg(),_segments[i].len());
      return std::string();
    }

    /**
     * @brief Get the beginning of a segment.
     *
     * @param i %Segment index.
     * @return Beginning of the segment.
     */
    unsigned beg(unsigned int i) const
    {
      if(i<_segments.size())
        return _segments[i].beg();
      return 0;
    }

    /**
     * @brief Get the end of a segment.
     *
     * @param i %Segment index.
     * @return End of the segment.
     */
    unsigned end(unsigned int i) const
    {
      if(i<_segments.size())
        return _segments[i].end();
      return 0;
    }

    /**
     * @brief Get the length of a segment.
     *
     * @param i %Segment index.
     * @return Length of the segment.
     */
    unsigned len(unsigned int i) const
    {
      if(i<_segments.size())
        return _segments[i].len();
      return 0;
    }

    /**
     * @brief Get the connexity of a segment.
     *
     * @param i %Segment index.
     * @return Connexity of the segment.
     */
    unsigned conn(unsigned int i) const
    {
      if(i<_segments.size())
        return _segments[i].conn();
      return 0;
    }

    /**
     * @brief Get the a segmentation of the query using the given method.
     *
     * @param method %Segmentation method
     * @return Pointer to the Segmentation object, valid as long as the
     *         Segments object is valid and not modified.
     */
    const Segmenter::Segmentation* segmentation(Segmenter::SegmentationMethod method)
    {
      if(method<SEGMENTATION_WEIGHTED || method>=SEGMENTATION_METHODS)
        method=SEGMENTATION_WEIGHTED;
      if(_segmentation[method]==NULL){
        buildSegmentation(method);
      }
      return _segmentation[method];
    }

  };

  // }}}


private:

  const FSA&    _dictionary;  /**< Dictionary. */
  Detector      _detector;    /**< Detector.   */

  /** Unimplemented private default constructor */
  Segmenter();
  /** Unimplemented private copy constructor */
  Segmenter(const Segmenter&);

public:

  /**
   * @brief Constructor.
   *
   * Create Segmeneter object and initialize dictionary and detector.
   *
   * @param dict Dictionary to use.
   */
  Segmenter(const FSA& dict) : _dictionary(dict), _detector(_dictionary) {}

  /**
   * @brief Constructor.
   *
   * Create Segmeneter object and initialize dictionary and detector.
   *
   * @param dict Dictionary to use.
   */
  Segmenter(const FSA* dict) : _dictionary(*dict), _detector(_dictionary) {}

  /** Destructor */
  ~Segmenter() {}

  /**
   * @brief %Segment a query.
   *
   * @param segments %Segments object, input text already initialized.
   */
  void segment(Segmenter::Segments &segments) const;

  /**
   * @brief %Segment a query.
   *
   * @param text Input text.
   * @param segments %Segments object to hold the results.
   */
  void segment(const NGram &text, Segmenter::Segments &segments) const;

  /**
   * @brief %Segment a query.
   *
   * @param text Input text.
   * @param segments %Segments object to hold the results.
   */
  void segment(const std::string &text, Segmenter::Segments &segments) const;

  /**
   * @brief %Segment a query.
   *
   * @param text Input text.
   * @param segments %Segments object to hold the results.
   */
  void segment(const char *text, Segmenter::Segments &segments) const;

  /**
   * @brief %Segment a query.
   *
   * @param text Input text.
   * @param segments %Segments object to hold the results.
   */
  void segment(const char *text, Segmenter::Segments *segments) const
  {
    segment(text,*segments);
  }

};

// }}}

} // namespace fsa

