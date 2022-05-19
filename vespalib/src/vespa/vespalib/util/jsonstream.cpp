// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "jsonstream.h"
#include "jsonexception.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace vespalib {

const char*
JsonStream::getStateName(const State& s) {
    switch (s) {
        case State::OBJECT_EXPECTING_KEY: return "ObjectExpectingKey";
        case State::OBJECT_EXPECTING_VALUE: return "ObjectExpectingValue";
        case State::ARRAY: return "ArrayExpectingValue";
        case State::ROOT: return "RootExpectingArrayOrObjectStart";
    }
    throw IllegalStateException("Control should not reach this point", VESPA_STRLOC);
}

JsonStream::JsonStream(asciistream& as, bool createIndents)
    : _writer(as)
{
    if (createIndents) _writer.setPretty();
    push({State::ROOT});
}

JsonStream::~JsonStream() {}

JsonStream&
JsonStream::operator<<(stringref value)
{
    if (_state.empty()) {
        fail("Stream already finalized. Can't add a string value.");
    }
    switch (top().state) {
        case State::OBJECT_EXPECTING_KEY: {
            _writer.appendKey(value);
            top() = {State::OBJECT_EXPECTING_VALUE, value};
            break;
        }
        case State::OBJECT_EXPECTING_VALUE: {
            _writer.appendString(value);
            top().state = State::OBJECT_EXPECTING_KEY;
            break;
        }
        case State::ARRAY: {
            _writer.appendString(value);
            ++top().array_index;
            break;
        }
        case State::ROOT: {
            _writer.appendString(value);
            pop();
            break;
        }
    }
    return *this;
}

JsonStream&
JsonStream::operator<<(bool value)
{
    if (_state.empty()) {
        fail("Stream already finalized. Can't add a bool value.");
    }
    switch (top().state) {
        case State::OBJECT_EXPECTING_KEY: {
            fail("A bool value cannot be an object key");
            break;
        }
        case State::OBJECT_EXPECTING_VALUE: {
            _writer.appendBool(value);
            top().state = State::OBJECT_EXPECTING_KEY;
            break;
        }
        case State::ARRAY: {
            _writer.appendBool(value);
            ++top().array_index;
            break;
        }
        case State::ROOT: {
            _writer.appendBool(value);
            pop();
            break;
        }
    }
    return *this;
}

JsonStream&
JsonStream::operator<<(double value)
{
    if (_state.empty()) {
        fail("Stream already finalized. Can't add a double value.");
    }
    switch (top().state) {
        case State::OBJECT_EXPECTING_KEY: {
            fail("A double value cannot be an object key");
            break;
        }
        case State::OBJECT_EXPECTING_VALUE: {
            _writer.appendDouble(value);
            top().state = State::OBJECT_EXPECTING_KEY;
            break;
        }
        case State::ARRAY: {
            _writer.appendDouble(value);
            ++top().array_index;
            break;
        }
        case State::ROOT: {
            _writer.appendDouble(value);
            pop();
            break;
        }
    }
    return *this;
}

JsonStream&
JsonStream::operator<<(float value)
{
    if (_state.empty()) {
        fail("Stream already finalized. Can't add a float value.");
    }
    switch (top().state) {
        case State::OBJECT_EXPECTING_KEY: {
            fail("A float value cannot be an object key");
            break;
        }
        case State::OBJECT_EXPECTING_VALUE: {
            _writer.appendFloat(value);
            top().state = State::OBJECT_EXPECTING_KEY;
            break;
        }
        case State::ARRAY: {
            _writer.appendFloat(value);
            ++top().array_index;
            break;
        }
        case State::ROOT: {
            _writer.appendDouble(value);
            pop();
            break;
        }
    }
    return *this;
}

JsonStream&
JsonStream::operator<<(long long value)
{
    if (_state.empty()) {
        fail("Stream already finalized. Can't add a long long value.");
    }
    switch (top().state) {
        case State::OBJECT_EXPECTING_KEY: {
            fail("An int64_t value cannot be an object key");
            break;
        }
        case State::OBJECT_EXPECTING_VALUE: {
            _writer.appendInt64(value);
            top().state = State::OBJECT_EXPECTING_KEY;
            break;
        }
        case State::ARRAY: {
            _writer.appendInt64(value);
            ++top().array_index;
            break;
        }
        case State::ROOT: {
            _writer.appendInt64(value);
            pop();
            break;
        }
    }
    return *this;
}

