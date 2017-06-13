// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nodestate.h"
#include "urlencoder.h"

#include <vespa/log/log.h>
LOG_SETUP(".nodestate");

using namespace documentapi;

NodeState::NodeState() :
    _parent(NULL),
    _id(""),
    _children(),
    _state()
{
    // empty
}

NodeState::NodeState(const NodeState &rhs) :
    _parent(rhs._parent),
    _id(rhs._id),
    _children(rhs._children),
    _state(rhs._state)
{
    // empty
}

NodeState::NodeState(StateMap args) :
    _parent(NULL),
    _id(""),
    _children(),
    _state(args)
{
    // empty
}

NodeState::~NodeState() { }

NodeState &
NodeState::addChild(const string &key, const NodeState &child)
{
    getChild(key, true)->copy(child);
    return *this;
}

NodeState *
NodeState::getChild(const string &key, bool force)
{
    if (key.empty()) {
        return this;
    }

    // Find first not-self location item.
    size_t from = 0, to = key.find('/');
    while (to != string::npos && key.substr(from, to - from) == ".") {
        from = to + 1;
        to = key.find('/', from);
    }
    string arr0 = to != string::npos ? key.substr(from, to - from) : key.substr(from);
    string arr1 = to != string::npos ? key.substr(to + 1) : "";

    // Reference this or parent.
    if (arr0 == ".") {
        return this;
    }
    if (arr0 == "..") {
        if (_parent == NULL) {
            LOG(error, "Location string '%s' requests a parent above the top-most node, returning self to avoid crash.",
                key.c_str());
            return this;
        }
        return _parent->getChild(arr1, force);
    }

    // Look for child, forcing it if requested.
    ChildMap::iterator it = _children.find(arr0);
    if (it == _children.end()) {
        if (!force) {
            return NULL;
        }
        _children[arr0] = NodeState::SP(new NodeState());
        _children[arr0]->setParent(*this, arr0);
    }
    if (to != string::npos) {
        return _children[arr0]->getChild(arr1, force);
    }
    return _children[arr0].get();
}

const NodeState::ChildMap &
NodeState::getChildren() const
{
    return _children;
}

NodeState &
NodeState::removeChild(const string &key)
{
    if (key.empty()) {
        return *this;
    }
    size_t pos = key.find_last_of('/');
    if (pos != string::npos) {
        NodeState* parent = getChild(key.substr(0, pos), false);
        if (parent != NULL) {
            return parent->removeChild(key.substr(pos + 1));
        }
    }
    else {
        _children.erase(key);
    }
    return compact();
}

const string
NodeState::getState(const string &key)
{
    if (key.empty()) {
        return "";
    }
    size_t pos = key.find_last_of('/');
    if (pos != string::npos) {
        NodeState* parent = getChild(key.substr(0, pos), false);
        return parent != NULL ? parent->getState(key.substr(pos + 1)) : "";
    }
    StateMap::iterator it = _state.find(key);
    return it != _state.end() ? it->second : "";
}

NodeState &
NodeState::setState(const string &key, const string &value)
{
    if (key.empty()) {
        return *this;
    }
    size_t pos = key.find_last_of('/');
    if (pos != string::npos) {
        getChild(key.substr(0, pos), true)->setState(key.substr(pos + 1), value);
    }
    else {
        if (value.empty()) {
            return removeState(key);
        }
        else {
            _state[key] = value;
        }
    }
    return *this;
}

NodeState &
NodeState::removeState(const string &key)
{
    if (key.empty()) {
        return *this;
    }
    size_t pos = key.find_last_of('/');
    if (pos != string::npos) {
        NodeState* parent = getChild(key.substr(0, pos), false);
        if (parent != NULL) {
            return parent->removeState(key.substr(pos + 1));
        }
    }
    else {
        _state.erase(key);
    }
    return compact();
}

NodeState &
NodeState::compact()
{
    if (_state.empty() && _children.empty()) {
        if (_parent != NULL) {
            return _parent->removeChild(_id);
        }
    }
    return *this;
}

NodeState &
NodeState::copy(const NodeState &node)
{
    for (StateMap::const_iterator it = node._state.begin();
         it != node._state.end(); ++it) {
        _state[it->first] = it->second;
    }
    for (ChildMap::const_iterator it = node._children.begin();
         it != node._children.end(); ++it) {
        getChild(it->first, true)->copy(*it->second);
    }
    return *this;
}

NodeState &
NodeState::clear()
{
    _state.clear();
    _children.clear();
    return compact();
}

NodeState &
NodeState::setParent(NodeState &parent, const string &id)
{
    _parent = &parent;
    _id = id;
    return *this;
}

const string
NodeState::toString() const
{
    const std::string ret = toString("");
    size_t pos = ret.find_last_not_of(' ');
    return pos != string::npos ? ret.substr(0, pos + 1) : ret;
}

const string
NodeState::toString(const string &prefix) const
{
    string ret;
    if (!_state.empty()) {
        string str;
        for (StateMap::const_iterator it = _state.begin();
             it != _state.end(); ++it) {
            str += it->first + "=" + URLEncoder::encode(it->second) + "&";
        }
        ret += (prefix.empty() ? ".?" : prefix + "?") + str.substr(0, str.size() - 1) + " ";
    }
    string pre = prefix.empty() ? "" : (prefix + "/");
    for (ChildMap::const_iterator it = _children.begin();
         it != _children.end(); ++it) {
        ret += it->second->toString(pre + URLEncoder::encode(it->first));
    }
    return ret;
}
