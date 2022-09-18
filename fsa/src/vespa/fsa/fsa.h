// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    fsa.h
 * @brief   Class definition of the %FSA (%Finite %State %Automaton) matcher
 */

#pragma once

#include <string>
#include <list>
#include <iostream>
#include <inttypes.h>

#include "file.h" // for FileAccessMethod
#include "unaligned.h"

namespace fsa {

// {{{ symbol_t, state_t, hash_t, data_t
/**
 * @brief Symbol type used by the automaton. sizeof() should be 1.
 */
typedef uint8_t symbol_t;

/**
 * @brief State type used by the automaton.
 */
typedef uint32_t state_t;

/**
 * @brief Hash type used by the automaton.
 */
typedef uint32_t hash_t;

/**
 * @brief Data type used by the automaton. sizeof() should be 1.
 */
typedef uint8_t data_t;

// }}}


// {{{ FSA

/**
 * Forward declaration of friend.
 */
class Automaton;

/**
 * @class FSA
 * @brief %FSA (%Finite %State %Automaton) matcher.
 *
 * The FSA class provides very fast string lookup and perfect hashing
 * using the Finite State Automaton technology. The automata are built
 * off-line using the Automaton class.
 */
class FSA {

public:

  class Handle; // defined in fsahandle.h
  class State;

  // {{{ FSA::iterator
  /**
   * @class iterator
   * @brief Iterate through all accepted strings in the fsa.
   */
  class iterator {

    friend class State;

  public:

    /**
     * @class iteratorItem
     * @brief Helper class for storing iterator state and accessing data.
     *
     * Internally, this class stores the state information for the
     * iterator. Externally, it is used for accessing the data
     * associated with the iterator position.
     */
    class iteratorItem {

      friend class iterator;

    private:
      std::string         _string;   /**< The current string.             */
      std::list<state_t>  _stack;    /**< The stack of visited states.    */
      symbol_t            _symbol;   /**< Currently examined symbol.      */
      state_t             _state;    /**< Currently examined state.       */
      const FSA*          _fsa;      /**< Pointer to the FSA.             */

      /**
       * @brief Default constructor; unimplemented.
       */
      iteratorItem();

      /**
       * @brief Constructor.
       *
       * @param fsa Pointer to the %FSA object the iterator is associated with.
       */
      iteratorItem(const FSA *fsa) : _string(), _stack(), _symbol(0), _state(0), _fsa(fsa) {}

      /**
       * @brief Constructor.
       *
       * @param fsa Pointer to the %FSA object the iterator is associated with.
       * @param s State to use as start state.
       */
      iteratorItem(const FSA *fsa, state_t s) :
        _string(), _stack(), _symbol(0), _state(s), _fsa(fsa) {}

      /**
       * @brief Copy constructor.
       *
       * @param it Pointer to iterator item to copy.
       */
      iteratorItem(const iteratorItem& it) : _string(it._string), _stack(it._stack),
                                             _symbol(it._symbol), _state(it._state),
                                             _fsa(it._fsa) {}

      iteratorItem &operator=(const iteratorItem &rhs) = default;

      /**
       * @brief Destructor.
       */
      ~iteratorItem() {}

    public:

      /**
       * @brief Access the string associated with the iterator poristion.
       *
       * @return Current string.
       */
      const std::string& str() const { return _string; }

      /**
       * @brief Get the size of meta data which belongs to the current string.
       *
       * @return The size of meta data.
       */
      int          dataSize() const { return _fsa->dataSize(_state); }

      /**
       * @brief Get the meta data which belongs to the current string.
       *
       * @return Pointer to the meta data.
       */
      const data_t*      data() const { return _fsa->data(_state); }
    };

  private:

    iteratorItem _item;  /**< Internal state. */

    /**
     * @brief Constructor.
     *
     * Private constructor, reserved for FSA::State::begin() and end().
     *
     * @param fsa Pointer to the FSA object to assiociate with.
     * @param s State to use as initial state.
     */
    iterator(const FSA *fsa, state_t s) : _item(fsa,s)
    {
      if(!fsa->isFinal(s))
        operator++();
    }

  public:

    /**
     * @brief Default constructor.
     *
     * Creates an unitialized iterator. The effect of using any of
     * the access methods on unitialized iterators is undefined.
     */
    iterator() : _item(NULL) {}

    /**
     * @brief Copy constructor.
     *
     * @param it iterator object to copy.
     */
    iterator(const iterator &it) : _item(it._item) {}

    /**
     * @brief Constructor.
     *
     * Create an iterator for a given state s. The iterator will
     * only iterate through possible endings from this state.
     *
     * @param s State to create the iterator from.
     */
    iterator(const State &s) : _item(s._fsa,s._state)
    {
      if(!s.isFinal())
        operator++();
    }

    /**
     * @brief Constructor.
     *
     * Private constructor, reserved for FSA::begin() and end().
     *
     * @param fsa Pointer to the FSA object to assiociate with.
     * @param atEnd True for end(), false for begin(). (Default is false.)
     */
    iterator(const FSA *fsa, bool atEnd=false) : _item(fsa)
    {
      if(atEnd)
        _item._symbol = 0xff;
      else
        operator++();
    }

    /**
     * @brief Assignment operator.
     *
     * @param it iterator object to set values from.
     * @return Reference to this iterator object.
     */
    iterator& operator=(const iterator &it) { _item=it._item; return *this; }

    /**
     * @brief Not equal operator.
     *
     * @return True if the two iterators do not point to the same poistion.
     */
    bool operator!=(const iterator &it) const
    {
      return _item._fsa!=it._item._fsa || _item._symbol!=it._item._symbol ||
        _item._state!=it._item._state || _item._string!=it._item._string ||
        _item._stack!=it._item._stack;
    }

    /**
     * @brief Prefix increment operator.
     *
     * Prefix increment operator. Calling on an uninitalized iterator
     * (or one which has reached end()) has no effect.
     *
     * @return Reference to this.
     */
    iterator& operator++();

    /**
     * @brief Dereference operator.
     *
     * @return Const reference to state object for data access.
     */
    const iteratorItem& operator*() const { return _item; }

