// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nodestate.h"

#include <boost/lexical_cast.hpp>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".vdslib.nodestate");

using vespalib::IllegalArgumentException;

namespace storage::lib {

NodeState::NodeState(const NodeState &) = default;
NodeState & NodeState::operator = (const NodeState &) = default;
NodeState::NodeState(NodeState &&) noexcept = default;
NodeState & NodeState::operator = (NodeState &&) noexcept = default;
NodeState::~NodeState() = default;

NodeState::NodeState()
    : _type(nullptr),
      _state(nullptr),
      _description(""),
      _capacity(1.0),
      _initProgress(0.0),
      _minUsedBits(16),
      _startTimestamp(0)
{
    setState(State::UP);
}

NodeState::NodeState(const NodeType& type, const State& state,
                     vespalib::stringref description, double capacity)
    : _type(&type),
      _state(nullptr),
      _description(description),
      _capacity(1.0),
      _initProgress(0.0),
      _minUsedBits(16),
      _startTimestamp(0)
{
    setState(state);
    if (type == NodeType::STORAGE) {
        setCapacity(capacity);
    }
}

NodeState::NodeState(vespalib::stringref serialized, const NodeType* type)
    : _type(type),
      _state(&State::UP),
      _description(),
      _capacity(1.0),
      _initProgress(0.0),
      _minUsedBits(16),
      _startTimestamp(0)
{

    vespalib::StringTokenizer st(serialized, " \t\f\r\n");
    st.removeEmptyTokens();
    for (auto token : st) {
        std::string::size_type index = token.find(':');
        if (index == std::string::npos) {
            throw IllegalArgumentException("Token " + token + " does not contain ':': " + serialized, VESPA_STRLOC);
        }
        std::string key = token.substr(0, index);
        std::string value = token.substr(index + 1);
        if (!key.empty()) switch (key[0]) {
            case 'b':
                if (_type != nullptr && *type != NodeType::STORAGE) break;
                if (key.size() > 1) break;
                try {
                    setMinUsedBits(boost::lexical_cast<uint32_t>(value));
                } catch (...) {
                    throw IllegalArgumentException("Illegal used bits '" + value + "'. Used bits must be a positive"
                                                   " integer ", VESPA_STRLOC);
                }
                continue;
            case 's':
                if (key.size() > 1) break;
                setState(State::get(value));
                continue;
            case 'c':
                if (key.size() > 1) break;
                if (_type != nullptr && *type != NodeType::STORAGE) break;
                try {
                    setCapacity(boost::lexical_cast<double>(value));
                } catch (...) {
                    throw IllegalArgumentException("Illegal capacity '" + value + "'. Capacity must be a positive"
                                                   " floating point number", VESPA_STRLOC);
                }
                continue;
            case 'i':
                if (key.size() > 1) break;
                try {
                    setInitProgress(boost::lexical_cast<double>(value));
                } catch (...) {
                    throw IllegalArgumentException("Illegal init progress '" + value + "'. Init progress must be a"
                                                   " floating point number from 0.0 to 1.0", VESPA_STRLOC);
                }
                continue;
            case 't':
                if (key.size() > 1) break;
                try {
                    setStartTimestamp(boost::lexical_cast<uint64_t>(value));
                } catch (...) {
                    throw IllegalArgumentException("Illegal start timestamp '" + value + "'. Start timestamp must be"
                                                   " 0 or positive long.", VESPA_STRLOC);
                }
                continue;
            case 'm':
                if (key.size() > 1) break;
                _description = document::StringUtil::unescape(value);
                continue;
            default:
                break;
        }
        LOG(debug, "Unknown key %s in nodestate. Ignoring it, assuming it's a "
                   "new feature from a newer version than ourself: %s",
            key.c_str(), vespalib::string(serialized).c_str());
    }
}

namespace {
    struct SeparatorPrinter {
        mutable bool first;
        SeparatorPrinter() : first(true) {}

        void print(vespalib::asciistream & os) const {
            if (first) {
                first = false;
            } else {
                os << ' ';
            }
        }
    };

