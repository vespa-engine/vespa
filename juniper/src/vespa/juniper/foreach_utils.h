// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <algorithm>

/** \if utils
 *  A simple general deleter object to be passed to for instance std::for_each
 *  to delete pointer referenced objects
 *  in STL containers.
 *
 */

struct Deleter
{
    template <typename T>
    void operator()(T* t) const
    {
        delete t;
    }
};

/*  \def Handy macro to delete all pointer objects in a container
 *  (using \a Deleter)
 */

#define delete_all(container) \
   std::for_each(container.begin(), container.end(), Deleter())


#define FunctionObj(name, func) \
  struct name \
  { \
    template <typename T> \
    void operator()(T* t) \
    { \
      t->func(); \
    } \
  }


#define for_all(container, obj) \
   std::for_each(container.begin(), container.end(), obj())

/** \endif (utils) */

