// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/07
 * @version $Id$
 * @file    rwlock.h
 * @brief   Read-write lock.
 *
 */

#pragma once

namespace fsa {

// {{{ class RWLock

/**
 * @class RWLock
 * @brief Read-write lock.
 *
 * Simple read-write lock class based on POSIX pthread_rwlock_t.
 */
class RWLock
{
 protected:
  struct Impl;
  Impl *_impl;

 public:

  /**
   * @brief Constructor.
   */
  RWLock();

  /**
   * @brief Destructor.
   */
  ~RWLock();

  /**
   * @brief Try to get a read (shared) lock.
   *
   * Try to get a read (shared) lock. This method is non-blocking, and
   * returns true if locking was succesful.
   *
   * @return True if locking was successful.
   */
  bool tryRdLock ();

  /**
   * @brief Try to get a write (exclusive) lock.
   *
   * Try to get a write (exclusive) lock. This method is non-blocking, and
   * returns true if locking was succesful.
   *
   * @return True if locking was successful.
   */
  bool tryWrLock ();

  /**
   * @brief Get a read (shared) lock.
   *
   * Get a read (shared) lock. This method blocks until a shared
   * lock is available (that is no other thread holds an exclusive
   * lock on the object.)
   *
   * @return True if locking was successful.
   */
  bool rdLock ();

  /**
   * @brief Get a write (exclusive) lock.
   *
   * Get a write (exclusive) lock. This method blocks until an
   * exclusive lock is available (that is no other thread holds a
   * shared or an exclusive lock on the object.)
   *
   * @return True if locking was successful.
   */
  bool wrLock ();

  /**
   * @brief Release a (shared or exclusive) lock.
   *
   * @return True if unlocking was successful.
   */
  bool unlock ();

};

// }}}

} // namespace fsa