    vespalib::asciistream & operator<<(vespalib::asciistream & os, const SeparatorPrinter& sep)
    {
        sep.print(os);
        return os;
    }

}

void
NodeState::serialize(vespalib::asciistream & out, vespalib::stringref prefix,
                     bool includeDescription) const
{
    SeparatorPrinter sep;
    // Always give node state if not part of a system state
    // to prevent empty serialization
    if (*_state != State::UP || prefix.empty()) {
        out << sep << prefix << "s:";
        out << _state->serialize();
    }
    if (_capacity != 1.0) {
        out << sep << prefix << "c:" << _capacity;
    }
    if (_minUsedBits != 16) {
        out << sep << prefix << "b:" << _minUsedBits;
    }
    if (*_state == State::INITIALIZING) {
        out << sep << prefix << "i:" << _initProgress;
    }
    if (_startTimestamp != 0u) {
        out << sep << prefix << "t:" << _startTimestamp;
    }
    if (includeDescription && ! _description.empty()) {
        out << sep << prefix << "m:"
            << document::StringUtil::escape(_description, ' ');
    }
}

void
NodeState::setState(const State& state)
{
    if (_type != nullptr) {
        // We don't know whether you want to store reported, wanted or
        // current node state, so we must accept any.
        if (!state.validReportedNodeState(*_type)
            && !state.validWantedNodeState(*_type))
        {
            throw IllegalArgumentException(state.toString(true) + " is not a legal " + _type->toString() + " state", VESPA_STRLOC);
        }
    }
    _state = &state;
}

void
NodeState::setMinUsedBits(uint32_t usedBits) {
    if (usedBits < 1 || usedBits > 58) {
        std::ostringstream ost;
        ost << "Illegal used bits '" << usedBits << "'. Minimum used bits must be an integer > 0 and < 59.";
        throw IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }

    _minUsedBits = usedBits;
}

void
NodeState::setCapacity(vespalib::Double capacity)
{
    if (capacity < 0) {
        std::ostringstream ost;
        ost << "Illegal capacity '" << capacity << "'. Capacity must be a positive floating point number";
        throw IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    if (_type != nullptr && *_type != NodeType::STORAGE) {
        throw IllegalArgumentException("Capacity only make sense for storage nodes.", VESPA_STRLOC);
    }
    _capacity = capacity;
}

void
NodeState::setInitProgress(vespalib::Double initProgress)
{
    if (initProgress < 0 || initProgress > 1.0) {
        std::ostringstream ost;
        ost << "Illegal init progress '" << initProgress << "'. Init progress "
               "must be a floating point number from 0.0 to 1.0";
        throw IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    _initProgress = initProgress;
}

void
NodeState::setStartTimestamp(uint64_t startTimestamp)
{
    _startTimestamp = startTimestamp;
}

void
NodeState::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    if (!verbose) {
        vespalib::asciistream tmp;
        serialize(tmp);
        out << tmp.str();
        return;
    }
    _state->print(out, verbose, indent);
    if (_capacity != 1.0) {
        out << ", capacity " << _capacity;
    }
    if (_minUsedBits != 16) {
        out << ", minimum used bits " << _minUsedBits;
    }
    if (*_state == State::INITIALIZING) {
        out << ", init progress " << _initProgress;
    }
    if (_startTimestamp != 0) {
        out << ", start timestamp " << _startTimestamp;
    }
    if (!_description.empty()) {
        out << ": " << _description;
    }
}

bool
NodeState::operator==(const NodeState& other) const
{
    if (_state != other._state ||
        _capacity != other._capacity ||
        _minUsedBits != other._minUsedBits ||
        _startTimestamp != other._startTimestamp ||
        (*_state == State::INITIALIZING && (_initProgress != other._initProgress)))
    {
        return false;
    }
    return true;
}

bool
NodeState::similarTo(const NodeState& other) const
{
    if (_state != other._state ||
        _capacity != other._capacity ||
        _minUsedBits != other._minUsedBits ||
        _startTimestamp < other._startTimestamp)
    {
        return false;
    }
    if (*_state == State::INITIALIZING) {
        double limit = getListingBucketsInitProgressLimit();
        bool below1 = (_initProgress < limit);
        bool below2 = (other._initProgress < limit);
        if (below1 != below2) {
            return false;
        }
    }
    return true;
}

void
NodeState::verifySupportForNodeType(const NodeType& type) const
{
    if (_type != nullptr && *_type == type) return;
    if (!_state->validReportedNodeState(type) && !_state->validWantedNodeState(type)) {
        throw IllegalArgumentException("State " + _state->toString() + " does not fit a node of type " + type.toString(), VESPA_STRLOC);
    }
    if (type == NodeType::DISTRIBUTOR && _capacity != 1.0) {
        throw IllegalArgumentException("Capacity should not be set for a distributor node.", VESPA_STRLOC);
    }
}

std::string
NodeState::getTextualDifference(const NodeState& other) const {
    std::ostringstream source;
    std::ostringstream target;

    if (_state != other._state) {
        source << ", " << *_state;
        target << ", " << *other._state;
    }
    if (_capacity != other._capacity) {
        source << ", capacity " << _capacity;
        target << ", capacity " << other._capacity;
    }
    if (_minUsedBits != other._minUsedBits) {
        source << ", minUsedBits " << _minUsedBits;
        target << ", minUsedBits " << _minUsedBits;
    }
    if (_initProgress != other._initProgress) {
        if (_state == &State::INITIALIZING) {
            source << ", init progress " << _initProgress;
        }
        if (other._state == &State::INITIALIZING) {
            target << ", init progress " << other._initProgress;
        }
    }
    if (_startTimestamp != other._startTimestamp) {
        source << ", start timestamp " << _startTimestamp;
        target << ", start timestamp " << other._startTimestamp;
    }

    if (source.str().length() < 2 || target.str().length() < 2) {
        return "no change";
    }

    std::ostringstream total;
    total << source.str().substr(2) << " to " << target.str().substr(2);
    if (other._description != _description) {
        total << " (" << other._description << ")";
    }
    return total.str();
}

}
