// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "diskstate.h"
#include <boost/lexical_cast.hpp>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/log/log.h>

LOG_SETUP(".vdslib.diskstate");

namespace storage::lib {

DiskState::DiskState()
    : _state(0),
      _description(""),
      _capacity(1.0)
{
    setState(State::UP);
}

DiskState::DiskState(const State& state, const vespalib::stringref & description,
                     double capacity)
    : _state(0),
      _description(description),
      _capacity(1.0)
{
    setState(state);
    setCapacity(capacity);
}

DiskState::DiskState(vespalib::stringref  serialized)
    : _state(&State::UP),
      _description(""),
      _capacity(1.0)
{
    vespalib::StringTokenizer st(serialized, " \t\f\r\n");
    st.removeEmptyTokens();
    for (vespalib::StringTokenizer::Iterator it = st.begin();
         it != st.end(); ++it)
    {
        std::string::size_type index = it->find(':');
        if (index == std::string::npos) {
            throw vespalib::IllegalArgumentException(
                    "Token " + *it + " does not contain ':': " + serialized,
                    VESPA_STRLOC);
        }
        std::string key = it->substr(0, index);
        std::string value = it->substr(index + 1);
        if (key.size() > 0) switch (key[0]) {
            case 's':
                if (key.size() > 1) break;
                setState(State::get(value));
                continue;
            case 'c':
                if (key.size() > 1) break;
                try{
                    setCapacity(boost::lexical_cast<double>(value));
                } catch (...) {
                    throw vespalib::IllegalArgumentException(
                            "Illegal disk capacity '" + value + "'. Capacity "
                            "must be a positive floating point number",
                            VESPA_STRLOC);
                }
                continue;
            case 'm':
                if (key.size() > 1) break;
                _description = document::StringUtil::unescape(value);
                continue;
            default:
                break;
        }
        LOG(debug, "Unknown key %s in diskstate. Ignoring it, assuming it's a "
                   "new feature from a newer version than ourself: %s",
            key.c_str(), vespalib::string(serialized).c_str());
    }

}

void
DiskState::serialize(vespalib::asciistream & out, const vespalib::stringref & prefix,
                     bool includeDescription, bool useOldFormat) const
{
        // Always give node state if not part of a system state
        // to prevent empty serialization
    bool empty = true;
    if (*_state != State::UP || prefix.size() == 0) {
        if (useOldFormat && prefix.size() > 0) {
            out << prefix.substr(0, prefix.size() - 1)
                << ":" << _state->serialize();
        } else {
            out << prefix << "s:" << _state->serialize();
        }
        empty = false;
    }
    if (_capacity != 1.0) {
        if (empty) { empty = false; } else { out << ' '; }
        out << prefix << "c:" << _capacity;
    }
    if (includeDescription && _description.size() > 0) {
        if (empty) { empty = false; } else { out << ' '; }
        out << prefix << "m:"
            << document::StringUtil::escape(_description, ' ');
    }
}


void
DiskState::setState(const State& state)
{
    if (!state.validDiskState()) {
        throw vespalib::IllegalArgumentException(
                "State " + state.toString() + " is not a valid disk state.",
                VESPA_STRLOC);
    }
    _state = &state;
}

void
DiskState::setCapacity(double capacity)
{
    if (capacity < 0) {
        throw vespalib::IllegalArgumentException(
                "Negative capacity makes no sense.", VESPA_STRLOC);
    }
    _capacity = capacity;
}

void
DiskState::print(std::ostream& out, bool verbose,
                 const std::string& indent) const
{
    (void) indent;
    if (verbose) {
        out << "DiskState(" << *_state;
    } else {
        out << _state->serialize();
    }
    if (_capacity != 1.0) {
        out << (verbose ? ", capacity " : ", c ") << _capacity;
    }
    if (_description.size() > 0) {
        out << ": " << _description;
    }
    if (verbose) {
        out << ")";
    }
}

bool
DiskState::operator==(const DiskState& other) const
{
    return (_state == other._state && _capacity == other._capacity);
}

bool
DiskState::operator!=(const DiskState& other) const
{
    return (_state != other._state || _capacity != other._capacity);
}

}
