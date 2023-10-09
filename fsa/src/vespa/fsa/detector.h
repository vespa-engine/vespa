// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    detector.h
 * @brief   %FSA (%Finite %State %Automaton) based detector.
 *
 */

#pragma once

#include <string>
#include <map>
#include <vector>

#include "fsa.h"
#include "ngram.h"

namespace fsa {

// {{{ Detector

/**
 * @class Detector
 * @brief Simple %FSA based detector.
 *
 * Class for processing a tokenized text and detecting occurrences of
 * terms and phrases in a given dictionary.
 */
class Detector {

public:

  // {{{ class Detector::Hits

  /**
   * @class Hits
   * @brief Class for collecting the detection results.
   *
   * This is a base class which must be subclassed for each particular
   * application of the detector. The method add() will be called for
   * each term/phrase detected by the detector.
   */
  class Hits {
  public:
    /** Default constructor. */
    Hits() {}
    /** Destructor. */
    virtual ~Hits() {};

    /**
     * @brief Method to receive results from the detector.
     *
     * @param text Tokenized detector input text.
     * @param from Index of the first term of the detected phrase.
     * @param length Length of the detected phrase.
     * @param state Final state after the detection of the phrase.
     */
    virtual void add(const NGram &text,
                     unsigned int from, int length,
                     const FSA::State &state) = 0;
  };

  // }}}

private:

  /** Dictionary. */
  const FSA& _dictionary;

  /** Unimplemented private default constructor. */
  Detector();
  /** Unimplemented private copy constructor. */
  Detector(const Detector&);

public:

  /**
   * @brief Constructor.
   *
   * Creates a detector, and initializes the dictionary from a handle.
   *
   * @param dict Dictionary handle.
   */
  Detector(const FSA& dict) : _dictionary(dict) {}

  /**
   * @brief Constructor.
   *
   * Creates a detector, and initializes the dictionary from a handle.
   *
   * @param dict Dictionary handle.
   */
  Detector(const FSA* dict) : _dictionary(*dict) {}

  /**
   * @brief Destructor.
   */
  ~Detector() {}

  /**
   * @brief Detect terms and phrases in a text.
   *
   * @param text Tokenized text.
   * @param hits Reference to the object for collecting the results.
   * @param from Index of first term in text where detection should start.
   * @param length Number of term to consider (-1 means to end of text).
   */
  void detect(const NGram &text, Hits &hits,
              unsigned int from=0, int length=-1) const;

  /**
   * @brief Detect terms and phrases in a text.
   *
   * Same as detect(), but uses hashed states.
   *
   * @param text Tokenized text.
   * @param hits Reference to the object for collecting the results.
   * @param from Index of first term in text where detection should start.
   * @param length Number of term to consider (-1 means to end of text).
   */
  void detectWithHash(const NGram &text, Hits &hits,
                      unsigned int from=0, int length=-1) const;

};

// }}}

} // namespace fsa

