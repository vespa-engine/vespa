// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    vectorizer.h
 * @brief   Simple document vectorizer based on %FSA (%Finite %State %Automaton)
 */

#pragma once

#include <string>
#include <map>
#include <vector>

#include "fsa.h"
#include "detector.h"

namespace fsa {

// {{{ Vectorizer

/**
 * @class Vectorizer
 * @brief Simple document vectorizer based on %FSA.
 */
class Vectorizer {

public:

  // {{{ Vectorizer::VectorItem

  /**
   * @class VectorItem
   * @brief Document vector item.
   *
   * Document vector item. Contains a term/phrase and an assigned
   * weight, and provides comparison operators for sorting.
   */
  class VectorItem {
  public:
    typedef std::pair<unsigned int /*position*/, int /*length*/> Hit;
    typedef std::vector<Hit> Hits;
  private:
    std::string   _term;     /**< Term/phrase. */
    double        _weight;   /**< Term weight. */
    Hits          _hits;     /**< The token positions at which the term was found */
  public:
    /**
     * @brief Default constructor, creates empty item with zero weight.
     */
    VectorItem() noexcept : _term(), _weight(0.0), _hits() {}

    /**
     * @brief Copy constructor.
     *
     * @param v VectorItem to copy.
     */
    VectorItem(const VectorItem &v) : _term(v._term), _weight(v._weight), _hits(v._hits) {}

    /**
     * @brief Constructor.
     *
     * Creates a vector item from a string and a weight.
     *
     * @param t Term/phrase.
     * @param w Weight.
     */
    VectorItem(const std::string t, double w) : _term(t), _weight(w), _hits() {}

    /**
     * @brief Constructor.
     *
     * Creates a vector item from a string and a weight.
     *
     * @param t Term/phrase.
     * @param w Weight.
     */
    VectorItem(const std::string t, double w, const Hits &h) : _term(t), _weight(w), _hits(h) {}

    /**
     * @brief Destructor.
     */
    ~VectorItem() {}

    /**
     * @brief Assignment operator.
     *
     * @param v VectorItem.
     * @return Reference to (this) VectorItem.
     */
    const VectorItem& operator=(const VectorItem& v)
    {
      _term = v._term;
      _weight = v._weight;
      _hits = v._hits;
      return *this;
    }

    /**
     * @brief Less-than operator.
     *
     * The order is highest weight first, than sorted alphabetically.
     *
     * @param v Other vector item.
     * @return True is this item<other item.
     */
    bool operator<(const VectorItem & v) const
    {
      if(_weight>v._weight) return true;
      if(_weight<v._weight) return false;
      if(_term<v._term) return true;
      return false;
    }

    /**
     * @brief Greater-than operator.
     *
     * The order is highest weight first, than sorted alphabetically.
     *
     * @param v Other vector item.
     * @return True is this item>other item.
     */
    bool operator>(const VectorItem & v) const
    {
      if(_weight<v._weight) return true;
      if(_weight>v._weight) return false;
      if(_term>v._term) return true;
      return false;
    }

    /**
     * @brief Equals operator.
     *
     * Two VectorItems equal if both the terms and weight are equal.
     *
     * @param v Other vector item.
     * @return True is this item==other item.
     */
    bool operator==(const VectorItem & v) const
    {
      if(_weight==v._weight && _term==v._term) return true;
      return false;
    }

    /**
     * @brief Get the term/phrase.
     *
     * @return (Copy of) term/phrase.
     */
    std::string term() const { return _term; }

    /**
     * @brief An obsolete alias for term().
     *
     * @return (Copy of) term/phrase.
     */
    std::string getTerm() const { return _term; }

    /**
     * @brief Get the weight.
     *
     * @return Weight.
     */
    double weight() const { return _weight; }

    /**
     * @brief An obsolete alias for weight().
     *
     * @return Weight.
     */
    double getWeight() const { return _weight; }

    /**
     * @brief Get the hits.
     *
     * @return A reference to the hits vector.
     */
    const Hits &hits() const { return _hits; }

  };

  // }}}

  // {{{ Vectorizer::TfIdf

  /**
   * @class TfIdf
   * @brief Class for computing TfIdf weights.
   *
   * Class for computing TfIdf (term frequency/inverse document
   * frequency) weights.
   */
  class TfIdf {
  private:
    unsigned int _tf;   /**< Term frequency.               */
    unsigned int _idf;  /**< (Inverse) document frequency. */
  public:
    /**
     * @brief Default constructor.
     */
    TfIdf() : _tf(0), _idf(0) {}

    /**
     * @brief Copy constructor.
     *
     * @param ti TfIdf object to copy.
     */
    TfIdf(const TfIdf &ti) : _tf(ti._tf), _idf(ti._idf) {}

    /**
     * @brief Constructor.
     *
     * @param t Term frequency.
     * @param i (Inverse) document frequency.
     */
    TfIdf(unsigned int t, unsigned int i) : _tf(t), _idf(i) {}

    /**
     * @brief Destructor.
     */
    ~TfIdf() {}

