// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    automaton.h
 * @brief   Definition of the classes used for %FSA (%Finite %State %Automaton) construction
 *
 */

#pragma once

#include <map>
#include <list>
#include <string>
#include <cassert>

#include "blob.h"
#include "fsa.h"

namespace fsa {

// {{{ Automaton
/**
 * @class Automaton
 * @brief %FSA (%Finite %State %Automaton) construction class.
 *
 * The Automaton class provides the methods and data structures needed
 * for construcing a %Finite %State %Automaton from input strings. (The
 * current implementation requires the input to be sorted, this
 * requirement may be relaxed in future relases.)
 *
 * The constructed %FSA, when stored in a compact representation, can
 * be used for lookups, etc. vie the FSA class. The compact %FSA can
 * not be modified anymore.
 */
class Automaton {

public:
  /**
   * Empty data item for final states without assigned data. Contains
   * a zero terminated empty string.
   */
  static const Blob EMPTY_BLOB;

private:

  class State;

  // {{{ Automaton::Transition
  /**
   * @struct Transition
   * @brief Struct for storing a single transition.
   *
   * A transition consists of an input symbol and a new state.
   */
  struct Transition {
    symbol_t  _symbol;  /**< Input symbol. */
    State    *_state;   /**< New state.    */
  };
  // }}}

  // {{{ Automaton::TransitionList
  /**
   * @class TransitionList
   * @brief Class representing all transitions from a state.
   *
   * This class is used for the interal representation of the
   * automaton.  A state can be represented by the list of all
   * possible transitions from that state. Two states are
   * equivalent, if both are final (with the same meta info) or both
   * are not final, and their transition list matches, that is they
   * have the same number of out-transitions, these correspond to the
   * same set of input symbols, and for each of these symbols the new
   * states are equal. In the internal representation, final states
   * are implemented by means of a special transition, so transition
   * list equivalence is implies state equivalence.
   */
  class TransitionList {

    friend class State;

  private:
    unsigned int _alloc;  /**< Allocated size (number of transitions). */
    unsigned int _size;   /**< Used size.                              */
    Transition*  _trans;  /**< Transition array.                       */

  public:
    /**
     * @brief Constructor.
     *
     * Default constructor, creates an empty transition list.
     */
    TransitionList() : _alloc(0), _size(0), _trans(NULL) {};

    /**
     * @brief Constructor.
     *
     * Constructor, creates an empty transition list, but preallocates
     * space for a given number of transitions.
     *
     * @param prealloc  Number of states to preallocate space for.
     */
    TransitionList(unsigned int prealloc) : _alloc(prealloc), _size(0), _trans(NULL)
    { if(prealloc>0){
        _trans = (Transition*)malloc(prealloc*sizeof(Transition));
        assert(_trans!=NULL);
      }
    }

    /**
     * @brief Destructor.
     */
    ~TransitionList()
    { if(_trans!=NULL) free(_trans); }

    /**
     * @brief Copy constructor.
     *
     * @param tl Reference to transition list object.
     */
    TransitionList(const TransitionList& tl) :  _alloc(tl._size), _size(tl._size), _trans(NULL)
    {
      if(_alloc>0){
        _trans = (Transition*)malloc(_alloc*sizeof(Transition));
        assert(_trans!=NULL);
      }
      for(unsigned int i=0; i<_size; i++)
        _trans[i] = tl._trans[i];
    }


    /**
     * @brief Less-than operator.
     *
     * t1<t2 (or t1.operator<(t2) is true iff
     *   - t1 has less transitions than t2, or
     *   - t1 and t2 have the same number of transitions, and the
     *     first transition which is different for t1 and t2 (sorted
     *     by symbol) has a lower symbol for t1, or
     *   - t1 and t2 have the same number of transitions, and the
     *     first transition which is different for t1 and t2 (sorted
     *     by symbol) has the same symbol but a lower new state for t1
     *
     * @param tl Reference to transition list object.
     * @return True iff the t1<t2.
     */
    bool operator<(const TransitionList& tl) const;

