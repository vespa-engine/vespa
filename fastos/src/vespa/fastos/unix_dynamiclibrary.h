// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*************************************************************-*C++-*-
* @author Eyvind Bernhardsen
* @date   Creation date: 2003-07-02
* @file
* Class definitions for FastOS_Unix_DynamicLibrary
*********************************************************************/



#pragma once


#include <vespa/fastos/types.h>

class FastOS_UNIX_DynamicLibrary : public FastOS_DynamicLibraryInterface
{
private:
    FastOS_UNIX_DynamicLibrary(const FastOS_UNIX_DynamicLibrary&);
    FastOS_UNIX_DynamicLibrary& operator=(const FastOS_UNIX_DynamicLibrary&);

    void        *_handle;
    std::string  _libname;

public:
    FastOS_UNIX_DynamicLibrary(const char *libname = nullptr);
    ~FastOS_UNIX_DynamicLibrary();

    void SetLibName(const char *libname);
    bool NormalizeLibName(void);
    bool Close() override;
    bool Open(const char *libname = nullptr) override;
    void * GetSymbol(const char *symbol) const override;
    std::string GetLastErrorString() const;
    const char * GetLibName() const { return _libname.c_str(); }
    bool IsOpen()             const override { return (_handle != nullptr); }
};


