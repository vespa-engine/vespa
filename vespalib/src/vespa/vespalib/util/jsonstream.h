// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

/**
 * An overbuild of the json writer, making the code writing the json looking
 * neater. Also allows templates to use it with unknown (but supported) types,
 * letting the compiler take care of calling the correct function rather than
 * having to resort to template specialization.
 */

#include <vespa/vespalib/util/jsonwriter.h>

namespace vespalib {

// Inherit to refer to types without namespace prefix in header file.
struct JsonStreamTypes {
    class Object {};
    class Array {};
    class End {};
};
// Use namespace in function to avoid prefixing namespace everywhere.
namespace jsonstream {
    typedef JsonStreamTypes::Object Object;
    typedef JsonStreamTypes::Array Array;
    typedef JsonStreamTypes::End End;
}

// We can disable this if it ends up being a performance issue.
// Really useful to explain what code bits have tried to write invalid json
// though.
#define TRACK_JSON_CREATION_TO_CREATE_EASY_TO_DEBUG_ERROR_MESSAGES 1

class JsonStream : public JsonStreamTypes {
    JSONWriter _writer;
    enum class State {
        ROOT,
        OBJECT_EXPECTING_KEY,
        OBJECT_EXPECTING_VALUE,
        ARRAY
    };
    static const char* getStateName(const State&);
    struct StateEntry {
        State state;
        string object_key;
        size_t array_index;

        StateEntry() noexcept
            : state(State::ROOT), object_key(""), array_index(size_t(0)) {}
        StateEntry(State s)
            : state(s), object_key(""), array_index(size_t(0)) {}
        StateEntry(State s, stringref key)
            : state(s), object_key(key), array_index(size_t(0)) {}
    };
    std::vector<StateEntry> _state;

    StateEntry & top() { return _state.back(); }
    const StateEntry & top() const { return _state.back(); }
    void pop() { _state.resize(_state.size() - 1); }
    void push(const StateEntry & e) { _state.push_back(e); }
public:
    JsonStream(asciistream&, bool createIndents = false);
    JsonStream(const JsonStream&) = delete;
    JsonStream& operator=(const JsonStream&) = delete;
    JsonStream(JsonStream &&) = default;
    JsonStream& operator=(JsonStream &&) = default;
    ~JsonStream();

    JsonStream& operator<<(stringref);
    JsonStream& operator<<(bool);
    JsonStream& operator<<(double);
    JsonStream& operator<<(float); // Less precision that double
    JsonStream& operator<<(long long);
    JsonStream& operator<<(unsigned long long);
    JsonStream& operator<<(const Object&);
    JsonStream& operator<<(const Array&);
    JsonStream& operator<<(const End&);

        // Additional functions provided to let compiler work out correct
        // function without requiring user to cast their value
    JsonStream& operator<<(unsigned long v)
        { return operator<<(static_cast<unsigned long long>(v)); }
    JsonStream& operator<<(unsigned int v)
        { return operator<<(static_cast<unsigned long long>(v)); }
    JsonStream& operator<<(long v)
        { return operator<<(static_cast<long long>(v)); }
    JsonStream& operator<<(int v)
        { return operator<<(static_cast<long long>(v)); }
    JsonStream& operator<<(const char* c)
        { return operator<<(stringref(c)); }

    JsonStream& finalize();

    vespalib::string getJsonStreamState() const;

private:
    string getStateString() const;
    void fail(stringref error) const;
};

} // vespalib