    /**
     * @brief Greater-than operator.
     *
     * t1>t2 (or t1.operator>(t2) is true iff
     *   - t1 has more transitions than t2, or
     *   - t1 and t2 have the same number of transitions, and the
     *     first transition which is different for t1 and t2 (sorted
     *     by symbol) has a higher symbol for t1, or
     *   - t1 and t2 have the same number of transitions, and the
     *     first transition which is different for t1 and t2 (sorted
     *     by symbol) has the same symbol but a higher new state for t1
     *
     * @param tl Reference to transition list object.
     * @return True iff the t1>t2.
     */
    bool operator>(const TransitionList& tl) const;

    /**
     * @brief Equals operator.
     *
     * t1==t2 (or t1.operator==(t2) is true iff
     *   - t1 and t2 have the same number of transitions, which have
     *     the same set of of symbols and for each symbol the new
     *     states are equal
     *
     * @param tl Reference to transition list object.
     * @return True iff the t1==t2.
     */
    bool operator==(const TransitionList& tl) const;

    /**
     * @brief Check for emptyness.
     *
     * @return True iff the transition list is empty.
     */
    bool empty() { return (_size==0); }

    /**
     * @brief Get transition list size.
     *
     * @return Size of the transition list (number of transitions, or 0 if empty).
     */
    unsigned int size() const { return _size; }

    /**
     * @brief Index operator.
     *
     * Returns a reference to the ith transition on the list. i must
     * be between 0 and size-1 (0<=i<=size-1).
     *
     * @param i Index of transition.
     * @return Reference to the ith transition.
     */
    const Transition& operator[](unsigned int i) const { return _trans[i]; }

    /**
     * @brief Get the last transition.
     *
     * Returns a pointer to the last transition, or NULL pointer if
     * the list is empty.
     *
     * @return Pointer to last transition, or NULL.
     */
    Transition* last()
    { if(_size>0) return &_trans[_size-1];
      return NULL;
    }

    /**
     * @brief Get the transition corresponding to a symbol.
     *
     * Returns a pointer to the transition corresponding to a given
     * symbol, or NULL pointer if the symbol is not found on the list
     * (a transition with that symbol does not exist).
     *
     * @param sy Input symbol.
     * @return Pointer to last transition, or NULL.
     */
    Transition* find(symbol_t sy)
    { for(unsigned int i=0; i<_size; i++){
        if(_trans[i]._symbol == sy) return &_trans[i];
      }
      return NULL;
    }

    /**
     * @brief Append a new transition to the list.
     *
     * Appends a new transition to the end of the list. The allocated
     * size is increased if necessary. If a transition with the same
     * symbol already exists, the behaviour is undefined.
     *
     * @param sy Input symbol.
     * @param st Pointer to new state.
     */
    void append(symbol_t sy, State* st)
    { if(_size==_alloc){
        if(_alloc==0){
          _alloc=1;
          _trans = (Transition*)malloc(_alloc*sizeof(Transition));
        }
        else{
          _alloc+=2;
          _trans = (Transition*)realloc(_trans,_alloc*sizeof(Transition));
        }
        assert(_trans!=NULL);
      }
      _trans[_size]._symbol=sy;
      _trans[_size]._state=st;
      _size++;
    }

  };

  // }}}

  // {{{ Automaton::TListPtr
  /**
   * @class TListPtr
   * @brief Helper class, pointer to a transition list (TransitionList).
   *
   * The purpose of this class is to override the comparison operators
   * for a pointer, instead of comparing the value of the pointer
   * itself, compares the objects the pointer is pointing to.
   */
  class TListPtr {

  private:
    const TransitionList *_ptr; /**< Pointer to a TransitionList */

  public:

    /**
     * @brief Constructor.
     *
     * Initialize object to point to the specified transition list.
     *
     * @param tl pointer to a transition list.
     */
    TListPtr(const TransitionList *tl) : _ptr(tl) {}

    /**
     * @brief Copy constructor.
     *
     * Initialize object from another TListPtr.
     *
     * @param tp Reference to TListPtr.
     */
    TListPtr(const TListPtr& tp) : _ptr(tp._ptr) {}

    /**
     * @brief Get the pointer to the transition list.
     *
     * @return Pointer to the TransitionList.
     */
    const TransitionList* getPtr() const { return _ptr; }

    /**
     * @brief Less-than operator.
     *
     * Compares the pointed objects instead of the value of the
     * pointer itself.
     *
     * @param tp Reference to TListPtr object.
     * @return Comparison result.
     */
    bool operator<(const TListPtr& tp) const
    { return(*_ptr<*tp._ptr); }

