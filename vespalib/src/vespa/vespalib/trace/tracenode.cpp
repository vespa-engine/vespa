// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tracenode.h"
#include "tracevisitor.h"
#include <algorithm>

#include <vespa/log/log.h>

LOG_SETUP(".tracenode");

namespace vespalib {

namespace {

struct Cmp {
    bool operator()(const vespalib::TraceNode &lhs,
                    const vespalib::TraceNode &rhs)
    {
        if (lhs.isLeaf() || rhs.isLeaf()) {
            if (lhs.isLeaf() && rhs.isLeaf()) {
                return (lhs.getNote() < rhs.getNote());
            } else {
                return lhs.isLeaf();
            }
        }
        if (lhs.getNumChildren() != rhs.getNumChildren()) {
            return lhs.getNumChildren() < rhs.getNumChildren();
        }
        for (uint32_t i = 0, len = lhs.getNumChildren(); i < len; ++i) {
            bool aLess = operator()(lhs.getChild(i), rhs.getChild(i));
            bool bLess = operator()(rhs.getChild(i), lhs.getChild(i));
            if (aLess || bLess) {
                return aLess;
            }
        }
        return false;
    }
};

void sortChildren(std::vector<TraceNode> & children) __attribute((noinline));
void
sortChildren(std::vector<TraceNode> & children) {
    std::sort(children.begin(), children.end(), Cmp());
}

} // namespace <unnamed>


TraceNode::TraceNode() noexcept
    : _note(""),
      _children(),
      _parent(nullptr),
      _timestamp(),
      _strict(true),
      _hasNote(false)
{ }

TraceNode::TraceNode(const TraceNode &rhs)
    : _note(rhs._note),
      _children(),
      _parent(nullptr),
      _timestamp(rhs._timestamp),
      _strict(rhs._strict),
      _hasNote(rhs._hasNote)
{
    addChildren(rhs._children);
}

TraceNode::TraceNode(TraceNode &&) noexcept = default;
TraceNode & TraceNode::operator =(TraceNode &&) noexcept = default;
TraceNode & TraceNode::operator =(const TraceNode &) = default;

TraceNode::~TraceNode() = default;

TraceNode::TraceNode(const string &note, system_time timestamp)
    : _note(note),
      _children(),
      _parent(nullptr),
      _timestamp(timestamp),
      _strict(true),
      _hasNote(true)
{ }

TraceNode::TraceNode(system_time timestamp) noexcept
    : _note(""),
      _children(),
      _parent(nullptr),
      _timestamp(timestamp),
      _strict(true),
      _hasNote(false)
{ }

TraceNode &
TraceNode::swap(TraceNode &other)
{
    std::swap(_parent, other._parent);
    std::swap(_strict, other._strict);
    std::swap(_hasNote, other._hasNote);
    _note.swap(other._note);
    _children.swap(other._children);
    for (auto & child : _children) {
        child._parent = this;
    }
    for (auto & child : other._children) {
        child._parent = &other;
    }
    std::swap(_timestamp, other._timestamp);
    return *this;
}

TraceNode &
TraceNode::clear()
{
    _parent = nullptr;
    _strict = true;
    _hasNote = false;
    _note.clear();
    _children.clear();
    _timestamp = system_time();
    return *this;
}

TraceNode &
TraceNode::sort()
{
    if (!isLeaf()) {
        for (auto & child : _children) {
            child.sort();
        }
        if (!isStrict()) {
            sortChildren(_children);
        }
    }
    return *this;
}

TraceNode &
TraceNode::compact()
{
    if (isLeaf()) {
        return *this;
    }
    std::vector<TraceNode> tmp;
    tmp.swap(_children);
    for (auto & child : tmp)
    {
        child.compact();
        if (child.isEmpty()) {
            // ignore
        } else if (child.isLeaf()) {
            addChild(child);
        } else if (_strict == child._strict) {
            addChildren(child._children);
        } else if (child.getNumChildren() == 1) {
            const TraceNode &grandChild = child.getChild(0);
            if (grandChild.isEmpty()) {
                // ignore
            } else if (grandChild.isLeaf() || _strict != grandChild._strict) {
                addChild(grandChild);
            } else {
                addChildren(grandChild._children);
            }
        } else {
            addChild(child);
        }
    }
    return *this;
}

TraceNode &
TraceNode::normalize()
{
    compact();
    sort();
    if (_hasNote || !_strict) {
        TraceNode child;
        child.swap(*this);
        addChild(child);
        _strict = true;
    }
    return *this;
}

TraceNode &
TraceNode::addChild(const string &note)
{
    return addChild(TraceNode(note, system_time()));
}

TraceNode &
TraceNode::addChild(const string &note, system_time timestamp)
{
    return addChild(TraceNode(note, timestamp));
}

TraceNode &
TraceNode::addChild(TraceNode child)
{
    LOG_ASSERT(!_hasNote);
    _children.emplace_back(std::move(child));
    _children.back()._parent = this;
    return *this;
}

TraceNode &
TraceNode::addChildren(std::vector<TraceNode> children)
{
    for (auto & child : children) {
        addChild(std::move(child));
    }
    return *this;
}

string
TraceNode::toString(size_t limit) const
{
    string str;
    if (!writeString(str, 0, limit)) {
        str.append("...\n");
    }
    return str;
}

bool
TraceNode::writeString(string &dst, size_t indent, size_t limit) const
{
    if (dst.size() >= limit) {
        return false;
    }
    string pre(indent, ' ');
    if (_hasNote) {
        dst.append(pre).append(_note).append("\n");
        return true;
    }
    string name = isStrict() ? "trace" : "fork";
    dst.append(pre).append("<").append(name).append(">\n");
    for (const auto & child : _children) {
        if (!child.writeString(dst, indent + 4, limit)) {
            return false;
        }
    }
    if (dst.size() >= limit) {
        return false;
    }
    dst.append(pre).append("</").append(name).append(">\n");
    return true;
}

string
TraceNode::encode() const
{
    string ret = "";
    if (_hasNote) {
        ret.append("[");
        for (char c : _note) {
            if (c == '\\' || c == ']') {
                ret.append("\\");
            }
            ret += c;
        }
        ret.append("]");
    } else {
        ret.append(_strict ? "(" : "{");
        for (const auto & child : _children) {
            ret.append(child.encode());
        }
        ret.append(_strict ? ")" : "}");
    }
    return ret;
}

TraceNode
TraceNode::decode(const string &str)
{
    if (str.empty()) {
        return TraceNode();
    }
    TraceNode proxy;
    TraceNode *node = &proxy;
    string note = "";
    bool inNote = false, inEscape = false;
    for (uint32_t i = 0, len = str.size(); i < len; ++i) {
        char c = str[i];
        if (inNote) {
            if (inEscape) {
                note += c;
                inEscape = false;
            } else if (c == '\\') {
                inEscape = true;
            } else if (c == ']') {
                node->addChild(note);
                note.clear();
                inNote = false;
            } else {
                note += c;
            }
        } else {
            if (c == '[') {
                inNote = true;
            } else if (c == '(' || c == '{') {
                node->addChild(TraceNode());
                node = &node->_children.back();
                node->setStrict(c == '(');
            } else if (c == ')' || c == '}') {
                if (node == nullptr) {
                    LOG(warning, "Unexpected closing brace in trace '%s' at "
                                 "position %d.", str.c_str(), i);
                    return TraceNode();
                }
                if (node->isStrict() != (c == ')')) {
                    LOG(warning, "Mismatched closing brace in trace '%s' at "
                                 "position %d.", str.c_str(), i);
                    return TraceNode();
                }
                node = node->_parent;
            }
        }
    }
    if (inNote) {
        LOG(warning, "Unterminated note in trace '%s'.", str.c_str());
        return TraceNode();
    }
    if (node != &proxy) {
        LOG(warning, "Missing closing brace in trace '%s'.", str.c_str());
        return TraceNode();
    }
    if (proxy.getNumChildren() == 0) {
        LOG(warning, "No nodes found in trace '%s'.", str.c_str());
        return TraceNode();
    }
    if (proxy.getNumChildren() > 1) {
        return proxy; // best-effort recovery from malformed input
    }
    return proxy.getChild(0);
}

TraceVisitor &
TraceNode::accept(TraceVisitor & visitor) const
{
    visitor.visit(*this);
    if (_children.empty()) {
        return visitor;
    }
    visitor.entering(*this);
    for (auto & child : _children) {
        child.accept(visitor);
    }
    visitor.leaving(*this);
    return visitor;
}

size_t
TraceNode::computeMemoryUsage() const
{
    if (isLeaf()) {
        return getNote().size();
    }
    size_t childSum = 0;
    for (const TraceNode & child : _children) {
        childSum += child.computeMemoryUsage();
    }
    return childSum;
}

} // namespace vespalib
