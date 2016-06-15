// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

// This is a compile-time test which should fail because undef<T>
// cannot be instantiated

#include <stdlib.h>

template <class T> class undef;

struct A {
  A() { throw 1; }
};

template<typename T> class Pool { };

template<typename T>
inline void *operator new(size_t size,Pool<T>& pool)
{
  return malloc(size);
}

template<typename T>
inline void operator delete(void *p,Pool<T>& pool)
{
  undef<T> t;
  free(p);
}

int main ()
{
  Pool<int> pool;
  new (pool) A();
  return 0;
}
