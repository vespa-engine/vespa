// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Utility class to parse the url-path part of an HTTP URL.
 * Used by status module.
 */

#pragma once

#include <vespa/vespalib/util/printable.h>
#include <map>
#include <sstream>
#include <string>

namespace storage::framework {

class HttpUrlPath : public vespalib::Printable {
    std::string _path;
    std::map<std::string, std::string> _attributes;
    std::string _serverSpec; // "host:port"

    void init(const std::string &urlpath);

public:
    HttpUrlPath(const std::string& urlpath);
    HttpUrlPath(const std::string& urlpath, const std::string& serverSpec);
    HttpUrlPath(std::string path,
                std::map<std::string, std::string> attributes,
                std::string serverSpec);
    ~HttpUrlPath();

    const std::string& getPath() const { return _path; }
    const std::map<std::string, std::string>& getAttributes() const
            { return _attributes; }

    bool hasAttribute(const std::string& id) const;
    std::string getAttribute(const std::string& id,
                                  const std::string& defaultValue = "") const;

    const std::string& getServerSpec() const {
        return _serverSpec;
    }

    template<typename T>
    T get(const std::string& id, const T& defaultValue = T()) const;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

template<typename T>
T HttpUrlPath::get(const std::string& id, const T& defaultValue) const
{
    std::map<std::string, std::string>::const_iterator it = _attributes.find(id);
    if (it == _attributes.end()) return defaultValue;
    T val;
    std::istringstream ist(it->second);
    ist >> val;
    return val;
}

}
