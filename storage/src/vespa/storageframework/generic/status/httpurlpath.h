// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Utility class to parse the url-path part of an HTTP URL.
 * Used by status module.
 */

#pragma once

#include <vespa/vespalib/util/printable.h>
#include <vespa/vespalib/stllike/string.h>
#include <map>
#include <sstream>

namespace storage::framework {

class HttpUrlPath : public vespalib::Printable {
    vespalib::string _path;
    std::map<vespalib::string, vespalib::string> _attributes;
    vespalib::string _serverSpec; // "host:port"

    void init(const vespalib::string &urlpath);

public:
    HttpUrlPath(const vespalib::string& urlpath);
    HttpUrlPath(const vespalib::string& urlpath, const vespalib::string& serverSpec);
    HttpUrlPath(vespalib::string path,
                std::map<vespalib::string, vespalib::string> attributes,
                vespalib::string serverSpec);
    ~HttpUrlPath();

    const vespalib::string& getPath() const { return _path; }
    const std::map<vespalib::string, vespalib::string>& getAttributes() const
            { return _attributes; }

    bool hasAttribute(const vespalib::string& id) const;
    vespalib::string getAttribute(const vespalib::string& id,
                                  const vespalib::string& defaultValue = "") const;

    const vespalib::string& getServerSpec() const {
        return _serverSpec;
    }

    template<typename T>
    T get(const vespalib::string& id, const T& defaultValue = T()) const;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

template<typename T>
T HttpUrlPath::get(const vespalib::string& id, const T& defaultValue) const
{
    std::map<vespalib::string, vespalib::string>::const_iterator it = _attributes.find(id);
    if (it == _attributes.end()) return defaultValue;
    T val;
    std::istringstream ist(it->second);
    ist >> val;
    return val;
}

}