    /**
     * @brief Dereference operator.
     *
     * @return Const pointer to state object for data access.
     */
    const iteratorItem* operator->() const { return &_item; }

  };

  // }}}

  // {{{ FSA::State
  /**
   * @class State
   * @brief Class for FSA lookups.
   *
   * This class represents the state of a finite state automaton. It
   * is connected to one FSA for its whole lifetime. Provides methods
   * for transitions and lookups.
   */
  class State {

    friend FSA::iterator::iterator(const State &);

  private:
    /**
     * @brief Unimplemented private default constructor.
     */
    State();
    /**
     * @brief Unimplemented private assignment operator.
     */
    State& operator=(const State&);

  protected:
    const FSA    *_fsa;        /**< Pointer to the FSA. */
    state_t       _state;      /**< Current state.      */

  public:
    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.
     *
     * @param f Reference to FSA.
     */
    State(const FSA& f) noexcept : _fsa(&f), _state(_fsa->start()) {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.
     *
     * @param f Pointer to FSA.
     */
    State(const FSA* f) noexcept : _fsa(f), _state(_fsa->start()) {}

    /**
     * @brief Copy constructor.
     *
     * Duplicate an existing state. The new state will refer to the
     * same state of the automaton, but it can be used independently
     * (e.g. continue with different transitions).
     *
     * @param s Reference to state to be duplicated.
     */
    State(const State& s) noexcept : _fsa(s._fsa), _state(s._state) {}

    /**
     * @brief Destructor.
     *
     * Destructor, does nothing special.
     */
    virtual ~State() = default;

    /**
     * @brief Check if the automaton has perfect hash built in.
     *
     * Returns true if the automaton was built with a perfect hash included.
     *
     * @return True if the automaton has perfect hash.
     */
    virtual bool hasPerfectHash() const
    {
      return _fsa->hasPerfectHash();
    }

    /**
     * @brief Check is the state is valid.
     *
     * Returns true if the state is valid, that is the sequence of
     * transitions leading to this state exists in the automaton.
     *
     * @return True if the state is valid.
     */
    virtual bool isValid() const
    {
      return _state>0;
    }

    /**
     * @brief Set the state to the start state of the automaton.
     *
     * @return True if the resulting state is valid.
     */
    virtual bool start()
    {
      _state = _fsa->start();
      return _state!=0;
    }

    /**
     * @brief Delta transition.
     *
     * Perform a delta transition using a single input symbol.
     *
     * @param  in     Input symbol.
     * @return        True if the resulting state is valid.
     */
    virtual bool delta(symbol_t in)
    {
      _state = _fsa->delta(_state,in);
      return _state!=0;
    }

    /**
     * @brief Try a delta transition.
     *
     * Try if a delta transition would succeed, without performing the
     * transition.
     *
     * @param  in     Input symbol.
     * @return        True if the delta transition would succeed.
     */
    virtual bool tryDelta(symbol_t in)
    {
      return _fsa->delta(_state,in)!=0;
    }

    /**
     * @brief Start and transition.
     *
     * Sets the state to the starting state of the automaton, and
     * performs a transition using a single input symbol.
     *
     * @param  in     Input symbol.
     * @return        True if the resulting state is valid.
     */
    virtual bool start(symbol_t in)
    {
      start();
      return delta(in);
    }

    /**
     * @brief Start and transition.
     *
     * Sets the state to the starting state of the automaton, and
     * performs a transition using a sequence of input symbols.
     *
     * @param  in     Input symbols, zero terminated.
     * @return        True if the resulting state is valid.
     */
    virtual bool start(const symbol_t *in)
    {
      start();
      return delta(in);
    }

    /**
     * @brief Start and transition.
     *
     * Sets the state to the starting state of the automaton, and
     * performs a transition using a sequence of input symbols.
     *
     * @param  in     Input symbols, zero terminated.
     * @return        True if the resulting state is valid.
     */
    virtual bool start(const char *in)
    {
      start();
      return delta(in);
    }

    /**
     * @brief Start and transition.
     *
     * Sets the state to the starting state of the automaton, and
     * performs a transition using a sequence of input symbols.
     *
     * @param  in     Input symbols.
     * @return        True if the resulting state is valid.
     */
    virtual bool start(const std::string &in)
    {
      start();
      return delta(in);
    }

    /**
     * @brief Start and transition.
     *
     * Sets the state to the starting state of the automaton, and
     * performs a transition using an input word.
     *
     * @param  in     Input word.
     * @return        True if the resulting state is valid.
     */
    virtual bool startWord(const std::string &in)
    {
      start();
      return delta(in);
    }

    /**
     * @brief Delta transition.
     *
     * Perform a delta transition using a sequence of input symbols.
     *
     * @param  in     Input symbols, zero terminated.
     * @return        True if the resulting state is valid.
     */
    virtual bool delta(const symbol_t *in)
    {
      const symbol_t *p=in;

      while(*p && _state>0){
        delta(*p);
        p++;
      }
      return _state!=0;
    }

    /**
     * @brief Delta transition.
     *
     * Perform a delta transition using a sequence of input symbols.
     *
     * @param  in     Input symbols, zero terminated.
     * @return        True if the resulting state is valid.
     */
    virtual bool delta(const char *in)
    {
      return delta((const symbol_t *)in);
    }

    /**
     * @brief Delta transition.
     *
     * Perform a delta transition using a sequence of input symbols.
     *
     * @param  in     Input symbols.
     * @return        True if the resulting state is valid.
     */
    virtual bool delta(const std::string &in)
    {
      unsigned int idx=0;

      while(idx<in.length() && _state>0){
        delta(in[idx]);
        idx++;
      }
      return _state!=0;
    }

    /**
     * @brief Delta transition.
     *
     * Perform a delta transition using an input word. A word
     * separator symbol ` ` is inserted before the word if it is not
     * the first word (the current state is not the start state).
     *
     * @param  in     Input word.
     * @return        True if the resulting state is valid.
     */
    virtual bool deltaWord(const std::string &in)
    {
      if(_state!=_fsa->start())
        delta(' ');
      return delta(in);
    }

    /**
     * @brief Check if the current state is final (accepting) state.
     *
     * @return True if the state is final.
     */
    virtual bool isFinal(void) const
    {
      return _fsa->isFinal(_state);
    }

    /**
     * @brief Get the size of a data item.
     *
     * Get the size of the data item assiciated with a final
     * state. The return value -1 indicates that the current state is
     * not a final state.
     *
     * @return Size of data item, or -1 if the state is not final.
     */
    virtual int dataSize(void) const
    {
      return _fsa->dataSize(_state);
    }

    /**
     * @brief Get the data item.
     *
     * Get the data item assiciated with a final state. The return
     * value NULL indicates that the current state is not a final
     * state.
     *
     * @return Pointer to data item, or NULL if the state is not final.
     */
    virtual const data_t *data() const
    {
      return _fsa->data(_state);
    }

    /**
     * @brief Get the data item as a character string.
     *
     * Get the data item assiciated with a final state. The return
     * value NULL indicates that the current state is not a final
     * state.
     *
     * @return Pointer to data item, or NULL if the state is not final.
     */
    virtual const char *cData() const
    {
      return (const char*)(_fsa->data(_state));
    }

    /**
     * @brief Get the data item as an unsigned 32-bit integer.
     *
     * Get the data item assiciated with a final state as an unsigned
     * 32-bit integer. If the data field size is 0 or the state is not
     * final, zero returned, otherwise 1, 2 or 4 byte integer is
     * retrieved according to the size and converted to uint32_t.
     *
     * @return Numerical data.
     */
    virtual uint32_t nData() const
    {
      const data_t *da = _fsa->data(_state);
      int si = _fsa->dataSize(_state);
      if(si<=0)
        return 0;
      switch(si){
      case 1:
        return (uint32_t)((const uint8_t*)da)[0];
      case 2:
      case 3:
        return (uint32_t)Unaligned<uint16_t>::at(da).read();
      case 4:
      default:
        return Unaligned<uint32_t>::at(da).read();
      }
    }

    /**
     * @brief Dummy hash() method; for simple states returns only
     *        zero. Will be overridden by HashedState etc.
     *
     * @return 0
     */
    virtual hash_t hash() const
    {
      return 0;
    }


    /**
     * @brief Perform a lookup.
     *
     * Perform a string lookup in the automaton (sequence of
     * transitions, starting from the start state. Returns a pointer
     * to the data item associated with the final state if the string
     * is accepted, NULL otherwise.
     *
     * @param  in   Input string.
     * @return      Pointer to data item, or NULL if the state is not final.
     */
    virtual const data_t *lookup(const symbol_t *in)
    {
      start(in);
      return data();
    }

    /**
     * @brief Perform a lookup.
     *
     * Perform a string lookup in the automaton (sequence of
     * transitions, starting from the start state. Returns a pointer
     * to the data item associated with the final state if the string
     * is accepted, NULL otherwise.
     *
     * @param  in   Input string.
     * @return      Pointer to data item, or NULL if the state is not final.
     */
    virtual const data_t *lookup(const char *in)
    {
      return lookup((const symbol_t*)in);
    }

    /**
     * @brief Perform a lookup.
     *
     * Perform a string lookup in the automaton (sequence of
     * transitions, starting from the start state. Returns a pointer
     * to the data item associated with the final state if the string
     * is accepted, NULL otherwise.
     *
     * @param  in   Input string.
     * @return      Pointer to data item, or NULL if the state is not final.
     */
    virtual const data_t *lookup(const std::string &in)
    {
      start(in);
      return data();
    }

    /**
     * @brief Reverse lookup.
     *
     * For a given hash value, return the corresponding string.
     *
     * @param hash Hash value.
     * @return String corresponding to hash value, or empty string if
     *         the fsa has no perfect hash or the hash value is out of
     *         range.
     */
    virtual std::string revLookup(hash_t hash) const
    {
      return _fsa->revLookup(hash);
    }

    /**
     * @brief Get iterator pointing to the beginning of the fsa.
     *
     * @return iterator pointing to the first string in the fsa.
     */
    virtual FSA::iterator begin() const { return FSA::iterator(_fsa,_state); }

    /**
     * @brief Get iterator pointing past the end of the fsa.
     *
     * @return iterator pointing past the last string in the fsa.
     */
    virtual FSA::iterator end() const { return FSA::iterator(_fsa,true); }

  };

