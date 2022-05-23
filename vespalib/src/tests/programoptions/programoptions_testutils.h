// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This class contains some test utilities, to create argc/argv inputs for
 * application tests.
 */

#pragma once

#include <string>
#include <vector>

namespace vespalib {

class AppOptions {
    int _argc;
    const char** _argv;
    std::vector<std::string> _source;

    AppOptions(const AppOptions&);
    AppOptions& operator=(const AppOptions&);

public:
    AppOptions(const std::string& optString);
    ~AppOptions();

    int getArgCount() const { return _argc; }
    const char* const* getArguments() const { return _argv; }

};

} // vespalib

