// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "httpurlpath.h"
#include <vespa/vespalib/text/stringtokenizer.h>

namespace storage::framework {

HttpUrlPath::HttpUrlPath(const vespalib::string& urlpath)
    : _path(),
      _attributes(),
      _serverSpec()
{
    init(urlpath);
}

HttpUrlPath::HttpUrlPath(const vespalib::string& urlpath,
                         const vespalib::string& serverSpec)
    : _path(),
      _attributes(),
      _serverSpec(serverSpec)
{
    init(urlpath);
}

HttpUrlPath::HttpUrlPath(vespalib::string path,
                         std::map<vespalib::string, vespalib::string> attributes,
                         vespalib::string serverSpec)
    : _path(std::move(path)),
      _attributes(std::move(attributes)),
      _serverSpec(std::move(serverSpec))
{
}

HttpUrlPath::~HttpUrlPath() {}

void
HttpUrlPath::init(const vespalib::string &urlpath)
{
    vespalib::string::size_type pos = urlpath.find('?');
    if (pos == vespalib::string::npos) {
        _path = urlpath;
    } else {
        _path = urlpath.substr(0, pos);
        vespalib::string sub(urlpath.substr(pos+1));
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

vespalib::string
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
    out << _path;
    if (!_attributes.empty()) {
        out << "?";
        size_t cnt = 0;
        for (const auto &attr: _attributes) {
            if (cnt++ > 0) {
                out << "&";
            }
            out << attr.first;
            if (!attr.second.empty()) {
                out << "=";
                out << attr.second;
            }
        }
    }
}

}