  // }}}

  // {{{ FSA::HashedState
  /**
   * @class HashedState
   * @brief Class for FSA lookups with perfect hash functionality.
   *
   * This class represents the state of a finite state automaton. It
   * is connected to one FSA for its whole lifetime. Provides all
   * methods of the FSA::State plus perfect hashing functionality.
   */
  class HashedState : public State {

  private:
    /**
     * @brief Unimplemented private default constructor.
     */
    HashedState();
    /**
     * @brief Unimplemented private assignment operator.
     */
    HashedState& operator=(const HashedState&);

  protected:
    hash_t  _hash;   /**< Hash value.  */

  public:
    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.
     *
     * @param f Reference to FSA.
     */
    HashedState(const FSA& f) : State(f), _hash(0) {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.
     *
     * @param f Pointer to FSA.
     */
    HashedState(const FSA* f) : State(f), _hash(0) {}

    /**
     * @brief Copy constructor.
     *
     * Duplicate an existing hashed state.
     *
     * @param s Reference to hashed state to copy.
     */
    HashedState(const HashedState& s) : State(s), _hash(s._hash) {}

    /**
     * @brief Destructor.
     */
    virtual ~HashedState() {}

    using State::start;
    using State::delta;

    /**
     * @brief Set the state to the starting state of the automaton.
     *
     * This method overrides the State::start() method, and resets the
     * hash value in addition.
     *
     * @return        True if the resulting state is valid.
     */
    bool start() override
    {
      _hash = 0;
      return State::start();
    }

    /**
     * @brief Delta transition for hashed states.
     *
     * Extends the State::delta() method with hash value update.
     *
     * @param  in     Input symbol.
     * @return        True if the resulting state is valid.
     */
    bool delta(symbol_t in) override
    {
      _hash += _fsa->hashDelta(_state,in);
      return State::delta(in);
    }

    /**
     * @brief Get current hash value.
     *
     * For final states, returns the perfect hash value for the input
     * string which lead to the the state. For any state (including
     * final states) the value equals the number of strings accepted
     * by the automaton which (in an alphabetical ordering) preceed
     * the string leading to the state.
     *
     * @return Hash value.
     */
    hash_t hash() const override
    {
      return _hash;
    }

    /**
     * @brief Obsolete alias for hash(), for backwards compatibility.
     *
     * @return Hash value.
     */
    virtual hash_t getHash() const
    {
      return _hash;
    }

  };

