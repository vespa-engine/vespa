// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/dynamiclibrary.h>
#include <vespa/fastos/file.h>

#include <dlfcn.h>

namespace {
const std::string FASTOS_DYNLIB_PREFIX("lib");
const std::string FASTOS_DYNLIB_SUFFIX(".so");
const std::string FASTOS_DYNLIB_SUFPREFIX(".so.");

bool hasValidSuffix(const std::string & s)
{
    return (s.rfind(FASTOS_DYNLIB_SUFFIX) == (s.size() - FASTOS_DYNLIB_SUFFIX.size()))
           || (s.rfind(FASTOS_DYNLIB_SUFPREFIX) != std::string::npos);
}

}

void
FastOS_UNIX_DynamicLibrary::SetLibName(const char *libname)
{
    if (libname != NULL) {
        _libname = libname;
        if ( ! hasValidSuffix(_libname)) {
            _libname.append(FASTOS_DYNLIB_SUFFIX);
        }
    } else {
        _libname = "";
    }
}

bool
FastOS_UNIX_DynamicLibrary::NormalizeLibName(void)
{
    bool returnCode = false;
    std::string::size_type pathPos = _libname.rfind(FastOS_File::GetPathSeparator()[0]);
    std::string tmp = (pathPos != std::string::npos)
                      ? _libname.substr(pathPos+1)
                      : _libname;
    if (tmp.find(FASTOS_DYNLIB_PREFIX) != 0) {
        tmp = FASTOS_DYNLIB_PREFIX + tmp;
        if (pathPos != std::string::npos) {
            tmp = _libname.substr(0, pathPos);
        }
        SetLibName(tmp.c_str());
        returnCode = true;
    }

    return returnCode;
}

bool
FastOS_UNIX_DynamicLibrary::Close()
{
    bool retcode = true;

    if (IsOpen()) {
        retcode = (dlclose(_handle) == TRUE);
        if (retcode)
            _handle = NULL;
    }

    return retcode;
}

FastOS_UNIX_DynamicLibrary::FastOS_UNIX_DynamicLibrary(const char *libname) :
    _handle(NULL),
    _libname("")
{
    SetLibName(libname);
}

FastOS_UNIX_DynamicLibrary::~FastOS_UNIX_DynamicLibrary()
{
    Close();
}

bool
FastOS_UNIX_DynamicLibrary::Open(const char *libname)
{
    if (! Close())
        return false;
    if (libname != NULL) {
        SetLibName(libname);
    }

    _handle = dlopen(_libname.c_str(), RTLD_NOW);

    if (_handle == NULL) {
        // Prepend "lib" if neccessary...
        if (NormalizeLibName()) {
            // ...try to open again if a change was made.
            _handle = dlopen(_libname.c_str(), RTLD_NOW);
        }
    }

    return (_handle != NULL);
}

void *
FastOS_UNIX_DynamicLibrary::GetSymbol(const char *symbol) const
{
    return dlsym(_handle, symbol);
}

std::string
FastOS_UNIX_DynamicLibrary::GetLastErrorString() const
{
    const char *errorString = dlerror();
    std::string e;
    if (errorString != NULL) {
        e = errorString;
    }

   return e;
}
