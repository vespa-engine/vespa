// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/10/01
 * @version $Id$
 * @file    conceptnetmanager.h
 * @brief   Concept network handle class definition.
 *
 */

#pragma once

#include <string>

#include "refcountable.h"
#include <vespa/fsa/conceptnet.h>

namespace fsa {

// {{{ class ConceptNet::Handle

/**
 * @class Handle
 * @brief Concept net handle.
 *
 * A Handle looks like a ConceptNet, but copies are cheap; the actual
 * ConceptNet objects are refcounted and Handle copies merely copy the
 * ConceptNet pointer and increment the refcount.
 */
class ConceptNet::Handle {

private:

  /**
   * @brief Unimplemented private default constructor.
   */
  Handle();
  /**
   * @brief Unimplemented private assignment operator.
   */
  Handle& operator=(const Handle&);

  class RefCountableConceptNet: public ConceptNet, public RefCountable<ConceptNet> {
  public:
    RefCountableConceptNet(const char *fsafile, const char *datafile=NULL, FileAccessMethod fam = FILE_ACCESS_UNDEF) : ConceptNet(fsafile,datafile,fam) {}
  };

  RefCountableConceptNet *_conceptNet; /**< The ConceptNet object itself. */

public:

  /**
   * @brief Copy constructor.
   *
   * Duplicate a handle (and add new reference to the ConceptNet object.
   *
   * @param h Reference to existing ConceptNet::Handle.
   */
  Handle(const Handle& h) : _conceptNet(h._conceptNet)
  {
    _conceptNet->addReference();
  }

  /**
   * @brief Constructor.
   *
   * @param fsafile %FSA file containing the units, with a perfect has
   *                (used for indexing the data file).
   * @param datafile Concept net data file.
   * @param fam File access mode (read or mmap). If not set, the
   *            global preferred access mode will be used.
   */
  Handle(const char *fsafile, const char *datafile=NULL, FileAccessMethod fam = FILE_ACCESS_UNDEF) :
    _conceptNet(new RefCountableConceptNet(fsafile,datafile,fam))
  {
    _conceptNet->addReference();
  }

  /**
   * @brief Constructor.
   *
   * @param fsafile %FSA file containing the units, with a perfect has
   *                (used for indexing the data file).
   * @param datafile Concept net data file.
   * @param fam File access mode (read or mmap). If not set, the
   *            global preferred access mode will be used.
   */
  Handle(const std::string &fsafile, const std::string &datafile=NULL, FileAccessMethod fam = FILE_ACCESS_UNDEF) :
    _conceptNet(new RefCountableConceptNet(fsafile.c_str(),datafile.c_str(),fam))
  {
    _conceptNet->addReference();
  }

  /**
   * @brief Destructor.
   */
  ~Handle(void)
  {
    _conceptNet->removeReference();
  }

  /**
   * @brief Dereference operator, provides access to ConceptNet
   *        methods.
   *
   * @return Reference to the ConceptNet object.
   */
  const ConceptNet& operator*() const { return *_conceptNet; }

  /**
   * @brief Dereference operator, provides access to ConceptNet
   *        methods.
   *
   * @return Pointer the ConceptNet object.
   */
  const ConceptNet* operator->() const { return _conceptNet; }

};

// }}}

} // namespace fsa

