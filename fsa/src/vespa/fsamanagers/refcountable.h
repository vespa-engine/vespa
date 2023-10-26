// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/08/20
 * @version $Id$
 * @file    refcountable.h
 * @brief   Reference countable template
 */

#pragma once

#include "mutex.h"

namespace fsa {

// {{{ class RefCountable

/**
 * @class RefCountable
 * @brief Reference countable template
 *
 * Subclass this template, and use the addReference and removeReference
 * methods to keep track of how many references the object has. When
 * the last reference is removed, the object blows up (well, destroys
 * itself).
 */
template <typename T>
class RefCountable
{
protected:

  /** Reference count */
  int      _refCount;

  /** Lock */
  Mutex    _sequencerLock;


  /**
   * @brief Destroy the object
   *
   * @return True.
   */
  virtual bool destroy(void)
  {
    delete this;
    return true;
  };

private:

  /** Unimplemented private copy constructor. */
  RefCountable(const RefCountable &original);
  /** Unimplemented private assignment operator. */
  const RefCountable& operator=(const RefCountable &original);

public:

  /**
   * @brief Constructor
   */
  RefCountable(void)
    : _refCount(0),
      _sequencerLock()
  {
  }

  /**
   * @brief Destructor
   */
  virtual ~RefCountable(void) {}

  /**
   * @brief Increase reference count.
   */
  virtual void addReference(void)
  {
    _sequencerLock.lock();
    _refCount++;
    _sequencerLock.unlock();
  }

  /**
   * @brief Decrease reference count, and destroy object if no
   *        references are left.
   *
   * @return True if the object was destroyed.
   */
  virtual bool removeReference(void)
  {
    bool destroyed = false;

    _sequencerLock.lock();
    _refCount--;

    if(_refCount<1){
      _sequencerLock.unlock();
      destroyed = destroy();
    }
    else{
      _sequencerLock.unlock();
    }
    return destroyed;
  }

};

// }}}

} // namespace fsa

