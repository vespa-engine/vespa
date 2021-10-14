// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @author  Peter Boros
 * @date    2004/09/07
 * @version $Id$
 * @file    rwlock.cpp
 * @brief   Read-write lock.
 *
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifndef DISABLE_THREADS
#include <pthread.h>
#include <sched.h>
#include <cassert>
#endif

#include "rwlock.h"

namespace fsa {

// {{{ class RWLock::Impl

struct RWLock::Impl
{
#ifndef DISABLE_THREADS
  pthread_rwlock_t _rwlock;   /**< Lock.  */
#else
  int _rwlock;
#endif
};

// }}}

RWLock::RWLock(void) : _impl(new Impl)
{
#ifndef DISABLE_THREADS
  int rc;
  rc = pthread_rwlock_init(&(_impl->_rwlock),NULL);
  assert(rc == 0);
#endif
}

RWLock::~RWLock(void)
{
#ifndef DISABLE_THREADS
  pthread_rwlock_destroy(&(_impl->_rwlock));
#endif
  delete _impl;
}

bool RWLock::tryRdLock (void)
{
#ifndef DISABLE_THREADS
  return pthread_rwlock_tryrdlock(&(_impl->_rwlock)) == 0;
#else
  return true;
#endif
}

bool RWLock::tryWrLock (void)
{
#ifndef DISABLE_THREADS
  return pthread_rwlock_trywrlock(&(_impl->_rwlock)) == 0;
#else
  return true;
#endif
}

bool RWLock::rdLock (void)
{
#ifndef DISABLE_THREADS
  return pthread_rwlock_rdlock(&(_impl->_rwlock)) == 0;
#else
  return true;
#endif
}

bool RWLock::wrLock (void)
{
#ifndef DISABLE_THREADS
  return pthread_rwlock_wrlock(&(_impl->_rwlock)) == 0;
#else
  return true;
#endif
}

bool RWLock::unlock (void)
{
#ifndef DISABLE_THREADS
  return pthread_rwlock_unlock(&(_impl->_rwlock)) == 0;
#else
  return true;
#endif
}

} // namespace fsa