  // }}}

  // {{{ FSA::CounterState
  /**
   * @class CounterState
   * @brief Class for FSA lookups with counter.
   *
   * This class represents the state of a finite state automaton. It
   * is connected to one FSA for its whole lifetime. Provides all
   * methods of the FSA::State and counts the number of transtitions.
   */
  class CounterState : public State {

  private:
    /**
     * @brief Unimplemented private default constructor.
     */
    CounterState();
    /**
     * @brief Unimplemented private assignment operator.
     */
    CounterState& operator=(const CounterState&);

  protected:
    uint32_t  _counter;   /**< Counter value.  */

  public:
    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the counter.
     *
     * @param f Reference to FSA.
     */
    CounterState(const FSA& f) : State(f), _counter(0) {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the counter.
     *
     * @param f Pointer to FSA.
     */
    CounterState(const FSA* f) : State(f), _counter(0) {}

    /**
     * @brief Copy constructor.
     *
     * Duplicate an existing hashed state.
     *
     * @param s Reference to hashed state to copy.
     */
    CounterState(const CounterState& s) : State(s), _counter(s._counter) {}

    /**
     * @brief Destructor.
     */
    virtual ~CounterState() {}

    using State::start;
    using State::delta;

    /**
     * @brief Set the state to the starting state of the automaton.
     *
     * This method overrides the State::start() method, and resets the
     * counter in addition.
     *
     * @return        True if the resulting state is valid.
     */
    bool start() override
    {
      _counter = 0;
      return State::start();
    }

    /**
     * @brief Delta transition for counter states.
     *
     * Extends the State::delta() method with counter increment.
     *
     * @param  in     Input symbol.
     * @return        True if the resulting state is valid.
     */
    bool delta(symbol_t in) override
    {
      bool ok = State::delta(in);
      if(ok)
        ++_counter;     // only count valid transitions
      return ok;
    }

    /**
     * @brief Get current counter value.
     *
     * Return the current counter. The counter is the number of
     * transitions from the start state to the current state.
     * If the state is not valid anymore, the counter is the number of
     * transitions to the last valid state.
     *
     * @return Counter value.
     */
    virtual uint32_t counter() const
    {
      return _counter;
    }

    /**
     * @brief An alias for counter()
     *
     * @return Counter value.
     */
    virtual uint32_t getCounter() const
    {
      return _counter;
    }

  };
  // }}}

  // {{{ FSA::WordCounterState
  /**
   * @class WordCounterState
   * @brief Class for FSA lookups with word counter.
   *
   * This class is similar to CounterState, but it counts whole word
   * transitions. Operations other than start(void), startWord(const std::string&)
   * or deltaWord(const std::string&) will not modify the counter.
   */
  class WordCounterState : public State {

  private:
    /**
     * @brief Unimplemented private default constructor.
     */
    WordCounterState();
    /**
     * @brief Unimplemented private assignment operator.
     */
    WordCounterState& operator=(const WordCounterState&);

  protected:
    uint32_t  _counter;   /**< Counter value.  */

  public:
    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the counter.
     *
     * @param f Reference to FSA.
     */
    WordCounterState(const FSA& f) noexcept : State(f), _counter(0) {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the counter.
     *
     * @param f Pointer to FSA.
     */
    WordCounterState(const FSA* f) noexcept : State(f), _counter(0) {}

    /**
     * @brief Copy constructor.
     *
     * Duplicate an existing hashed state.
     *
     * @param s Reference to hashed state to copy.
     */
    WordCounterState(const WordCounterState& s) noexcept : State(s), _counter(s._counter) {}

    /**
     * @brief Destructor.
     */
    virtual ~WordCounterState() = default;

    /**
     * @brief Set the state to the starting state of the automaton.
     *
     * This method overrides the State::start() method, and resets the
     * counter in addition.
     *
     * @return        True if the resulting state is valid.
     */
    bool start() override
    {
      _counter = 0;
      return State::start();
    }

    /**
     * @brief Start and transition.
     *
     * Sets the state to the starting state of the automaton, and
     * performs a transition using an input word.
     *
     * @param  in     Input word.
     * @return        True if the resulting state is valid.
     */
    bool startWord(const std::string &in) override
    {
      start();
      return deltaWord(in);
    }

    /**
     * @brief Delta transition.
     *
     * Perform a delta transition using an input word. A word
     * separator symbol ` ` is inserted before the word if it is not
     * the first word (the current state is not the start state).
     *
     * @param  in     Input word.
     * @return        True if the resulting state is valid.
     */
    bool deltaWord(const std::string &in) override
    {
      if(in.length()==0){
        return _state!=0;
      }
      if(_state!=_fsa->start())
        delta(' ');
      bool ok = delta(in);
      if(ok)
        ++_counter;  // only count valid word transitions
      return ok;
    }

    /**
     * @brief Get current counter value.
     *
     * Return the current counter. The counter is the number of
     * word transitions from the start state to the current state.
     * If the state is not valid anymore, the counter is the number of
     * word transitions to the last valid state.
     *
     * @return Counter value.
     */
    virtual uint32_t counter() const
    {
      return _counter;
    }

    /**
     * @brief An alias for counter()
     *
     * @return Counter value.
     */
    virtual uint32_t getCounter() const
    {
      return _counter;
    }

  };
  // }}}