    /**
     * @brief Assignment operator.
     *
     * @param ti Reference to TfIdf object.
     * @return Reference to (this) TfIdf object.
     */
    const TfIdf& operator=(const TfIdf& ti)
    {
      _tf = ti._tf;
      _idf = ti._idf;
      return *this;
    }

    /**
     * @brief Assignment operator, set only Tf.
     *
     * @param t Term frequency.
     * @return Reference to (this) TfIdf object.
     */
    const TfIdf& operator=(unsigned int t)
    {
      _tf = t;
      return *this;
    }

    /**
     * @brief Prefix increment operator.
     *
     * Prefix increment operator, increments Tf.
     *
     * @return Reference to (this) TfIdf object.
     */
    TfIdf& operator++()
    {
      ++_tf;
      return *this;
    }

    /**
     * @brief += operator.
     *
     * += operator, adds the parameter to Tf.
     *
     * @return Reference to (this) TfIdf object.
     */
    const TfIdf& operator+=(unsigned int t)
    {
      _tf+=t;
      return *this;
    }

    /**
     * @brief Get Tf value.
     *
     * @return Tf (term frequency) value.
     */
    unsigned int tf() const { return _tf; }

    /**
     * @brief An obsolete alias for tf().
     *
     * @return Tf (term frequency) value.
     */
    unsigned int getTf() const { return _tf; }

    /**
     * @brief Get Idf value.
     *
     * @return Idf ((inverse) document frequency) value.
     */
    unsigned int idf() const { return _idf; }

    /**
     * @brief An obsolete alias for idf().
     *
     * @return Idf ((inverse) document frequency) value.
     */
    unsigned int getIdf() const { return _idf; }

    /**
     * @brief Compute the weight from the Tf and Idf values.
     *
     * @param tfnorm Normalize Tf (divide by tfnorm).
     * @param idfnorm Normalize Idf (divide by idfnorm).
     * @param tfexp Tf exponent.
     * @param idfexp Idf exponent.
     * @return Weight based on Tf and Idf values.
     */
    double weight(unsigned int tfnorm=1, unsigned int idfnorm=1,
                  double tfexp=1.0, double idfexp=1.0) const;

    /**
     * @brief An obsolete alias for weight().
     *
     * @param tfnorm Normalize Tf (divide by tfnorm).
     * @param idfnorm Normalize Idf (divide by idfnorm).
     * @param tfexp Tf exponent.
     * @param idfexp Idf exponent.
     * @return Weight based on Tf and Idf values.
     */
    double getWeight(unsigned int tfnorm=1, unsigned int idfnorm=1,
                     double tfexp=1.0, double idfexp=1.0) const
    {
      return weight(tfnorm,idfnorm,tfexp,idfexp);
    }

  };

  // }}}

  /**
   * @brief Term vector type.
   */
  typedef std::vector<VectorItem>    TermVector;


private:

  // {{{ Vectorizer::RawVector

  /**
   * @class RawVector
   * @brief Class for building a raw document vector.
   *
   * The RawVector class is a subclass of Detector::Hits, so it can be
   * used directly with a Detector. The recognized terms and phrases
   * will be collected and counted (->term frequency). Idf counts are
   * obtained from the automaton the first time the term is
   * encountered.
   */
  class RawVector : public Detector::Hits {

  public:

    typedef std::map<std::string, std::pair<TfIdf, VectorItem::Hits> > ItemMap;

    // {{{ Vectorizer::RawVector::iterator

    /**
     * @class iterator
     * @brief Iterator for the RawVector class.
     *
     * This class is actually a wrapper around an
     * std::map<std::string,TfIdf>::iterator.
     */
    class iterator {
      friend class RawVector;
    private:

      /**
       * @brief The real (std::map<>) iterator.
       */
      ItemMap::iterator _mi;

      /**
       * @brief Constructor.
       *
       * @param mi A real (std::map<>) iterator.
       */
      iterator(ItemMap::iterator mi) : _mi(mi) {}

    public:

      /**
       * @brief Default constructor.
       */
      iterator() : _mi() {}

      /**
       * @brief Copy constructor.
       *
       * @param it Reference to a Vectorizer::RawVector::iterator
       *        object.
       */
      iterator(const iterator &it) : _mi(it._mi) {}

      /**
       * @brief Constructor.
       *
       * Initialize the iterator to the beginning of a RawVector
       * object.
       *
       * @param rv Reference to a Vectorizer::RawVector object, the
       *           iterator will be initalized to rv.begin().
       */
      iterator(RawVector &rv) : _mi(rv._item_map.begin()) { }

      /**
       * @brief Assignment operator.
       *
       * @param it Reference to another iterator.
       * @return Reference to this iterator.
       */
      iterator& operator=(const iterator &it) { _mi=it._mi; return *this; }

      /**
       * @brief Not equals operator.
       *
       * @param it Reference to another iterator.
       * @return True if the two iterators point to different elements.
       */
      bool operator!=(const iterator &it) const { return _mi!=it._mi; }

      /**
       * @brief Prefix increment operator.
       *
       * @return Reference to the (incremented) iterator.
       */
      iterator& operator++() { ++_mi; return *this; }