    /**
     * @brief Greater-than operator.
     *
     * Compares the pointed objects instead of the value of the
     * pointer itself.
     *
     * @param tp Reference to TListPtr object.
     * @return Comparison result.
     */
    bool operator>(const TListPtr& tp) const
    { return(*_ptr>*tp._ptr); }

    /**
     * @brief Equals operator.
     *
     * Compares the pointed objects instead of the value of the
     * pointer itself.
     *
     * @param tp Reference to TListPtr object.
     * @return Comparison result.
     */
    bool operator==(const TListPtr& tp) const
    { return(*_ptr==*tp._ptr); }
  };
  // }}}

  // {{{ Automaton::State
  /**
   * @class State
   * @brief Class representing a state of the automaton.
   *
   * The representation of the automaton states consists of a
   * transition list for the state, and meta info blob (the latter
   * only used for special states reached by a final transition. A
   * final transition is a transition from a final (accepting) state
   * with the reserved FINAL_SYMBOL (0xff) to a special state, which
   * stores the meta info corresponding to the final state. For each
   * unique meta info blob, there is one special state.
   */
  class State {

  private:

    TransitionList  _tlist;  /**< Transition list. */
    const Blob      *_blob;  /**< Meta info blob.  */

  public:

    /**
     * @brief Constructor.
     *
     * Default constructor, creates a state with an empty transition
     * list and no (NULL) blob.
     */
    State() : _tlist(), _blob(NULL) {}

    /**
     * @brief Constructor.
     *
     * Creates a (special) state with an empty transition list and a
     * given blob.
     *
     * @param b Pointer to blob.
     */
    State(const Blob* b) : _tlist(), _blob(b) {}

    /**
     * @brief Destructor.
     */
    ~State() { if(_blob!=NULL) delete _blob; }

    /**
     * @brief Check if the state is final (accepting) state.
     *
     * @return True if the state is final.
     */
    bool isFinal() { return child(FSA::FINAL_SYMBOL)!=NULL; }

    /**
     * @brief Get the blob assigned to the state.
     *
     * @return Pointer to blob.
     */
    const Blob* getBlob() const { return _blob; }

    /**
     * @brief Check if the state has children.
     *
     * Returns true if the state has children (the transition list is
     * not empty), or false if the state is a leaf.
     *
     * @return True if the state has children.
     */
    bool hasChildren() { return !_tlist.empty(); }

    /**
     * @brief Get child corresponding to a symbol.
     *
     * Get the child of the state which is reached by a transition
     * with a given symbol. If there is no out-transition with that
     * symbol, NULL is returned.
     *
     * @return Pointer to the child, or NULL.
     */
    State* child(symbol_t sy)
    { Transition* t = _tlist.find(sy);
      if(t!=NULL){ return t->_state; }
      return NULL;
    }

    /**
     * @brief Get the last child.
     *
     * Get the last child of the state which is reached by a valid
     * transition (not FINAL_SYMBOL). If no such children exists, NULL
     * is returned.
     *
     * @return Pointer to last child, or NULL.
     */
    State* lastChild()
    { Transition* t = _tlist.last();
      if(t!=NULL && t->_symbol!=FSA::FINAL_SYMBOL){ return t->_state; }
      return NULL;
    }

    /**
     * @brief Update the last child.
     *
     * Updates the last child to point to a new state. This method is
     * used when merging equivalent subtrees together.
     *
     * @param st New state to be used in last child.
     */
    void updateLastChild(State* st)
    { Transition* t = _tlist.last();
      if(t!=NULL){
        t->_state = st;
      }
    }

    /**
     * @brief Append a new empty child.
     *
     * Append an empty child to the list of transitions using the
     * given symbol (and optional blob).
     *
     * @param sy New transition symbol.
     * @param b  Optional blob to be assigned to the new state, defaults to NULL.
     * @return Pointer to the new state.
     */
    State* addEmptyChild(symbol_t sy, const Blob *b=NULL)
    {
      State* child = new State(b);
      assert(child!=NULL);
      _tlist.append(sy,child);
      return child;
    }

    /**
     * @brief Add a transition to an existing state.
     *
     * Append a new transition to the list pointing to an existing
     * state, using the given symbol.
     *
     * @param sy New transition symbol.
     * @param child Pointer to destination state (already existing).
     * @return Pointer to the child state.
     */
    State* addChild(symbol_t sy, State* child)
    {
      _tlist.append(sy,child);
      return child;
    }

