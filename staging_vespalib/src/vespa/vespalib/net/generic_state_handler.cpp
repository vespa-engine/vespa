// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic_state_handler.h"
#include <vespa/vespalib/data/slime/slime.h>

namespace vespalib {

namespace {

std::vector<vespalib::string> split_path(const vespalib::string &path) {
    vespalib::string tmp;
    std::vector<vespalib::string> items;
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

bool is_prefix(const std::vector<vespalib::string> &root, const std::vector<vespalib::string> &full) {
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

vespalib::string make_url(const vespalib::string &host, const std::vector<vespalib::string> &items) {
    vespalib::string url = "http://" + host;
    if (items.empty()) {
        url += "/";
    }
    for (const vespalib::string &item: items) {
        url += "/" + item;
    }
    return url;
}

void inject_children(const StateExplorer &state, const vespalib::string &url, slime::Cursor &self);

Slime child_state(const StateExplorer &state, const vespalib::string &url) {
    Slime child_state;
    state.get_state(slime::SlimeInserter(child_state), false);
    if (child_state.get().type().getId() == slime::NIX::ID) {
        inject_children(state, url, child_state.setObject());
    } else {
        child_state.get().setString("url", url);
    }
    return child_state;
}

void inject_children(const StateExplorer &state, const vespalib::string &url, slime::Cursor &self) {
    std::vector<vespalib::string> children_names = state.get_children_names();
    for (const vespalib::string &child_name: children_names) {
        std::unique_ptr<StateExplorer> child = state.get_child(child_name);
        if (child) {
            vespalib::string child_url = url + "/" + child_name;
            Slime fragment = child_state(*child, child_url);
            slime::inject(fragment.get(), slime::ObjectInserter(self, child_name));
        }
    }
}

vespalib::string render(const StateExplorer &state, const vespalib::string &url) {
    Slime top;
    state.get_state(slime::SlimeInserter(top), true);
    if (top.get().type().getId() == slime::NIX::ID) {
        top.setObject();
    }
    inject_children(state, url, top.get());
    slime::SimpleBuffer buf;
    slime::JsonFormat::encode(top, buf, true);
    return buf.get().make_string();
}

vespalib::string explore(const StateExplorer &state, const vespalib::string &host,
                         const std::vector<vespalib::string> &items, size_t pos) {
    if (pos == items.size()) {
        return render(state, make_url(host, items));
    }
    std::unique_ptr<StateExplorer> child = state.get_child(items[pos]);
    if (!child) {
        return "";
    }
    return explore(*child, host, items, pos + 1);
}

} // namespace vespalib::<unnamed>

GenericStateHandler::GenericStateHandler(const vespalib::string &root_path, const StateExplorer &state)
    : _root(split_path(root_path)),
      _state(state)
{
}

vespalib::string
GenericStateHandler::get(const vespalib::string &host,
                         const vespalib::string &path,
                         const std::map<vespalib::string,vespalib::string> &) const
{
    std::vector<vespalib::string> items = split_path(path);
    if (!is_prefix(_root, items)) {
        return "";
    }
    return explore(_state, host, items, _root.size());
}

} // namespace vespalib