      /**
       * @brief Dereference operator
       *
       * @return Reference to the actual pair the iterator refers to.
       */
      ItemMap::value_type& operator*() { return _mi.operator*(); }

      /**
       * @brief Dereference operator
       *
       * @return Pointer to the actual pair the iterator refers to.
       */
      ItemMap::value_type* operator->() { return _mi.operator->(); }
    };

    // }}}

  private:

    /**
     * @brief Flag for controlling whether or not the detector will
     * save hit position information.
     */
    bool _save_positions;

    /**
     * @brief The map holding the detected terms/phrases.
     */
    ItemMap _item_map;

  public:

    /**
     * @brief Default constructor.
     */
    RawVector(bool save_positions = false) : _save_positions(save_positions), _item_map() {}

    /**
     * @brief Destructor.
     */
    ~RawVector() {}

    /**
     * @brief Clear all data structures.
     */
    void clear() { _item_map.clear(); }

    /**
     * @brief Register a term or phrase.
     *
     * This method will be called by the detector for each term or
     * recognized.
     *
     * @param text Input document (tokenized).
     * @param from Index of first token of the phrase.
     * @param length Length of the phrase.
     * @param state Reference to the final state of the automaton
     *              after recognition of the phrase.
     */
    void add(const NGram &text,
             unsigned int from, int length,
             const FSA::State &state) override
    {
      ItemMap::iterator pos;
      std::string str = text.join(" ",from,length);
      pos=_item_map.find(str);
      if(pos==_item_map.end()){
        pos=_item_map.insert(
          ItemMap::value_type(
            str,
            std::pair<TfIdf,VectorItem::Hits>(
              TfIdf(1,state.nData()),
              VectorItem::Hits()
            )
          )
        ).first;
      }
      else {
        ++(pos->second.first);
      }
      if(_save_positions){
        pos->second.second.push_back(VectorItem::Hit(from,length));
      }
    }

    /**
     * @brief Get the size of the vector.
     *
     * @return Size of the vector (number of items).
     */
    unsigned int size() const { return _item_map.size(); }

    /**
     * @brief Get an iterator to the beginning of the vector.
     *
     * @return Iterator pointing to the first item of the vector.
     */
    iterator begin() { return iterator(_item_map.begin()); }

    /**
     * @brief Get an iterator to the end of the vector.
     *
     * @return Iterator pointing beyond the last item of the vector.
     */
    iterator end() { return iterator(_item_map.end()); }

  };

  // }}}

  const FSA&    _dictionary; /**< The dictionary. */
  Detector      _detector;   /**< The detector.   */
  unsigned int  _idf_docs;   /**< Total number of documents (for Idf calculations) */

  /**
   * @brief Retrieve total number of documents from the automaton.
   *
   * Retrieve total number of documents from the automaton. For the
   * Idf calculations to work properly, the total number of documents
   * needs to be stored in the automaton. This is done via a special
   * term, '#IDFDOCS', with a numerical meta info which equals the
   * total number of documents.
   */
  void initIdfCount()
  {
    _idf_docs=0;
    FSA::State s(_dictionary);
    if(s.start("#IDFDOCS"))
      _idf_docs = s.nData();

    if(!_idf_docs)
      ++_idf_docs;
  }

public:

  /**
   * @brief Constructor.
   *
   * Initialize the dictionary and the detector from an FSA.
   *
   * @param dict FSA
   */
  Vectorizer(const FSA& dict) :
    _dictionary(dict),
    _detector(_dictionary),
    _idf_docs(0)
  {
    initIdfCount();
  }

  /**
   * @brief Constructor.
   *
   * Initialize the dictionary and the detector from an FSA.
   *
   * @param dict FSA
   */
  Vectorizer(const FSA* dict) :
    _dictionary(*dict),
    _detector(_dictionary),
    _idf_docs(0)
  {
    initIdfCount();
  }

  /**
   * @brief Destructor.
   */
  ~Vectorizer() {}


  /**
   * @brief Vectorize a document.
   *
   * @param text Input document.
   * @param vector TermVector object to hold the document vector.
   * @param limit Limit the number of vector items.
   * @param keephits Include in the vector items the hit positions of terms.
   * @param tfexp Exponent for tf (term frequency).
   * @param idfexp Exponent for idf (inverse document frequency).
   */
  void vectorize(const NGram &text, TermVector &vector, unsigned int limit,
                 bool keephits, double tfexp = 1.0, double idfexp = 1.0) const;

  /**
   * @brief Vectorize a document.
   *
   * In this version of the call, hit positions are not kept.
   *
   * @param text Input document.
   * @param vector TermVector object to hold the document vector.
   * @param limit Limit the number of vector items (default=15).
   * @param tfexp Exponent for tf (term frequency).
   * @param idfexp Exponent for idf (inverse document frequency).
   */
  void vectorize(const NGram &text, TermVector &vector, unsigned int limit=15,
                 double tfexp = 1.0, double idfexp = 1.0) const;

};

// }}}

} // namespace fsa