    /**
     * @brief Get the transition list.
     *
     * Get the transition list of the state.
     *
     * @return Reference to the transition list.
     */
    const TransitionList& getTransitionList(void) const { return _tlist; }


  };

  // }}}

  // {{{ Automaton::Register, BlobRegister, PackMap, SymList and iterators
  /**
   * @brief Register of states, maps a transition list to a state object
   */
  using Register = std::map< TListPtr,State* >;
  /**
   * @brief State register iterator.
   */
  using RegisterIterator = std::map< TListPtr,State* >::iterator;

  /**
   * @brief Register of states, maps a blob to a special state.
   */
  using BlobRegister = std::map< Blob,State* >;
  /**
   * @brief Blob register iterator.
   */
  using BlobRegisterIterator = std::map< Blob,State* >::iterator;

  /**
   * @brief Packing map, maps a state pointer to a state ID.
   */
  using PackMap = std::map< const void*, unsigned int >;
  /**
   * @brief Packing map iterator.
   */
  using PackMapIterator = std::map< const void*, unsigned int >::iterator;

  /**
   * @brief symbol_t list.
   */
  using SymList = std::list<symbol_t> ;
  /**
   * @brief symbol_t list iterator.
   */
  using SymListIterator = std::list<symbol_t>::iterator;
  // }}}

  // {{{ Automaton::PackedAutomaton

  /**
   * @class PackedAutomaton
   * @brief Helper class for packing an automaton.
   *
   * This class is used for packing an Automaton to a compressed
   * format which can be saved to file to be used by the FSA class.
   */
  class PackedAutomaton {

  private:
    bool          _packable;           /**< Packable flag.                    */
    PackMap       _pack_map;           /**< Map state pointers to indices.    */
    PackMap       _blob_map;           /**< Map blob pointers to indices.     */
    State       **_packed_ptr;         /**< Array for state pointers.         */
    state_t      *_packed_idx;         /**< Array for state indices.          */
    symbol_t     *_symbol;             /**< Array for transition symbols.     */
    bool         *_used;               /**< Array for cell used flags.        */
    hash_t       *_perf_hash;          /**< Array for perfect hash deltas.    */
    hash_t       *_totals;             /**< Array for perfect hash totals.    */
    uint32_t      _packed_size;        /**< Size of packed arrays (in cells). */
    uint32_t      _last_packed;        /**< Index of last packed state.       */

    data_t       *_blob;               /**< Data storage.                     */
    uint32_t      _blob_size;          /**< Data storage size.                */
    uint32_t      _blob_used;          /**< Used data storage size.           */
    uint32_t      _blob_type;          /**< Type of data items (fixed/var.)   */
    uint32_t      _fixed_blob_size;    /**< Data item size if fixed.          */

    state_t       _start_state;        /**< Index of start state.             */

    /**
     * @brief Number of cells to allocate in one expansion.
     */
    static const uint32_t _ALLOC_CELLS = 131072;  // 128k

    /**
     * @brief Number of bytes to allocate in one data storage expansion.
     */
    static const uint32_t _ALLOC_BLOB  = 65536;  // 64k

    /**
     * @brief How long back the search for an empty cell should start.
     */
    static const uint32_t _BACKCHECK = 255;


    /**
     * @brief Expand cell arrays.
     */
    void expandCells();

    /**
     * @brief Expand data storage.
     *
     * @param minExpand Mimimum size to expand, it will be rounded up
     *                  to the nearest multiply of _ALLOC_BLOB.
     */
    void expandBlob(uint32_t minExpand);

    /**
     * @brief Get an empty cell.
     *
     * Start looking for an empty cell _BACKCHECK cells before the
     * last packed cell, and return the index of the first empty cell
     * found. The cell arrays are expanded on demand, that is if no
     * empty cell is found.
     *
     * @return Index of empty cell.
     */
    uint32_t getEmptyCell();

    /**
     * @brief Get an empty cell where a list of transitions can be stored.
     *
     * Start looking for an empty cell _BACKCHECK cells before the
     * last packed cell. In addition to the cell being empty, it
     * should be possible to store a list of transitions from that
     * cell. The cell arrays are expanded on demand, that is if no
     * empty cell is found.
     *
     * @param t List of transition symbols.
     * @return Index of empty cell.
     */
    uint32_t getCell(SymList t);

