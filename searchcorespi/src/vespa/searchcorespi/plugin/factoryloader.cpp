// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "factoryloader.h"
#include <vespa/vespalib/util/exceptions.h>

using vespalib::stringref;
using vespalib::make_string;
using vespalib::IllegalArgumentException;

namespace searchcorespi {

FactoryLoader::FactoryLoader() :
    _libraries()
{
}

FactoryLoader::~FactoryLoader() = default;

IIndexManagerFactory::UP
FactoryLoader::create(stringref factory)
{
    typedef IIndexManagerFactory* (*FuncT)();
    _libraries.loadLibrary(factory);
    const FastOS_DynamicLibrary & lib = *_libraries.get(factory);
    FuncT registrationMethod = reinterpret_cast<FuncT>(lib.GetSymbol("createIndexManagerFactory"));
    if (registrationMethod == nullptr) {
        throw IllegalArgumentException(make_string("Failed locating symbol 'createIndexManagerFactory' in library '%s' for factory '%s'.",
                                                   lib.GetLibName(), vespalib::string(factory).c_str()));
    }
    return IIndexManagerFactory::UP(registrationMethod());
}

}
