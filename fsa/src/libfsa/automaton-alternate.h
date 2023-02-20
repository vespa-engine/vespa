// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @file    automaton.h
 * @brief   Definition of the classes used for %FSA (%Finite %State %Automaton) construction
 *
 */

#pragma once

#include <map>
#include <list>
#include <string>
#include <vector>
#include <cassert>
#include <sys/mman.h> // for mmap() etc

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
 * requirement may be relaxed in future releases.)
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
   * This class is used for the internal representation of the
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
    Transition*  _trans;  /**< Transition array.                       */
    unsigned int _size;   /**< Used size.                              */

  public:
    /**
     * @brief Constructor.
     *
     * Default constructor, creates an empty transition list.
     */
    TransitionList() : _trans(NULL), _size(0) {};

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
    TransitionList(const TransitionList& tl) : _trans(NULL), _size(tl._size)
    {
      if(_size>0){
        _trans = (Transition*)malloc(_size*sizeof(Transition));
        assert(_trans!=NULL);
      }
      memcpy(_trans, tl._trans, sizeof(_trans[0]) * _size);
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
    {
      if(_size==0){
        _trans = (Transition*)malloc(sizeof(Transition));
      }
      else{
        _trans = (Transition*)realloc(_trans,(_size+1)*sizeof(Transition));
      }
      assert(_trans!=NULL);
      _trans[_size]._symbol=sy;
      _trans[_size]._state=st;
      _size++;
    }

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
  // {{{ Automaton::TListPtrLess
  /**
   * @class TListPtrLess
   * @brief Less-than functor for use with ordered STL containers.
   *
   * The function compares two TransitionList pointers by comparing
   * the objects they point to.
   */
  struct TListPtrLess {
      inline bool operator()(const TransitionList * const & x, const TransitionList * const & y) const { return *x < *y; }
  };
  // }}}
  // {{{ Special allocator for Register that will make it possible to completely reclaim its memory when we are done with it
  template <typename _Tp>
  class MMapArenaAllocator {
    std::vector<_Tp*> _chunks;
    size_t _size; // used # of objects in current chunk
    static const size_t _CAPACITY = 16 * 1024 * 1024; // capacity of chunk in bytes
  public:
    using size_type = size_t;
    using difference_type = ptrdiff_t;
    using pointer = _Tp*;
    using const_pointer = const _Tp*;
    using reference = _Tp&;
    using const_reference = const _Tp&;
    using value_type = _Tp;

    template<typename _Tp1>
      struct rebind
      { using other = MMapArenaAllocator<_Tp1>; };

    MMapArenaAllocator() noexcept : _chunks(), _size(0) { }

    MMapArenaAllocator(const MMapArenaAllocator&) noexcept : _chunks(), _size(0) { }

    template<typename _Tp1>
      MMapArenaAllocator(const MMapArenaAllocator<_Tp1>&) noexcept : _chunks(), _size(0) { }

    ~MMapArenaAllocator() noexcept { release(); }

    pointer
    address(reference __x) const { return &__x; }

    const_pointer
    address(const_reference __x) const { return &__x; }

    // NB: __n is permitted to be 0.  The C++ standard says nothing
    // about what the return value is when __n == 0.
    pointer
    allocate(size_type __n, const void* = 0)
    {
      pointer __ret;
      if(__n) {
        size_type __b = __n * sizeof(_Tp);
        if(_chunks.size()==0 || _CAPACITY - (_size*sizeof(_Tp)) < __b) { // need new chunk
          __ret = static_cast<_Tp*>(::mmap(0, _CAPACITY, PROT_READ|PROT_WRITE, MAP_PRIVATE|MAP_ANONYMOUS, /*fd=*/0, /*offset=*/0));
          if(__ret == MAP_FAILED)
            throw std::bad_alloc();
          _chunks.push_back(__ret);
          _size = __n;
        }
        else { // fits in current chunk
          __ret = (*(_chunks.end()-1)) + _size;
          _size += __n;
        }
      }
      return __ret;
    }

    // __p is not permitted to be a null pointer.
    void
    deallocate(pointer, size_type)
    { }

    void release(void)
    {
      for(size_t i = 0; i < _chunks.size(); i++){
        ::munmap(_chunks[i], _CAPACITY);
      }
      _chunks.clear();
      _size = 0;
    }

    size_type
    max_size() const noexcept
    { return _CAPACITY / sizeof(_Tp); }

    void
    construct(pointer __p, const _Tp& __val)
    { ::new(__p) value_type(__val); }

    void
    destroy(pointer __p) { __p->~_Tp(); }
  };
  // }}}
  // {{{ Automaton::Register, BlobRegister, StateArray, StateCellArray, PackMap, SymList and iterators

  struct StateArrayLess {
    bool operator()(State* const &x, State* const &y)
    { return x < y; }
  };
  struct StateCellArrayItem {
    State *state;
    uint32_t cell;
    StateCellArrayItem(): state(NULL), cell(0) { }
    StateCellArrayItem(State *s): state(s), cell(0) { }
  };
  struct StateCellArrayLess {
    bool operator()(const StateCellArrayItem &x, const StateCellArrayItem &y)
    { return x.state < y.state; }
  };

  /**
   * @brief Register of states, maps a transition list to a state object
   */
  using Register = std::map< const TransitionList*,State*,TListPtrLess,MMapArenaAllocator< std::pair< const TransitionList*, State* > > >;
  /**
   * @brief State register iterator.
   */
  using RegisterIterator = std::map< const TransitionList*,State*,TListPtrLess,MMapArenaAllocator< std::pair< const TransitionList*, State* > > >::iterator;

  /**
   * @brief Register of states, maps a blob to a special state.
   */
  using BlobRegister = std::map< Blob,State* >;
  /**
   * @brief Blob register iterator.
   */
  using BlobRegisterIterator = std::map< Blob,State* >::iterator;

  /**
   * @brief Array of state pointers.
   */
  using StateArray = std::vector< State* >;
  /**
   * @brief State* array iterator.
   */
  using StateArrayIterator = std::vector< State* >::iterator;

  /**
   * @brief Array of state/cell pairs.
   */
  using StateCellArray = std::vector< StateCellArrayItem >;
  /**
   * @brief StateCell array iterator.
   */
  using StateCellArrayIterator = std::vector< StateCellArrayItem >::iterator;

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
  using SymList = std::list<symbol_t>;
  /**
   * @brief symbol_t list iterator.
   */
  using SymListIterator = std::list<symbol_t>::iterator;
  /**
   * @brief symbol_t list const_iterator.
   */
  using SymListConstIterator = std::list<symbol_t>::const_iterator;
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
    bool             _packable;         /**< Packable flag.                    */
    PackMap          _blob_map;         /**< Map blob pointers to indices.     */
    State          **_packed_ptr;       /**< Array for state pointers.         */
    state_t         *_packed_idx;       /**< Array for state indices.          */
    symbol_t        *_symbol;           /**< Array for transition symbols.     */
    bool            *_used;             /**< Array for cell used flags.        */
    hash_t          *_perf_hash;        /**< Array for perfect hash deltas.    */
    hash_t          *_totals;           /**< Array for perfect hash totals.    */
    uint32_t         _packed_size;      /**< Size of packed arrays (in cells). */
    uint32_t         _last_packed;      /**< Index of last packed state.       */

    data_t          *_blob;             /**< Data storage.                     */
    uint32_t         _blob_size;        /**< Data storage size.                */
    uint32_t         _blob_used;        /**< Used data storage size.           */
    uint32_t         _blob_type;        /**< Type of data items (fixed/var.)   */
    uint32_t         _fixed_blob_size;  /**< Data item size if fixed.          */

    state_t          _start_state;      /**< Index of start state.             */

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
    uint32_t getCell(const SymList &t);

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
     * @return False if the object is not packable (it has been
     *         finalized, or it has not been initialized)
     */
    bool packState(Automaton::StateCellArrayIterator &it);

    /**
     * @brief Set the cell of the start state.
     *
     * @param cell Cell of start state.
     */
    void setStartState(uint32_t cell) { _start_state = (state_t)cell; }

    /**
     * @brief Finalize the packed structure.
     *
     * Obtain all state indices from the state pointers using the
     * pack map. Also compact the data storage if all data items have
     * the same size (only store the size once, and store data items
     * consecutively, without size attribute).
     *
     * @param queue State queue.
     */
    void finalize(const StateCellArray &queue);

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


  Register        *_register;       /**< Register of states.           */
  BlobRegister     _blob_register;  /**< Register of data items.       */
  State*           _q0;             /**< Start state.                  */
  StateArray      *_queue;          /**< State queue.                  */
  bool             _finalized;      /**< Finalized flag.               */
  PackedAutomaton  _packed;         /**< Packed automaton.             */

  /**
   * @brief Get last state in common path.
   *
   * Get the last state of the common path shared by the current input
   * string and strings already in the automaton.  Also sets a pointer
   * to the suffix part of \a input which occurs after the last state.
   *
   * @param input Input string.
   * @return Pointer to last state in common path.
   */
  State* getCPLastState(const char *input, const char *&suffix);

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
    _register(NULL),
    _blob_register(),
    _q0(NULL),
    _queue(NULL),
    _finalized(false),
    _packed()
  { }

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

template<typename _Tp>
  inline bool
  operator==(const Automaton::MMapArenaAllocator<_Tp>&, const Automaton::MMapArenaAllocator<_Tp>&)
  { return true; }

template<typename _Tp>
  inline bool
  operator!=(const Automaton::MMapArenaAllocator<_Tp>&, const Automaton::MMapArenaAllocator<_Tp>&)
  { return false; }

} // namespace fsa

