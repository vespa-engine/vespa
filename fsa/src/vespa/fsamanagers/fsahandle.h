// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/07
 * @version $Id$
 * @file    fsamanager.h
 * @brief   FSA handle class definition.
 *
 */

#pragma once

#include <string>

#include "refcountable.h"
#include <vespa/fsa/fsa.h>

namespace fsa {

// {{{ FSA::Handle

/**
 * @class Handle
 * @brief FSA accessor.
 *
 * A Handle looks like an FSA, but copies are cheap; the actual FSA
 * objects are refcounted and Handle copies merely copy the FSA pointer
 * and increment the refcount.
 */
class FSA::Handle {

private:

  /**
   * @brief Unimplemented private default constructor.
   */
  Handle();
  /**
   * @brief Unimplemented private assignment operator.
   */
  Handle& operator=(const Handle&);

  class RefCountableFSA: public FSA, public RefCountable<FSA> {
  public:
    RefCountableFSA(const char *file, FileAccessMethod fam = FILE_ACCESS_UNDEF) : FSA(file,fam) {}
  };

  RefCountableFSA *_fsa; /**< The FSA object itself. */

  /**
   * @brief Get a pointer to the referred FSA object.
   *
   * @return pointer to the referred FSA object.
   */
  const FSA* getFSA() const
  {
    return _fsa;
  }

public:

  /**
   * @brief Copy constructor.
   *
   * Duplicate a handle (and add new reference to the FSA object.
   *
   * @param h Reference to handle to duplicate.
   */
  Handle(const Handle& h) : _fsa(h._fsa)
  {
    _fsa->addReference();
  }

  /**
   * @brief Constructor.
   *
   * Create a new FSA object (loaded from file) and add reference.
   *
   * @param file Name of the file containing the automaton.
   * @param fam File access mode (read or mmap). If not set, the
   *            global preferred access mode will be used.
   */
  Handle(const char *file, FileAccessMethod fam = FILE_ACCESS_UNDEF) :
    _fsa(new RefCountableFSA(file,fam))
  {
    _fsa->addReference();
  }

  /**
   * @brief Constructor.
   *
   * Create a new FSA object (loaded from file) and add reference.
   *
   * @param file Name of the file containing the automaton.
   * @param fam File access mode (read or mmap). If not set, the
   *            global preferred access mode will be used.
   */
  Handle(const std::string &file, FileAccessMethod fam = FILE_ACCESS_UNDEF) :
    _fsa(new RefCountableFSA(file.c_str(),fam))
  {
    _fsa->addReference();
  }

  /**
   * @brief Destructor.
   *
   * Remove reference to the FSA object.
   */
  ~Handle(void)
  {
    _fsa->removeReference();
  }

  /**
   * @brief Dereference operator, provides access to Metadata
   *        methods.
   *
   * @return Reference to the Metadata object.
   */
  const FSA& operator*() const { return *_fsa; }

  /**
   * @brief Dereference operator, provides access to Metadata
   *        methods.
   *
   * @return Pointer the Metadata object.
   */
  const FSA* operator->() const { return _fsa; }

  /**
   * @brief Check if %FSA was properly constructed.
   *
   * @return true iff underlying %FSA was properly constructed.
   */
  bool isOk(void) const
  {
    return _fsa->isOk();
  }

  /**
   * @brief Get the fsa library version used for building this %FSA.
   *
   * @return fsa library version.
   */
  uint32_t version(void) const
  {
    return _fsa->version();
  }

  /**
   * @brief Get the serial number of the %FSA.
   *
   * @return Serial number.
   */
  uint32_t serial(void) const
  {
    return _fsa->serial();
  }

  /**
   * @brief Check is the automaton has perfect hash built in.
   *
   * Returns true if the automaton was built with a perfect hash included.
   *
   * @return True if the automaton has perfect hash.
   */
  bool hasPerfectHash() const
  {
    return _fsa->hasPerfectHash();
  }

  /**
   * @brief Get iterator pointing to the beginning of the fsa.
   *
   * @return iterator pointing to the first string in the fsa.
   */
  FSA::iterator begin() const { return FSA::iterator(_fsa); }

  /**
   * @brief Get iterator pointing past the end of the fsa.
   *
   * @return iterator pointing past the last string in the fsa.
   */
  FSA::iterator end() const { return FSA::iterator(_fsa,true); }

};

// }}}

} // namespace fsa