    /**
     * @brief Pack a data item.
     *
     * Pack a data item to the data storage. If the same (or
     * equivalent) data item has been packed before, return the offset
     * where it was packed. Otherwise, pack the data item at the end
     * of the storage (expand storage if needed), add the item and
     * offset to the blob map and return the offset.
     *
     * @param b Pointer to data item.
     * @return Offset to data item in data storage.
     */
    uint32_t packBlob(const Blob* b);

    /**
     * @brief Compute perfect hash deltas for a subtree.
     *
     * Recursive function for computing the perfect hash deltas for
     * all transitions within a subtree. The delta for transition T
     * from state S is the number of final states reachable from state
     * S via transitions lower than T (that is, with a lower input
     * symbol). Also, state S being a final state counts. The hash
     * deltas are filled into the _perf_hash array.
     *
     * @return Number of final states within the subtree.
     */
    hash_t computePerfectHash(state_t state);


  public:

    /**
     * @brief Default constructor.
     */
    PackedAutomaton() :
      _packable(false),
      _pack_map(),
      _blob_map(),
      _packed_ptr(NULL),
      _packed_idx(NULL),
      _symbol(NULL),
      _used(NULL),
      _perf_hash(NULL),
      _totals(NULL),
      _packed_size(0),
      _last_packed(0),
      _blob(NULL),
      _blob_size(0),
      _blob_used(0),
      _blob_type(0),
      _fixed_blob_size(0),
      _start_state(0)
    { }

    /**
     * @brief Destructor.
     */
    ~PackedAutomaton() { reset(); }

    /**
     * @brief Reset the object.
     *
     * Reset the object and free all allocated memory.
     */
    void reset();

    /**
     * @brief Initialize.
     *
     * Reset the object, and initialize data structures, also
     * preallocate memory for cell and data storage.
     */
    void init();

    /**
     * @brief Pack a state.
     *
     * Pack a state and its transitions into the compact structure. For
     * final states, the data item is packed as well.
     *
     * @param s Pointer to state to pack.
     * @param start True if the state is the start state.
     * @return False if the object is not packable (it has been
     *         finalized, or it has not been initialized)
     */
    bool packState(const State* s, bool start=false);

    /**
     * @brief Pack the start state.
     *
     * Pack the state and mark it as the start state. (Equivalent to
     * packState(s,true)).
     *
     * @param s Pointer to state to pack.
     * @return False if the object is not packable (it has been
     *         finalized, or it has not been initialized)
     */
    bool packStartState(const State* s);

    /**
     * @brief Finalize the packed structure.
     *
     * Obtain all state inidices from the state pointers using the
     * pack map. Also compact the data storage if all data items have
     * the same size (only store the size once, and store data items
     * consecutively, without size attribute).
     */
    void finalize();

    /**
     * @brief Add perfect hash to the automaton.
     *
     * Computes the perfect hash for the whole automaton.
     */
    void addPerfectHash();

    /**
     * @brief Write the automaton to a file.
     *
     * @param filename Name of file.
     * @param serial Serial number.
     * @return True on success.
     */
    bool write(const char *filename, uint32_t serial = 0);

    /**
     * @brief Read an automaton from file.
     *
     * @param filename Name of file.
     * @return True on success.
     */
    bool read(const char *filename);

    /**
     * @brief Perform a lookup in the packed automaton.
     *
     * @param input Input string
     * @return Pointer to data associated with input, or NULL if input is not accepted.
     */
    const unsigned char* lookup(const char *input) const;

    /**
     * @brief Create an FSA object from the automaton.
     *
     * Create an FSA object from the automaton. The PackedAutomaton is
     * implicitly reset if the operation succeeds. PackedAutomanton
     * cannot access the private constructor of FSA, so we have to pass
     * the object via a struct, which is ugly :-(.
     *
     * @param d Pointer to the FSA::Descriptor (struct) to store necessary info for
     *                  creating the FSA object.
     * @return True if the operation was successful.
     */
    bool getFSA(FSA::Descriptor &d);

  };

  // }}}