  // {{{ FSA::MemoryState
  /**
   * @class MemoryState
   * @brief Class for FSA lookups with memory functionality.
   *
   * This class represents the state of a finite state automaton. It
   * is connected to one FSA for its whole lifetime. Provides all
   * methods of the FSA::State and in addition it remebers the
   * sequence of symbols which led to this state.
   */
  class MemoryState : public State {

  private:
    /**
     * @brief Unimplemented private default constructor.
     */
    MemoryState();
    /**
     * @brief Unimplemented private assignment operator.
     */
    MemoryState& operator=(const MemoryState&);

  protected:
    std::string _memory;   /**< Memory value.  */

  public:
    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the memory value.
     *
     * @param f Reference to FSA.
     */
    MemoryState(const FSA& f) : State(f), _memory() {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the memory value.
     *
     * @param f Pointer to FSA.
     */
    MemoryState(const FSA* f) : State(f), _memory() {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the memory value.
     * Reserves space for the memory string.
     *
     * @param f Reference to FSA.
     * @param res Size to pre-reserve.
     */
    MemoryState(const FSA& f, unsigned int res) : State(f), _memory()
    {
      _memory.reserve(res);
    }

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the memory value.
     * Reserves space for the memory string.
     *
     * @param f Pointer to FSA.
     * @param res Size to pre-reserve.
     */
    MemoryState(const FSA* f, unsigned int res) : State(f), _memory()
    {
      _memory.reserve(res);
    }

    /**
     * @brief Copy constructor.
     *
     * Duplicate an existing memory state.
     *
     * @param s Reference to memory state to copy.
     */
    MemoryState(const MemoryState& s) : State(s), _memory(s._memory) {}

    /**
     * @brief Destructor.
     */
    virtual ~MemoryState() {}

    using State::start;
    using State::delta;

    /**
     * @brief Set the state to the starting state of the automaton.
     *
     * This method overrides the State::start() method, and resets the
     * memory in addition.
     *
     * @return        True if the resulting state is valid.
     */
    bool start() override
    {
      _memory.clear();
      return State::start();
    }

    /**
     * @brief Delta transition for memory states.
     *
     * Extends the State::delta() method with memory update.
     *
     * @param  in     Input symbol.
     * @return        True if the resulting state is valid.
     */
    bool delta(symbol_t in) override
    {
      bool ok = State::delta(in);
      if(ok)
        _memory += (char)in;
      return ok;
    }

    /**
     * @brief Get current memory value.
     *
     * The memory for a state stores the sequence of the
     * transitions which lead to the current state (or the last valid
     * state).
     *
     * @return Memory value.
     */
    virtual std::string memory() const
    {
      return _memory;
    }

    /**
     * @brief Alias for memory().
     *
     * @return Memory value.
     */
    virtual std::string getMemory() const
    {
      return _memory;
    }

  };

  // }}}

  // {{{ FSA::HashedMemoryState
  /**
   * @class HashedMemoryState
   * @brief Class for FSA lookups with perfect hash and memory functionality.
   *
   * This class represents the state of a finite state automaton. It
   * is connected to one FSA for its whole lifetime. Provides all
   * methods of the FSA::State plus perfect hashing functionality and
   * in addition it remebers the sequence of symbols which led to this
   * state.
   */
  class HashedMemoryState : public State {

  private:
    /**
     * @brief Unimplemented private default constructor.
     */
    HashedMemoryState();
    /**
     * @brief Unimplemented private assignment operator.
     */
    HashedMemoryState& operator=(const HashedMemoryState&);

  protected:
    hash_t        _hash;    /**< Hash value.   */
    std::string   _memory;  /**< Memory value. */

  public:
    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the memory value.
     *
     * @param f Reference to FSA.
     */
    HashedMemoryState(const FSA& f) : State(f), _hash(0), _memory() {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the memory value.
     *
     * @param f Pointer to FSA.
     */
    HashedMemoryState(const FSA* f) : State(f), _hash(0), _memory() {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the memory value.
     * Reserves space for the memory string.
     *
     * @param f Reference to FSA.
     * @param res Size to pre-reserve.
     */
    HashedMemoryState(const FSA& f, unsigned int res) : State(f), _hash(0), _memory()
    {
      _memory.reserve(res);
    }

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the memory value.
     * Reserves space for the memory string.
     *
     * @param f Pointer to FSA.
     * @param res Size to pre-reserve.
     */
    HashedMemoryState(const FSA* f, unsigned int res) : State(f), _hash(0), _memory()
    {
      _memory.reserve(res);
    }

    /**
     * @brief Copy constructor.
     *
     * Duplicate an existing hashed memory state.
     *
     * @param s Reference to hashed memory state to copy.
     */
    HashedMemoryState(const HashedMemoryState& s) : State(s),
                                                    _hash(s._hash),
                                                    _memory(s._memory) {}
    /**
     * @brief Destructor.
     */
    virtual ~HashedMemoryState() {}

    using State::start;
    using State::delta;

    /**
     * @brief Set the state to the starting state of the automaton.
     *
     * This method overrides the State::start() method, and resets the
     * hash and memory in addition.
     *
     * @return        True if the resulting state is valid.
     */
    bool start() override
    {
      _hash = 0;
      _memory.clear();
      return State::start();
    }

    /**
     * @brief Delta transition for memory states.
     *
     * Extends the State::delta() method with hash and memory update.
     *
     * @param  in     Input symbol.
     * @return        True if the resulting state is valid.
     */
    bool delta(symbol_t in) override
    {
      _hash += _fsa->hashDelta(_state,in);
      bool ok = State::delta(in);
      if(ok)
        _memory += (char)in;  // only remeber valid transitions
      return ok;
    }

    /**
     * @brief Get current hash value.
     *
     * For final states, returns the perfect hash value for the input
     * string which lead to the the state. For any state (including
     * final states) the value equals the number of strings accepted
     * by the automaton which (in an alphabetical ordering) preceed
     * the string leading to the state.
     *
     * @return Hash value.
     */
    hash_t hash() const override
    {
      return _hash;
    }

    /**
     * @brief Obsolete alias for hash(), for backwards compatibility.
     *
     * @return Hash value.
     */
    virtual hash_t getHash() const
    {
      return _hash;
    }

    /**
     * @brief Get current memory value.
     *
     * The memory for a state stores the sequence of the
     * transitions which lead to the current state (or the last valid
     * state).
     *
     * @return Memory value.
     */
    virtual std::string memory() const
    {
      return _memory;
    }

    /**
     * @brief Alias for memory().
     *
     * @return Memory value.
     */
    virtual std::string getMemory() const
    {
      return _memory;
    }

  };

  // }}}

  // {{{ FSA::HashedCounterState
  /**
   * @class HashedCounterState
   * @brief Class for FSA lookups with counter and hash.
   *
   * This class represents the state of a finite state automaton. It
   * is connected to one FSA for its whole lifetime. Provides all
   * methods of the FSA::State and counts the number of transtitions,
   * and computes hash value.
   */
  class HashedCounterState : public State {

  private:
    /**
     * @brief Unimplemented private default constructor.
     */
    HashedCounterState();
    /**
     * @brief Unimplemented private assignment operator.
     */
    HashedCounterState& operator=(const CounterState&);

  protected:
    hash_t      _hash;      /**< Hash value.   */
    uint32_t    _counter;   /**< Counter value.  */

  public:
    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the counter.
     *
     * @param f Reference to FSA.
     */
    HashedCounterState(const FSA& f) : State(f), _hash(0), _counter(0) {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the counter.
     *
     * @param f Pointer to FSA.
     */
    HashedCounterState(const FSA* f) : State(f), _hash(0), _counter(0) {}

    /**
     * @brief Copy constructor.
     *
     * Duplicate an existing hashed state.
     *
     * @param s Reference to hashed state to copy.
     */
    HashedCounterState(const HashedCounterState& s) : State(s), _hash(s._hash), _counter(s._counter) {}

    /**
     * @brief Destructor.
     */
    virtual ~HashedCounterState() {}

    using State::start;
    using State::delta;

    /**
     * @brief Set the state to the starting state of the automaton.
     *
     * This method overrides the State::start() method, and resets the
     * counter in addition.
     *
     * @return        True if the resulting state is valid.
     */
    bool start() override
    {
      _hash = 0;
      _counter = 0;
      return State::start();
    }

    /**
     * @brief Delta transition for hashed counter states.
     *
     * Extends the State::delta() method with counter increment and
     * hash update.
     *
     * @param  in     Input symbol.
     * @return        True if the resulting state is valid.
     */
    bool delta(symbol_t in) override
    {
      _hash += _fsa->hashDelta(_state,in);
      bool ok = State::delta(in);
      if(ok)
        ++_counter;     // only count valid transitions
      return ok;
    }

    /**
     * @brief Get current hash value.
     *
     * For final states, returns the perfect hash value for the input
     * string which lead to the the state. For any state (including
     * final states) the value equals the number of strings accepted
     * by the automaton which (in an alphabetical ordering) preceed
     * the string leading to the state.
     *
     * @return Hash value.
     */
    hash_t hash() const override
    {
      return _hash;
    }

    /**
     * @brief Obsolete alias for hash(), for backwards compatibility.
     *
     * @return Hash value.
     */
    virtual hash_t getHash() const
    {
      return _hash;
    }

    /**
     * @brief Get current counter value.
     *
     * Return the current counter. The counter is the number of
     * transitions from the start state to the current state.
     * If the state is not valid anymore, the counter is the number of
     * transitions to the last valid state.
     *
     * @return Counter value.
     */
    virtual uint32_t counter() const
    {
      return _counter;
    }

    /**
     * @brief An alias for counter()
     *
     * @return Counter value.
     */
    virtual uint32_t getCounter() const
    {
      return _counter;
    }

  };
  // }}}

  // {{{ FSA::HashedWordCounterState
  /**
   * @class HashedWordCounterState
   * @brief Class for FSA lookups with word counter and hash.
   *
   * This class is similar to CounterState, but it counts whole word
   * transitions. Operations other than start(void), startWord(const std::string&)
   * or deltaWord(const std::string&) will not modify the counter.
   */
  class HashedWordCounterState : public State {

  private:
    /**
     * @brief Unimplemented private default constructor.
     */
    HashedWordCounterState();
    /**
     * @brief Unimplemented private assignment operator.
     */
    HashedWordCounterState& operator=(const HashedWordCounterState&);

  protected:
    hash_t    _hash;      /**< Hash value.   */
    uint32_t  _counter;   /**< Counter value.  */


    using State::delta;

    /**
     * @brief Delta transition for hashed word counter states.
     *
     * Extends the State::delta() method with hash update. It is
     * protected so it is not accessible outside (only deltaWord is).
     *
     * @param  in     Input symbol.
     * @return        True if the resulting state is valid.
     */
    bool delta(symbol_t in) override
    {
      _hash += _fsa->hashDelta(_state,in);
      bool ok = State::delta(in);
      return ok;
    }

   public:
    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the counter.
     *
     * @param f Reference to FSA.
     */
    HashedWordCounterState(const FSA& f) noexcept : State(f), _hash(0), _counter(0) {}

    /**
     * @brief Constructor.
     *
     * Create a new state from an FSA, and set it to the starting
     * state of the automaton.  Also reset the counter.
     *
     * @param f Pointer to FSA.
     */
    HashedWordCounterState(const FSA* f) noexcept : State(f), _hash(0), _counter(0) {}

    /**
     * @brief Copy constructor.
     *
     * Duplicate an existing hashed state.
     *
     * @param s Reference to hashed state to copy.
     */
    HashedWordCounterState(const HashedWordCounterState& s) noexcept : State(s), _hash(s._hash), _counter(s._counter) {}

    /**
     * @brief Destructor.
     */
    virtual ~HashedWordCounterState() = default;

    /**
     * @brief Set the state to the starting state of the automaton.
     *
     * This method overrides the State::start() method, and resets the
     * counter in addition.
     *
     * @return        True if the resulting state is valid.
     */
    bool start() override
    {
      _hash = 0;
      _counter = 0;
      return State::start();
    }

    /**
     * @brief Start and transition.
     *
     * Sets the state to the starting state of the automaton, and
     * performs a transition using an input word.
     *
     * @param  in     Input word.
     * @return        True if the resulting state is valid.
     */
    bool startWord(const std::string &in) override
    {
      start();
      return deltaWord(in);
    }

    /**
     * @brief Delta transition.
     *
     * Perform a delta transition using an input word. A word
     * separator symbol ` ` is inserted before the word if it is not
     * the first word (the current state is not the start state).
     *
     * @param  in     Input word.
     * @return        True if the resulting state is valid.
     */
    bool deltaWord(const std::string &in) override
    {
      if(in.length()==0){
        return _state!=0;
      }
      if(_state!=_fsa->start())
        delta(' ');
      bool ok = delta(in);
      if(ok)
        ++_counter;  // only count valid word transitions
      return ok;
    }

    /**
     * @brief Get current hash value.
     *
     * For final states, returns the perfect hash value for the input
     * string which lead to the the state. For any state (including
     * final states) the value equals the number of strings accepted
     * by the automaton which (in an alphabetical ordering) preceed
     * the string leading to the state.
     *
     * @return Hash value.
     */
    hash_t hash() const override
    {
      return _hash;
    }

    /**
     * @brief Obsolete alias for hash(), for backwards compatibility.
     *
     * @return Hash value.
     */
    virtual hash_t getHash() const
    {
      return _hash;
    }

    /**
     * @brief Get current counter value.
     *
     * Return the current counter. The counter is the number of
     * word transitions from the start state to the current state.
     * If the state is not valid anymore, the counter is the number of
     * word transitions to the last valid state.
     *
     * @return Counter value.
     */
    virtual uint32_t counter() const
    {
      return _counter;
    }

    /**
     * @brief An alias for counter()
     *
     * @return Counter value.
     */
    virtual uint32_t getCounter() const
    {
      return _counter;
    }

  };

  // }}}

public:
  /**
   * @brief Magic number for identifying fsa files.
   */
  static const uint32_t MAGIC        = 0x79832469;

  /**
   * @brief Version number.
   *
   * Version number for identifying the fsa library and files. The
   * format is MMMmmmrrr, M=major, m=minor, r=revision. 1000 equals
   * 0.1.0.
   */
  static const uint32_t VER          = 2000001;

  /**
   * @brief Library version number.
   *
   * Static method which returns the library version.
   */
  static uint32_t libVER();

  /**
   * @brief Reserved symbol used for empty cells in internal tables.
   */
  static const symbol_t EMPTY_SYMBOL = 0x00;

  /**
   * @brief Reserved symbol used for final states in internal tables.
   */
  static const symbol_t FINAL_SYMBOL = 0xff;

  /**
   * @brief Type of data items for final states.
   *
   * Type of data items for final states. The possible values are:
   *   - DATA_VARIABLE (0) - variable size data items, the size is
   *     stored with each item
   *   - DATA_FIXED (1) - fixed size data items. The size is only
   *     stored once in the header.
   */
  enum Data_Type {
    DATA_VARIABLE = 0,
    DATA_FIXED
  };

  /**
   * @struct Header
   * @brief %FSA header.
   *
   * Header structure of the %FSA files.
   */
  struct Header {
    uint32_t _magic;                     /**< Magic number.                      */
    uint32_t _version;                   /**< Version number.                    */
    uint32_t _checksum;                  /**< Checksum.                          */
    uint32_t _size;                      /**< Size of fsa (cells).               */
    uint32_t _start;                     /**< Start state.                       */
    uint32_t _data_size;                 /**< Size of data.                      */
    uint32_t _data_type;                 /**< Type of data items.                */
    uint32_t _fixed_data_size;           /**< Data item size if fixed.           */
    uint32_t _has_perfect_hash;          /**< Indicator for perfect hash.        */
    uint32_t _serial;                    /**< Serial number                      */
    uint32_t _reserved[54];              /**< Reserved (pads size to 256 bytes). */
  };

  /**
   * @struct Descriptor
   * @brief %FSA descriptor.
   *
   * %FSA descriptor for creating FSA objects directly from Automaton
   * objects (used by Automaton::getFSA()).
   */
  struct Descriptor {
    uint32_t   _version;
    uint32_t   _serial;
    Unaligned<state_t> *_state;
    symbol_t  *_symbol;
    uint32_t   _size;
    data_t    *_data;
    uint32_t   _data_size;
    uint32_t   _data_type;
    uint32_t   _fixed_data_size;
    Unaligned<hash_t> *_perf_hash;
    uint32_t   _start;
  };

private:

  static const FileAccessMethod _default_file_access_method = FILE_ACCESS_MMAP;   /**< Default file access method (read/mmap). */

  void          *_mmap_addr;             /**< mmap address, NULL is file has not been mmapped.   */
  size_t         _mmap_length;           /**< mmap length.                                       */

  uint32_t       _version;               /**< Version of fsalib used to build this fsa. */
  uint32_t       _serial;                /**< Serial number of this fsa.                */

  Unaligned<state_t> *_state;                 /**< State table for transitions.       */
  symbol_t      *_symbol;                /**< Symbol table for transitions.      */
  uint32_t       _size;                  /**< Size (number of cells).            */

  data_t        *_data;                  /**< Data storage.                      */
  uint32_t       _data_size;             /**< Size of data storage.              */
  uint32_t       _data_type;             /**< Type of data items (fixed or var.) */
  uint32_t       _fixed_data_size;       /**< Size of data items if fixed.       */

  bool           _has_perfect_hash;      /**< Indicator of perfect hash present. */
  Unaligned<hash_t> *_perf_hash;             /**< Perfect hash table, if present.    */

  state_t        _start;                 /**< Index of start state.              */

  bool           _ok;                    /**< Flag set if object initialization succeeded. */

public:

  /**
   * @brief Constructor.
   *
   * Initializes the object from an fsa file.
   *
   * @param file Name of fsa file.
   * @param fam File access mode (read or mmap). If not set, the
   *            global default access mode will be used.
   */
  FSA(const char *file, FileAccessMethod fam = FILE_ACCESS_UNDEF);
  FSA(const std::string &file, FileAccessMethod fam = FILE_ACCESS_UNDEF);

  /**
   * @brief Destructor.
   */
  virtual ~FSA();

  /**
   * @brief Check if initialization was successful.
   *
   * @return True if the initialization of the object succeeded.
   */
  bool isOk() const
  {
    return _ok;
  }

  /**
   * @brief Get the fsa library version used for building this %FSA.
   *
   * @return fsa library version.
   */
  uint32_t version(void) const
  {
    return _version;
  }

  /**
   * @brief Get the serial number of the %FSA.
   *
   * @return Serial number.
   */
  uint32_t serial(void) const
  {
    return _serial;
  }

  /**
   * @brief Check if the %FSA has perferct hash.
   *
   * @return True if the %FSA was built with perfect hash.
   */
  bool hasPerfectHash(void) const
  {
    return _has_perfect_hash;
  }

  /**
   * @brief Get the start state of the %FSA.
   *
   * @return Index of the start state (0 if the %FSA is empty).
   */
  state_t start() const noexcept
  {
    return _start;
  }

  /**
   * @brief Perform a delta transition.
   *
   * Performs a delta transtion in the automaton. The input is the
   * index of the current state and an input symbol, and the return
   * value is the index of the new state.
   *
   * @param fs Index of current state.
   * @param in Input symbol.
   * @return Index of new state.
   */
  state_t delta(state_t fs, symbol_t in) const
  {
    // fs!=0 check is unnecessary, as state 0 is never packed so _symbol[in]!=in always.
    // if(!fs)
    //  return 0;
    state_t nfs=fs+in;
    if(_symbol[nfs]==in)
      return _state[nfs];
    else
      return 0;
  }

  /**
   * @brief Get hash delta for a transition.
   *
   * The perfect hash value for a final state is obtained from the sum
   * of hash deltas for the transitions leading to that state.
   *
   * @param fs Index of current state.
   * @param in Input symbol.
   * @return Hash delta for the transition.
   */
  hash_t hashDelta(state_t fs, symbol_t in) const
  {
    if(_has_perfect_hash && fs!=0 && _symbol[fs+in]==in)
      return _perf_hash[fs+in];
    else
      return 0;
  }

  /**
   * @brief Check if the state is a final (accepting) state.
   *
   * @param fs State.
   * @return True if the state is final.
   */
  bool isFinal(state_t fs) const
  {
    if(fs==0)
      return false;
    return _symbol[fs+FINAL_SYMBOL]==FINAL_SYMBOL;
  }

  /**
   * @brief Reverse lookup.
   *
   * For a given hash value, return the corresponding string.
   *
   * @param hash Hash value.
   * @return String corresponding to hash value, or empty string if
   *         the fsa has no perfect hash or the hash value is out of
   *         range.
   */
  std::string revLookup(hash_t hash) const;

  /**
   * @brief Get the size of data item associated with a final state.
   *
   * @param fs State.
   * @return Size of data item, or -1 if the state is not final.
   */
  int dataSize(state_t fs) const
  {
    if(fs==0)
      return -1;
    if(_symbol[fs+FINAL_SYMBOL]==FINAL_SYMBOL){
      if(_data_type==DATA_FIXED)
        return _fixed_data_size;
      else
        return (int)Unaligned<uint32_t>::at(_data+_state[fs+FINAL_SYMBOL]).read();
    }
    return -1;
  }

  /**
   * @brief Get a pointer to the data item associated with a final state.
   *
   * @param fs State.
   * @return Pointer to data item, or NULL if the state is not final.
   */
  const data_t *data(unsigned int fs) const
  {
    if(fs==0)
      return NULL;
    if(_symbol[fs+FINAL_SYMBOL]==FINAL_SYMBOL){
      if(_data_type==DATA_FIXED)
        return _data+_state[fs+FINAL_SYMBOL];
      else
        return _data+_state[fs+FINAL_SYMBOL]+sizeof(uint32_t);
    }
    return NULL;
  }

  /**
   * @brief Print the fsa in dot (graphviz) format.
   *
   * @param out Output stream (std::cout if omitted).
   */
  void printDot(std::ostream &out=std::cout) const;

  /**
   * @brief Get iterator pointing to the beginning of the fsa.
   *
   * @return iterator pointing to the first string in the fsa.
   */
  FSA::iterator begin() const { return FSA::iterator(this); }

  /**
   * @brief Get iterator pointing past the end of the fsa.
   *
   * @return iterator pointing past the last string in the fsa.
   */
  FSA::iterator end() const { return FSA::iterator(this,true); }

private:

  /**
   * @brief Unimplemented private default constructor.
   */
  FSA();
  /**
   * @brief Unimplemented private copy constructor.
   */
  FSA(const FSA&);
  /**
   * @brief Unimplemented private assignment operator.
   */
  const FSA& operator=(const FSA&);

  /**
   * Automaton needs access to a private constructor.
   */
  friend class Automaton;

  /**
   * @brief Constructor.
   *
   * Initializes the object from ready memory buffers.
   * (Used by Automaton::PackedAutomaton::getFSA.)
   *
   * @param d  Descriptor containing all FSA parameters.
   */
  FSA(Descriptor &d) :
    _mmap_addr(NULL), _mmap_length(0),
    _version(d._version), _serial(d._serial),
    _state(d._state), _symbol(d._symbol), _size(d._size),
    _data(d._data), _data_size(d._data_size), _data_type(d._data_type),
    _fixed_data_size(d._fixed_data_size),
    _has_perfect_hash(d._perf_hash!=NULL),_perf_hash(d._perf_hash),
    _start(d._start)
  {
  }

  /**
   * @brief Reset the object.
   *
   * Resets the object to an empty %FSA, and releases allocated memory.
   */
  void reset();

  /**
   * @brief Read the %FSA from file.
   *
   * Reads the %FSA from a file. Returns true on success.
   *
   * @param filename Name of fsa file.
   * @return     True on success.
   */
  bool read(const char *filename, FileAccessMethod fam = FILE_ACCESS_UNDEF);

};

// }}}

} // namespace fsa

