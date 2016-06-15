// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/librarypool.h>
#include <vespa/vespalib/util/exceptions.h>

namespace vespalib {

LibraryPool::LibraryPool() :
    _libraries(),
    _lock()
{
}

LibraryPool::~LibraryPool()
{
    LockGuard guard(_lock);
    _libraries.clear();
}

void
LibraryPool::loadLibrary(const vespalib::stringref & libName)
{
    LockGuard guard(_lock);
    if (_libraries.find(libName) == _libraries.end()) {
        DynamicLibrarySP lib(new FastOS_DynamicLibrary);
        vespalib::string file(libName);
        if (!lib->Open(file.c_str())) {
            vespalib::string error = lib->GetLastErrorString();
            throw IllegalArgumentException(make_string("Failed loading dynamic library '%s' due to '%s'.",
                      file.c_str(), error.c_str()));
        } else {
            _libraries[libName] = lib;
        }
    }
}

FastOS_DynamicLibrary *
LibraryPool::get(const vespalib::stringref & name)
{
    LockGuard guard(_lock);
    LibraryMap::const_iterator found(_libraries.find(name));
    return (found != _libraries.end())
               ? found->second.get()
               : NULL;
}

const FastOS_DynamicLibrary *
LibraryPool::get(const vespalib::stringref & name) const
{
    LockGuard guard(_lock);
    LibraryMap::const_iterator found(_libraries.find(name));
    return (found != _libraries.end())
               ? found->second.get()
               : NULL;
}

}