  Register      _register;        /**< Register of states.           */
  BlobRegister  _blob_register;   /**< Register of data items.       */
  State*        _q0;              /**< Start state.                  */
  std::string   _previous_input;  /**< Previous input string.        */
  bool          _finalized;       /**< Finalized flag.               */
  PackedAutomaton  _packed;       /**< Packed automaton.             */

  /**
   * @brief Get common path length.
   *
   * Get the length of the common path shared by the current input
   * string and strings already in the automaton.
   *
   * @param input Input string.
   * @return Length of common path.
   */
  unsigned int getCPLength(const char *input);

  /**
   * @brief Get last state in common path.
   *
   * Get the last state of the common path shared by the current input
   * string and strings already in the automaton.
   *
   * @param input Input string.
   * @return Pointer to last state in common path.
   */
  State* getCPLastState(const char *input);

  /**
   * @brief Replace or register a state.
   *
   * Replace the state with an already registered equivalent state in
   * the automaton, or register it if no such state exists yet.
   *
   * @param state Pointer to state to be replaced or registered.
   */
  void replaceOrRegister(State* state);

  /**
   * @brief Add new states for a suffix.
   *
   * Add the necessary new states for a suffix of an input string. The
   * suffix is that part of an input string which is not covered by
   * the common path.
   *
   * @param state Pointer to last state in the common path.
   * @param suffix Suffix.
   * @param b Data item associated with the input.
   */
  void addSuffix(State* state, const char *suffix, const Blob *b=NULL);

  /**
   * @brief Clean up data structures and release memory.
   */
  void cleanUp();

public:

  /**
   * @brief Default constructor.
   */
  Automaton() :
    _register(),
    _blob_register(),
    _q0(NULL),
    _previous_input(),
    _finalized(false),
    _packed()
  {}

  /**
   * @brief Destructor.
   */
  ~Automaton();

  /**
   * @brief Initialize the object.
   */
  void init();

  /**
   * @brief Insert a string to the automaton.
   *
   * Insert a string to the automaton. Input strings must be inserted
   * in sorted order, otherwise the behaviour is undefined.
   *
   * @param input Input string.
   */
  void insertSortedString(const std::string &input);

  /**
   * @brief Insert a string to the automaton.
   *
   * Insert a string to the automaton. Input strings must be inserted
   * in sorted order, otherwise the behaviour is undefined.
   *
   * @param input Input string.
   * @param meta  Meta info string to be stored in data item).
   */
  void insertSortedString(const std::string &input, const std::string &meta);

  /**
   * @brief Insert a string to the automaton.
   *
   * Insert a string to the automaton. Input strings must be inserted
   * in sorted order, otherwise the behaviour is undefined.
   *
   * @param input Input string.
   * @param b     Reference to data item.
   */
  void insertSortedString(const char *input, const Blob &b);

  /**
   * @brief Insert a string to the automaton.
   *
   * Insert a string to the automaton. Input strings must be inserted
   * in sorted order, otherwise the behaviour is undefined.
   *
   * @param input Input string.
   * @param b     Pointer to data item.
   */
  void insertSortedString(const char *input, const Blob *b=NULL);

  /**
   * @brief Finalize the automaton.
   *
   * Finalize the automaton. This involves calling replaceOrRegister
   * for the start state _q0, and building the packed automaton, so no
   * strings can be added to the automaton after this method is
   * called.
   */
  void finalize();

  /**
   * @brief Add perfect hash to automaton.
   *
   * Compute and add perfect hash structure to the automaton. Only
   * works on finalized automata.
   */
  void addPerfectHash();

  /**
   * @brief Write the finalized automaton to file.
   *
   * @param file Name of the file.
   * @param serial Serial number.
   * @return True on success.
   */
  bool write(const char *file, uint32_t serial = 0);

  /**
   * @brief Write the finalized automaton to file.
   *
   * @param file Name of the file.
   * @param serial Serial number.
   * @return True on success.
   */
  bool write(const std::string &file, uint32_t serial = 0)
  {
    return write(file.c_str(),serial);
  }

  /**
   * @brief Create an FSA object from the automaton.
   *
   * Create an FSA object from the automaton. The Automaton and
   * PackedAutomaton is implicitly reset.
   *
   * @return Pointer to a newly created  FSA object.  The caller is
   *         responsible for freeing it.
   */
  FSA* getFSA(void);

};
// }}}

} // namespace fsa

