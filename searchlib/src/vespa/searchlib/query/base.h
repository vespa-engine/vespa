// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/fastlib/text/unicodeutil.h>
#include <vector>

namespace search {

/// Type of general unsigned 8 bit data.
typedef unsigned char byte;
/// A simple container for the raw querystack.
typedef vespalib::stringref QueryPacketT;
/// The type of the local documentId.
typedef unsigned DocumentIdT;
/// This is the type of the CollectionId used in the StorageAPI.
typedef uint64_t CollectionIdT;
/// The type to identify a query.
typedef unsigned QueryIdT;
/// The rank type.
typedef unsigned RankT;
/// How time type. Used to represent seconds since 1970.
typedef unsigned TimeT;
/// Type to identify performance counters.
typedef uint64_t CounterT;
/// Type to identify performance values.
typedef int ValueT;
/// This is a 16 byte vector used in SSE2 integer operations.
typedef char v16qi __attribute__ ((__vector_size__(16)));
/// This is a 2 element uint64_t vector used in SSE2 integer operations.
typedef long long v2di  __attribute__ ((__vector_size__(16)));
/// A type to represent a list of strings.
typedef std::vector<vespalib::string> StringListT;
/// A type to represent a vector of 32 bit signed integers.
typedef std::vector<int32_t> Int32ListT;
/// A type to represent a list of document ids.
typedef std::vector<DocumentIdT> DocumentIdList;

/// A debug macro the does "a" when l & the mask is true. The mask is set per file.
#define DEBUG(l, a) { if (l&DEBUGMASK) {a;} }
#ifdef __USE_RAWDEBUG__
  #define RAWDEBUG(a) a
#else
  #define RAWDEBUG(a)
#endif
/// A macro avoid warnings for unused parameters.
#define UNUSED_PARAM(p)
/// A macro that gives you number of elements in an array.
#define NELEMS(a)    (sizeof(a)/sizeof(a[0]))

/// A macro used in descendants of Object to instantiate the duplicate method.
#define DUPLICATE(a) a * duplicate() const override;
#define IMPLEMENT_DUPLICATE(a) a * a::duplicate() const { return new a(*this); }

/**
  This is a base class that ensures that all descendants can be duplicated.
  This implies also that they have a copy constructor.
  It also makes them streamable to an std:ostream.
*/
class Object
{
 public:
  virtual ~Object(void);
  /// Returns an allocated(new) object that is identical to this one.
  virtual Object * duplicate() const = 0;
  /// Gives you streamability of the object. Object does nothing.
  virtual vespalib::string toString() const;
};

/**
  This is a template that can hold any objects of any descendants of T.
  It does take a copy of the object. Very nice for holding different descendants
  and not have to worry about what happens on copy, assignment, destruction.
  No references, just simple copy.
  It gives you the -> and * operator so you can use it as a pointer to T.
  Very convenient.
*/
template <typename T>
class ObjectContainer
{
 public:
  ObjectContainer() : _p(NULL) { }
  ObjectContainer(const T & org) : _p(static_cast<T*>(org.duplicate())) { }
  ObjectContainer(const T * org) : _p(org ? static_cast<T*>(org->duplicate()) : NULL) { }
  ObjectContainer(const ObjectContainer & org) : _p(NULL) { *this = org; }
  ObjectContainer & operator = (const T * org) { cleanUp(); if (org) { _p = static_cast<T*>(org->duplicate()); } return *this; }
  ObjectContainer & operator = (const T & org) { cleanUp(); _p = static_cast<T*>(org.duplicate()); return *this; }
  ObjectContainer & operator = (const ObjectContainer & org) { if (this != & org) { cleanUp(); if (org._p) { _p = static_cast<T*>(org._p->duplicate());} } return *this; }
  virtual ~ObjectContainer() { cleanUp(); }
  bool valid()           const { return (_p != NULL); }
  T *operator->()              { return _p; }
  T &operator*()               { return *_p; }
  const T *operator->()  const { return _p; }
  const T &operator*()   const { return *_p; }
  operator T & ()        const { return *_p; }
  operator T * ()        const { return _p; }

 private:
  void cleanUp()             { delete _p; _p = NULL; }
  T * _p;
};

/**
  This is a template similar to ObjectContainer that frees you from the trouble
  of having to write you own copy/assignment operators when you use pointers as
  pure references. Adds one level of indirection, but that normally optimized
  away by the compiler. Can be used as an ordinary pointer since -> and * is
  overloaded.
*/
template <typename T>
class PointerContainer
{
 public:
  PointerContainer() : _p(NULL) { }
  PointerContainer(T & org) : _p(org) { }
  PointerContainer(T * org) : _p(org) { }
  PointerContainer(const PointerContainer & org) : _p(org._p) { }
  PointerContainer & operator = (T * org) { _p = org; return *this; }
  PointerContainer & operator = (T & org) { _p = &org; return *this; }
  PointerContainer & operator = (const PointerContainer & org) { if (this != & org) { _p = org._p;} return *this; }
  virtual ~PointerContainer() { _p = 0; }
  bool valid()         const { return (_p != NULL); }
  T *operator->()      const { return _p; }
  T &operator*()       const { return *_p; }
  operator T & ()      const { return *_p; }
  operator T * ()      const { return _p; }
 private:
  T * _p;
};

}

