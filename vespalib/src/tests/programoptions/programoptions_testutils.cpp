// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "programoptions_testutils.h"

namespace vespalib {

namespace {
    std::vector<std::string> splitString(const std::string& source) {
        std::vector<std::string> target;
        std::string::size_type start = 0;
        std::string::size_type stop = source.find(' ');
        while (stop != std::string::npos) {
            target.push_back(source.substr(start, stop - start));
            start = stop + 1;
            stop = source.find(' ', start);
        }
        target.push_back(source.substr(start));
        return target;
    }
} // anonymous

AppOptions::AppOptions(const std::string& optString)
    : _argc(0), _argv(0), _source()
{
    _source = splitString(optString);
    _argc = _source.size();
    _argv = new const char*[_source.size()];
    for (int i=0; i<_argc; ++i) {
        if (_source[i].size() > 1
            && _source[i][0] == _source[i][_source[i].size() - 1]
            && (_source[i][0] == '\'' || _source[i][0] == '"'))
        {
            if (_source[i].size() == 2) {
                _source[i] = "";
            } else {
                _source[i] = _source[i].substr(1, _source.size() - 2);
            }
        }
        _argv[i] = _source[i].c_str();
    }
}

AppOptions::~AppOptions()
{
    delete[] _argv;
}

} // vespalib
