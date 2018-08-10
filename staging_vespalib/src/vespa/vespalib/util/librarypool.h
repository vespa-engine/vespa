// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/fastos/dynamiclibrary.h>
#include <map>

namespace vespalib {

class LibraryPool
{
public:
    LibraryPool();
    ~LibraryPool();
    /**
     * This will load the library with the name given.
     * - It will verify linkage at load time.
     * - Symbols will be private.
     * @param name The name of the library to load. That is without the 'lib' prefix and the '.so' extension.
     * @throws IllegalArgumentException if there are any errors.
     */
    void loadLibrary(stringref  name);
    /**
     * Will return the library requested. NULL if not found.
     * @param name The name of the library as given in the @ref loadLibrary call.
     * @return The library that has already been loaded. NULL if not found.
     */
    FastOS_DynamicLibrary *get(stringref  name);
    const FastOS_DynamicLibrary *get(stringref  name) const;
private:
    typedef std::shared_ptr<FastOS_DynamicLibrary>      DynamicLibrarySP;
    typedef std::map<vespalib::string, DynamicLibrarySP>  LibraryMap;
    LibraryMap _libraries;
    Lock       _lock;
};

}

