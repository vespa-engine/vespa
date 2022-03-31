// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "tracenode.h"
#include <memory>

namespace vespalib {

/**
 * A Trace object contains ad-hoc string notes organized in a strict-loose
 * tree. A Trace object consists of a trace level indicating which trace notes
 * should be included and a TraceTree object containing the tree structure and
 * collecting the trace information. Tracing is used to collect debug
 * information about a Routable traveling through the system. The trace level
 * is in the range [0,9]. 0 means no tracing, and 9 means all tracing is
 * enabled. A client that has the ability to trace information will have a
 * predefined level attached to that information. If the level on the
 * information is lower or equal to the level set in the Trace object, the
 * information will be traced.
 */
class Trace {
public:

    /**
     * Create an empty Trace with level set to 0 (no tracing)
     */
    Trace() noexcept : Trace(0) {}
    explicit Trace(uint32_t level) noexcept : _root(), _level(level) { }
    Trace & operator = (Trace &&) noexcept = default;
    Trace(Trace &&) noexcept = default;
    Trace(const Trace &);
    Trace & operator = (const Trace &) = delete;
    ~Trace() = default;

    /**
     * Remove all trace information and set the trace level to 0.
     */
    void clear();

    /**
     * Swap the internals of this with another.
     *
     * @param other The trace to swap internals with.
     * @return This, to allow chaining.
     */
    Trace &swap(Trace &other) {
        std::swap(_level, other._level);
        _root.swap(other._root);
        return *this;
    }

    void setLevel(uint32_t level) {
        _level = std::min(level, 9u);
    }

    uint32_t getLevel() const noexcept { return _level; }

    /**
     * Check if information with the given level should be traced. This method
     * is added to allow clients to check if something should be traced before
     * spending time building up the trace information itself.
     *
     * @param level The trace level to test.
     * @return True if tracing is enabled for the given level, false otherwise.
     */
    bool shouldTrace(uint32_t level) const noexcept { return level <= _level; }

    /**
     * Add the given note to the trace information if tracing is enabled for
     * the given level. If the addTime parameter is true, then the note is
     * prefixed with the current time. This is the default behaviour when
     * ommiting this parameter.
     *
     * @param level   The trace level of the note.
     * @param note    The note to add.
     * @param addTime Whether or not to prefix note with a timestamp.
     * @return True if the note was added to the trace information, false
     * otherwise.
     */
    bool trace(uint32_t level, const string &note, bool addTime = true);

    void normalize() {
        if (_root) {
            _root->normalize();
        }
    }

    void setStrict(bool strict) {
        ensureRoot().setStrict(strict);
    }
    void addChild(TraceNode && child) {
        ensureRoot().addChild(std::move(child));
    }
    void addChild(Trace && child) {
        if (!child.isEmpty()) {
            addChild(std::move(*child._root));
        }
    }

    bool isEmpty() const { return !_root || _root->isEmpty(); }

    uint32_t getNumChildren() const noexcept { return _root ? _root->getNumChildren() : 0; }
    const TraceNode & getChild(uint32_t child) const { return getRoot().getChild(child); }
    string encode() const;

    /**
     * Returns a string representation of the contained trace tree. This is a
     * readable, non-parseable string.
     *
     * @return Readable trace string.
     */
    string toString(size_t limit=31337) const;
    size_t computeMemoryUsage() const {
        return _root ? _root->computeMemoryUsage() : 0;
    }
private:
    const TraceNode &getRoot() const { return *_root; }
    TraceNode &ensureRoot();

    std::unique_ptr<TraceNode> _root;
    uint32_t  _level;
};

#define VESPALIB_TRACE2(ttrace, level, note, addTime) \
    if (ttrace.shouldTrace(level)) {             \
        ttrace.trace(level, note, addTime);      \
    }

#define VESPALIB_TRACE(trace, level, note) \
    VESPALIB_TRACE2(trace, level, note, true)

} // namespace vespalib

