// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/****************************************************************-*-C++-*-
 * @file
 * Class definitions for FastOS_DynamicLibrary.
 *
 * @author  Eyvind Bernhardsen
 *
 * Creation date    : 2003-07-02
 *************************************************************************/



#pragma once


#include <vespa/fastos/types.h>
#include <string>

/**
 * This class contains functionality to load, get symbols from and
 * unload dynamic libraries.
 */

class FastOS_DynamicLibraryInterface
{
public:
    /**
     * Destructor.  The destructor will close the library if it is open.
     */
    virtual ~FastOS_DynamicLibraryInterface() {}

    /**
     * Open (load) the library.
     * @param libname the name of the library to open
     * @return Boolean success/failure
     */
    virtual bool Open(const char *libname = nullptr) = 0;

    /**
     * Close (unload) the library.
     * @return Boolean success/failure
     */
    virtual bool Close() = 0;

    /**
     * Find the address of a symbol in the library.
     * @param symbol Name of symbol to find
     * @return Address of the symbol, or nullptr if an error has occurred
     */
    virtual void * GetSymbol(const char *symbol) const = 0;

    /**
     * Check if the library is open.
     * @return true if it is, false if it ain't
     */
    virtual bool IsOpen() const = 0;

    /**
     * Return an error message describing the last error.  This is
     * currently platform-dependent, unfortunately; FastOS does not
     * normalize the error messages.
     * @return The error string if an error has occurred since the last
     *         invocation, or an empty one if no error has occurred.
     */
    std::string GetLastErrorString();
};


#  include "unix_dynamiclibrary.h"
typedef FastOS_UNIX_DynamicLibrary FASTOS_PREFIX(DynamicLibrary);

/*********************************************************************
 * Dynamic library helper macros:
 *
 * FASTOS_LOADABLE_EXPORT   prefix that marks a symbol to be exported
 * FASTOS_LOADABLE_IMPORT   prefix that marks a symbol to be imported
 *                          from a dll
 * FASTOS_LOADABLE_FACTORY  macro that creates and exports a function
 *                          called factory.  The macro takes a class
 *                          name as its only parameter, and the
 *                          factory function returns a pointer to an
 *                          instance of that class.
 *
 * Example usage:
 * loadableclass.h:
 * class FastOS_LoadableClass
 * {
 * public:
 *    void DoSomething();
 * }
 *
 * in loadableclass.cpp:
 * FASTOS_LOADABLE_FACTORY(LoadableClass)
 *********************************************************************/


#  define FASTOS_LOADABLE_EXPORT

#  define FASTOS_LOADABLE_IMPORT

#define FASTOS_LOADABLE_FACTORY(loadable_class)       \
extern "C" {                                          \
  FASTOS_LOADABLE_EXPORT loadable_class *factory() {  \
    return new loadable_class;                        \
  }                                                   \
}

// New macros to support the new gcc visibility features.

#define VESPA_DLL_EXPORT __attribute__ ((visibility("default")))
#define VESPA_DLL_LOCAL  __attribute__ ((visibility("hidden")))
