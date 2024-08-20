// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_state_handler.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/simple_buffer.h>

namespace vespalib {

namespace {

// escape a path component in the URL
// (needed to avoid java.net.URI throwing an exception)

std::string url_escape(const std::string &item) {
    static const char hexdigits[] = "0123456789ABCDEF";
    std::string r;
    r.reserve(item.size());
    for (const char c : item) {
        if (   ('a' <= c && c <= 'z')
            || ('0' <= c && c <= '9')
            || ('A' <= c && c <= 'Z')
            || (c == '_')
            || (c == '-'))
        {
            r += c;
        } else {
            r += '%';
            r += hexdigits[0xF & (c >> 4)];
            r += hexdigits[0xF & c];
        }
    }
    return r;
}

class Url {
private:
    std::string _url;
    void append(const std::string &item) {
        if (*_url.rbegin() != '/') {
            _url += '/';
        }
        _url.append(url_escape(item));
    }
public:
    Url(const std::string &host, const std::vector<std::string> &items)
        : _url("http://")
    {
        _url.append(host);
        _url += '/';
        for (const auto &item: items) {
            append(item);
        }
    }
    Url(const Url &parent, const std::string &item)
        : _url(parent._url)
    {
        append(item);
    }
    const std::string &get() const { return _url; }
};

std::vector<std::string> split_path(const std::string &path) {
    std::string tmp;
    std::vector<std::string> items;
    for (size_t i = 0; (i < path.size()) && (path[i] != '?'); ++i) {
        if (path[i] == '/') {
            if (!tmp.empty()) {
                items.push_back(tmp);
                tmp.clear();
            }
        } else {
            tmp.push_back(path[i]);
        }
    }
    if (!tmp.empty()) {
        items.push_back(tmp);
    }
    return items;
}

bool is_prefix(const std::vector<std::string> &root, const std::vector<std::string> &full) {
    if (root.size() > full.size()) {
        return false;
    }
    for (size_t i = 0; i < root.size(); ++i) {
        if (root[i] != full[i]) {
            return false;
        }
    }
    return true;
}

void inject_children(const StateExplorer &state, const Url &url, slime::Cursor &self);

Slime child_state(const StateExplorer &state, const Url &url) {
    Slime child_state;
    state.get_state(slime::SlimeInserter(child_state), false);
    if (child_state.get().type().getId() == slime::NIX::ID) {
        inject_children(state, url, child_state.setObject());
    } else {
        child_state.get().setString("url", url.get());
    }
    return child_state;
}

void inject_children(const StateExplorer &state, const Url &url, slime::Cursor &self) {
    std::vector<std::string> children_names = state.get_children_names();
    for (const std::string &child_name: children_names) {
        std::unique_ptr<StateExplorer> child = state.get_child(child_name);
        if (child) {
            Slime fragment = child_state(*child, Url(url, child_name));
            slime::inject(fragment.get(), slime::ObjectInserter(self, child_name));
        }
    }
}

std::string render(const StateExplorer &state, const Url &url) {
    Slime top;
    state.get_state(slime::SlimeInserter(top), true);
    if (top.get().type().getId() == slime::NIX::ID) {
        top.setObject();
    }
    inject_children(state, url, top.get());
    SimpleBuffer buf;
    slime::JsonFormat::encode(top, buf, true);
    return buf.get().make_string();
}

JsonGetHandler::Response explore(const StateExplorer &state, const std::string &host,
                                 const std::vector<std::string> &items, size_t pos) {
    if (pos == items.size()) {
        return JsonGetHandler::Response::make_ok_with_json(render(state, Url(host, items)));
    }
    std::unique_ptr<StateExplorer> child = state.get_child(items[pos]);
    if (!child) {
        return JsonGetHandler::Response::make_not_found();
    }
    return explore(*child, host, items, pos + 1);
}

} // namespace vespalib::<unnamed>

GenericStateHandler::GenericStateHandler(const std::string &root_path, const StateExplorer &state)
    : _root(split_path(root_path)),
      _state(state)
{
}

JsonGetHandler::Response
GenericStateHandler::get(const std::string &host,
                         const std::string &path,
                         const std::map<std::string,std::string> &,
                         const net::ConnectionAuthContext &) const
{
    std::vector<std::string> items = split_path(path);
    if (!is_prefix(_root, items)) {
        return Response::make_not_found();
    }
    return explore(_state, host, items, _root.size());
}

} // namespace vespalib
