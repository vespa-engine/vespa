// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/07
 * @version $Id$
 * @file    rwlock.cpp
 * @brief   Read-write lock.
 *
 */

#include <pthread.h>
#include <sched.h>
#include <cassert>

#include "rwlock.h"

namespace fsa {

// {{{ class RWLock::Impl

struct RWLock::Impl
{
  pthread_rwlock_t _rwlock;   /**< Lock.  */
};

// }}}

RWLock::RWLock(void) : _impl(new Impl)
{
  int rc;
  rc = pthread_rwlock_init(&(_impl->_rwlock),NULL);
  assert(rc == 0);
}

RWLock::~RWLock(void)
{
  pthread_rwlock_destroy(&(_impl->_rwlock));
  delete _impl;
}

bool RWLock::tryRdLock (void)
{
  return pthread_rwlock_tryrdlock(&(_impl->_rwlock)) == 0;
}

bool RWLock::tryWrLock (void)
{
  return pthread_rwlock_trywrlock(&(_impl->_rwlock)) == 0;
}

bool RWLock::rdLock (void)
{
  return pthread_rwlock_rdlock(&(_impl->_rwlock)) == 0;
}

bool RWLock::wrLock (void)
{
  return pthread_rwlock_wrlock(&(_impl->_rwlock)) == 0;
}

bool RWLock::unlock (void)
{
  return pthread_rwlock_unlock(&(_impl->_rwlock)) == 0;
}

} // namespace fsa
