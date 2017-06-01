// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "httpurlpath.h"
#include <vespa/vespalib/text/stringtokenizer.h>

namespace storage::framework {

HttpUrlPath::HttpUrlPath(const vespalib::string& urlpath)
    : _urlPath(urlpath),
      _path(),
      _attributes(),
      _serverSpec()
{
    init();
}

HttpUrlPath::HttpUrlPath(const vespalib::string& urlpath,
                         const vespalib::string& serverSpec)
    : _urlPath(urlpath),
      _path(),
      _attributes(),
      _serverSpec(serverSpec)
{
    init();
}

HttpUrlPath::~HttpUrlPath() {}

void
HttpUrlPath::init()
{
    vespalib::string::size_type pos = _urlPath.find('?');
    if (pos == vespalib::string::npos) {
        _path = _urlPath;
    } else {
        _path = _urlPath.substr(0, pos);
        vespalib::string sub(_urlPath.substr(pos+1));
        vespalib::StringTokenizer tokenizer(sub, "&", "");
        for (uint32_t i=0, n=tokenizer.size(); i<n; ++i) {
            const vespalib::string& s(tokenizer[i]);
            pos = s.find('=');
            if (pos == vespalib::string::npos) {
                _attributes[s] = "";
            } else {
                _attributes[s.substr(0,pos)] = s.substr(pos+1);
            }
        }
    }
}

bool
HttpUrlPath::hasAttribute(const vespalib::string& id) const
{
    return (_attributes.find(id) != _attributes.end());
}

const vespalib::string&
HttpUrlPath::getAttribute(const vespalib::string& id,
                          const vespalib::string& defaultValue) const
{
    std::map<vespalib::string, vespalib::string>::const_iterator it
            = _attributes.find(id);
    return (it == _attributes.end() ? defaultValue : it->second);
}

void
HttpUrlPath::print(std::ostream& out, bool, const std::string&) const
{
    out << _urlPath;
}

}