JsonStream&
JsonStream::operator<<(unsigned long long value)
{
    if (_state.empty()) {
        fail("Stream already finalized. Can't add an unsigned long long value.");
    }
    switch (top().state) {
        case State::OBJECT_EXPECTING_KEY: {
            fail("A uint64_t value cannot be an object key");
            break;
        }
        case State::OBJECT_EXPECTING_VALUE: {
            _writer.appendUInt64(value);
            top().state = State::OBJECT_EXPECTING_KEY;
            break;
        }
        case State::ARRAY: {
            _writer.appendUInt64(value);
            ++top().array_index;
            break;
        }
        case State::ROOT: {
            _writer.appendUInt64(value);
            pop();
            break;
        }
    }
    return *this;
}

JsonStream&
JsonStream::operator<<(const Object&)
{
    if (_state.empty()) {
        fail("Stream already finalized. Can't start a new object.");
    }
    switch (top().state) {
        case State::OBJECT_EXPECTING_KEY: {
            fail("An object value cannot be an object key");
            break;
        }
        case State::OBJECT_EXPECTING_VALUE: {
            _writer.beginObject();
            top().state = State::OBJECT_EXPECTING_KEY;
            push({State::OBJECT_EXPECTING_KEY, ""});
            break;
        }
        case State::ARRAY: {
            _writer.beginObject();
            push({State::OBJECT_EXPECTING_KEY, ""});
            break;
        }
        case State::ROOT: {
            _writer.beginObject();
            top() = {State::OBJECT_EXPECTING_KEY, ""};
            break;
        }
    }
    return *this;
}

JsonStream&
JsonStream::operator<<(const Array&)
{
    if (_state.empty()) {
        fail("Stream already finalized. Can't start a new array.");
    }
    switch (top().state) {
        case State::OBJECT_EXPECTING_KEY: {
            fail("An array value cannot be an object key");
            break;
        }
        case State::OBJECT_EXPECTING_VALUE: {
            _writer.beginArray();
            top().state = State::OBJECT_EXPECTING_KEY;
            push({State::ARRAY});
            break;
        }
        case State::ARRAY: {
            _writer.beginArray();
            push({State::ARRAY});
            break;
        }
        case State::ROOT: {
            _writer.beginArray();
            top() = {State::ARRAY};
            break;
        }
    }
    return *this;
}

JsonStream&
JsonStream::operator<<(const End&)
{
    if (_state.empty()) {
        fail("Stream already finalized. Can't end it.");
    }
    switch (top().state) {
        case State::OBJECT_EXPECTING_KEY: {
            _writer.endObject();
            pop();
            break;
        }
        case State::OBJECT_EXPECTING_VALUE: {
            fail("Object got key but not value. Cannot end it now");
            break;
        }
        case State::ARRAY: {
            _writer.endArray();
            pop();
            break;
        }
        case State::ROOT: {
            fail("No tag to end. At root");
            break;
        }
    }
    if (!_state.empty() && top().state == State::ARRAY) {
        ++top().array_index;
    }
    return *this;
}

JsonStream&
JsonStream::finalize()
{
    while (!_state.empty()) {
        operator<<(End());
    }
    return *this;
}
    
string
JsonStream::getStateString() const
{
    asciistream as;
    for (auto it(_state.begin()), mt(_state.end()); it != mt; it++) {
        switch (it->state) {
            case State::OBJECT_EXPECTING_KEY:
            case State::OBJECT_EXPECTING_VALUE: {
                as << "{" << it->object_key << "}";
                break;
            }
            case State::ARRAY: {
                as << "[";
                if (it->array_index != 0) {
                    as << (it->array_index - 1);
                }
                as << "]";
                break;
            }
            case State::ROOT: {
                break;
            }
        }
    }
    if (_state.empty()) {
        as << "Finalized";
    } else {
        as << "(" << getStateName(_state.back().state) << ")";
    }
    return as.str();
}

string
JsonStream::getJsonStreamState() const
{
    asciistream report;
    report << "Current: " << getStateString();
    return report.str();
}

void
JsonStream::fail(stringref error) const
{
    asciistream report;
    report << "Invalid state on call: " << error
           << " (" << getStateString() << ")";
    throw JsonStreamException(report.str(), "", VESPA_STRLOC);
}

}
