// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "integerresultnode.h"
#include "floatresultnode.h"
#include "stringresultnode.h"
#include "rawresultnode.h"
#include "enumresultnode.h"
#include "nullresultnode.h"
#include "positiveinfinityresultnode.h"
#include <vespa/vespalib/locale/c.h>
#include <cmath>
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/deserializer.hpp>
#include <stdexcept>

namespace search::expression {

using vespalib::nbo;
using vespalib::Serializer;
using vespalib::Deserializer;
using vespalib::make_string;
using vespalib::Identifiable;
using vespalib::BufferRef;
using vespalib::ConstBufferRef;

IMPLEMENT_ABSTRACT_RESULTNODE(ResultNode,        Identifiable);
IMPLEMENT_ABSTRACT_RESULTNODE(SingleResultNode,  ResultNode);
IMPLEMENT_ABSTRACT_RESULTNODE(NumericResultNode, SingleResultNode);
IMPLEMENT_ABSTRACT_RESULTNODE(IntegerResultNode, NumericResultNode);
IMPLEMENT_RESULTNODE(StringResultNode,           SingleResultNode);
IMPLEMENT_RESULTNODE(NullResultNode,             SingleResultNode);
IMPLEMENT_RESULTNODE(PositiveInfinityResultNode, SingleResultNode);
IMPLEMENT_RESULTNODE(RawResultNode,              SingleResultNode);
IMPLEMENT_RESULTNODE(BoolResultNode,             IntegerResultNode);
IMPLEMENT_RESULTNODE(Int8ResultNode,             IntegerResultNode);
IMPLEMENT_RESULTNODE(Int16ResultNode,            IntegerResultNode);
IMPLEMENT_RESULTNODE(Int32ResultNode,            IntegerResultNode);
IMPLEMENT_RESULTNODE(Int64ResultNode,            IntegerResultNode);
IMPLEMENT_RESULTNODE(EnumResultNode,             IntegerResultNode);
IMPLEMENT_RESULTNODE(FloatResultNode,            NumericResultNode);

void ResultNode::sort() {}

void ResultNode::reverse() {}

void ResultNode::negate()
{
    throw std::runtime_error(make_string("Class %s does not implement 'negate'", getClass().name()));
}

ResultSerializer & ResultNode::onSerializeResult(ResultSerializer & os) const
{
    os.proxyPut(*this);
    return os;
}

ResultDeserializer & ResultNode::onDeserializeResult(ResultDeserializer & is)
{
    is.proxyGet(*this);
    return is;
}

int64_t FloatResultNode::onGetInteger(size_t index) const { (void) index; return static_cast<int64_t>(std::round(_value)); }
double  FloatResultNode::onGetFloat(size_t index)   const { (void) index; return _value; }
void FloatResultNode::add(const ResultNode & b)      { _value += b.getFloat(); }
void FloatResultNode::negate()                       { _value = - _value; }
void FloatResultNode::multiply(const ResultNode & b) { _value *= b.getFloat(); }
void FloatResultNode::divide(const ResultNode & b)   {
    double val = b.getFloat();
    _value = (val == 0.0) ? 0.0 : (_value / val);
}
void FloatResultNode::modulo(const ResultNode & b)   { _value = ResultNode::getInteger() % b.getInteger(); }
void FloatResultNode::min(const ResultNode & b)      { double t(b.getFloat()); if (t < _value) { _value = t; } }
void FloatResultNode::max(const ResultNode & b)      { double t(b.getFloat()); if (t > _value) { _value = t; } }
void FloatResultNode::set(const ResultNode & rhs)    { _value = rhs.getFloat(); }
Serializer & FloatResultNode::onSerialize(Serializer & os) const { os << _value; return os; }
Deserializer & FloatResultNode::onDeserialize(Deserializer & is) { is >> _value; return is; }

void
FloatResultNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "value", _value);
}

ResultNode::ConstBufferRef
FloatResultNode::onGetString(size_t index, ResultNode::BufferRef buf) const
{
    (void) index;
    int numWritten = std::min(buf.size(), (size_t)std::max(0, snprintf(buf.str(), buf.size(), "%g", _value)));
    return ConstBufferRef(buf.str(), numWritten);
}

bool
FloatResultNode::isNan() const
{
    return std::isnan(_value);
}

int
FloatResultNode::onCmp(const Identifiable & b) const
{
    const FloatResultNode & rhs(static_cast<const FloatResultNode &>(b));
    if (isNan()) {
        return rhs.isNan() ? 0 : -1;
    } else {
        if (rhs.isNan()) {
            return 1;
        } else {
            return (_value > rhs._value) ? 1 : (_value < rhs._value) ? -1 : 0;
        }
    }
}

void StringResultNode::setMin() { _value.clear(); }
void StringResultNode::setMax() { _value.clear(); _value.append(char(-1)); }
void RawResultNode::setMin() { _value.clear(); }
void RawResultNode::setMax() { _value.push_back(-1); }
void FloatResultNode::setMin() { _value = -std::numeric_limits<double>::max(); }
void FloatResultNode::setMax() { _value = std::numeric_limits<double>::max(); }

void NullResultNode::setMin() { }
void NullResultNode::setMax() { }
void    NullResultNode::add(const ResultNode & b) { (void) b; }
void    NullResultNode::min(const ResultNode & b) { (void) b; }
void    NullResultNode::max(const ResultNode & b) { (void) b; }
int64_t NullResultNode::onGetInteger(size_t index) const { (void) index; return 0; }
double  NullResultNode::onGetFloat(size_t index)   const { (void) index; return 0.0; }
int     NullResultNode::onCmp(const Identifiable & b) const { (void) b; return (b.getClass().id() == NullResultNode::classId) ? 0 : 1; }
void    NullResultNode::set(const ResultNode & rhs) { (void) rhs; }
size_t  NullResultNode::hash() const { return 0; }
ResultNode::ConstBufferRef NullResultNode::onGetString(size_t index, ResultNode::BufferRef buf) const { (void) index; return buf; }
void PositiveInfinityResultNode::setMin() { }
void PositiveInfinityResultNode::setMax() { }
void    PositiveInfinityResultNode::add(const ResultNode & b) { (void) b; }
void    PositiveInfinityResultNode::min(const ResultNode & b) { (void) b; }
void    PositiveInfinityResultNode::max(const ResultNode & b) { (void) b; }
int64_t PositiveInfinityResultNode::onGetInteger(size_t index) const { (void) index; return 0; }
double  PositiveInfinityResultNode::onGetFloat(size_t index)   const { (void) index; return 0.0; }
void    PositiveInfinityResultNode::set(const ResultNode & rhs) { (void) rhs; }
size_t  PositiveInfinityResultNode::hash() const { return 0; }
ResultNode::ConstBufferRef PositiveInfinityResultNode::onGetString(size_t index, ResultNode::BufferRef buf) const { (void) index; return buf; }

int PositiveInfinityResultNode::onCmp(const Identifiable & b) const
{
    if (b.inherits(PositiveInfinityResultNode::classId)) {
        return 0;
    }
    return 1;
}

int64_t StringResultNode::onGetInteger(size_t index) const { (void) index; return strtoll(_value.c_str(), nullptr, 0); }
double  StringResultNode::onGetFloat(size_t index)   const { (void) index; return vespalib::locale::c::strtod(_value.c_str(), nullptr); }
Serializer &
StringResultNode::onSerialize(Serializer & os) const
{
    os << _value;
    return os;
}

int
StringResultNode::onCmp(const Identifiable & b) const
{
    if (b.inherits(PositiveInfinityResultNode::classId)) {
        return -1;
    } else {
        const StringResultNode & sb(static_cast<const StringResultNode &>(b));
        size_t sz(std::min(_value.size(), sb._value.size()));
        int result = memcmp(_value.c_str(), sb._value.c_str(), sz);
        if (result == 0) {
            result = _value.size() < sb._value.size() ? -1 : _value.size() > sb._value.size() ? 1 : 0;
        }
        return result;
    }
}

Deserializer &
StringResultNode::onDeserialize(Deserializer & is)
{
    is >> _value;
    return is;
}


void
RawResultNode::add(const ResultNode & b)
{
    char buf[32];
    ConstBufferRef s(b.getString(BufferRef(buf, sizeof(buf))));
    const uint8_t *raw = static_cast<const uint8_t *>(s.data());

    size_t i(0);
    for (; i < _value.size() && i < s.size(); i++) {
        _value[i] += raw[i];
    }
    if (i < s.size()) {
        for (; i < s.size(); i++) {
            _value.push_back(raw[i]);
        }
    }

}

void
RawResultNode::min(const ResultNode & b)
{
    char buf[32];
    ConstBufferRef s(b.getString(BufferRef(buf, sizeof(buf))));

    if (memcmp(&_value[0], s.data(), std::min(s.size(), _value.size())) > 0)  {
        setBuffer(s.data(), s.size());
    }
}

void
RawResultNode::max(const ResultNode & b)
{
    char buf[32];
    ConstBufferRef s(b.getString(BufferRef(buf, sizeof(buf))));

    if (memcmp(&_value[0], s.data(), std::min(s.size(), _value.size())) < 0)  {
        setBuffer(s.data(), s.size());
    }
}

void
RawResultNode::negate()
{
    for (size_t i(0); i < _value.size(); i++) {
        _value[i] = - _value[i];
    }
}

void
StringResultNode::add(const ResultNode & b)
{
    char buf[32];
    ConstBufferRef s(b.getString(BufferRef(buf, sizeof(buf))));
    vespalib::stringref bs(s.c_str(), s.size());
    size_t i(0);
    for (; i < _value.length() && i < bs.length(); i++) {
        _value[i] += bs[i];
    }
    if (i < bs.length()) {
        // XXX: Should have some way of appending with iterators
        _value.append(bs.data() + i, (bs.length() - i));
    }
}

void
StringResultNode::min(const ResultNode & b)
{
    char buf[32];
    ConstBufferRef s(b.getString(BufferRef(buf, sizeof(buf))));
    vespalib::stringref bs(s.c_str(), s.size());
    if (_value > bs) {
        _value = bs;
    }
}

void
StringResultNode::max(const ResultNode & b)
{
    char buf[32];
    ConstBufferRef s(b.getString(BufferRef(buf, sizeof(buf))));
    vespalib::stringref bs(s.c_str(), s.size());
    if (_value < bs) {
        _value = bs;
    }
}

void
StringResultNode::negate()
{
    for (size_t i(0); i < _value.length(); i++) {
        _value[i] = - _value[i];
    }
}

void
StringResultNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "value", _value);
}

ResultNode::ConstBufferRef
StringResultNode::onGetString(size_t index, ResultNode::BufferRef ) const {
    (void) index;
    return ConstBufferRef(_value.c_str(), _value.size());
}

void
StringResultNode::set(const ResultNode & rhs)
{
    char buf[32];
    ConstBufferRef b(rhs.getString(BufferRef(buf, sizeof(buf))));
    _value.assign(b.c_str(), b.size());
}

StringResultNode & StringResultNode::append(const ResultNode & rhs)
{
    char buf[32];
    ConstBufferRef b(rhs.getString(BufferRef(buf, sizeof(buf))));
    _value.append(b.c_str(), b.size());
    return *this;
}

namespace {

size_t hashBuf(const void *s, size_t sz)
{
    size_t result(0);
    const size_t * value = static_cast<const size_t *>(s);
    for(size_t i(0), m(sz/sizeof(size_t)); i < m; i++) {
        result ^= value[i];
    }
    unsigned left(sz%sizeof(size_t));
    if (left) {
        size_t lastValue(0);
        memcpy(&lastValue, static_cast<const char *>(s)+sz-left, left);
        result ^= lastValue;
    }
    return result;
}

}

size_t StringResultNode::hash() const { return hashBuf(_value.c_str(),  _value.size()); }

size_t
StringResultNode::hash(const void * buf) const
{
    const vespalib::string & s = *static_cast<const vespalib::string *>(buf);
    return hashBuf(s.c_str(), s.size());
}

int64_t
RawResultNode::onGetInteger(size_t index) const
{
    (void) index;
    union {
        int64_t _int64;
        uint8_t _bytes[8];
    } nbo;
    nbo._int64 = 0;
    memcpy(nbo._bytes, &_value[0], std::min(sizeof(nbo._bytes), _value.size()));
    return nbo::n2h(nbo._int64);
}

double  RawResultNode::onGetFloat(size_t index) const
{
    (void) index;
    union {
        double  _double;
        uint8_t _bytes[8];
    } nbo;
    nbo._double = 0;
    memcpy(nbo._bytes, &_value[0], std::min(sizeof(nbo._bytes), _value.size()));
    return nbo::n2h(nbo._double);
}

Serializer & RawResultNode::onSerialize(Serializer & os) const
{
    os << _value;
    return os;
}

ResultSerializer & RawResultNode::onSerializeResult(ResultSerializer & os) const
{
    return os.putResult(*this);
}

int RawResultNode::onCmp(const Identifiable & b) const
{
    if (b.inherits(PositiveInfinityResultNode::classId)) {
        return -1;
    } else {
        const RawResultNode & rb( static_cast<const RawResultNode &>(b) );
        int result = memcmp(&_value[0], &rb._value[0], std::min(_value.size(), rb._value.size()));
        if (result == 0) {
            result = _value.size() < rb._value.size() ? -1 : _value.size() > rb._value.size() ? 1 : 0;
        }
        return result;
    }
}

size_t RawResultNode::hash() const { return hashBuf(&_value[0], _value.size()); }

size_t
RawResultNode::hash(const void * buf) const
{
    const std::vector<uint8_t> & s = *static_cast<const std::vector<uint8_t> *>(buf);
    return hashBuf(&s[0], s.size());
}

Deserializer &
RawResultNode::onDeserialize(Deserializer & is)
{
    is >> _value;
    return is;
}

ResultDeserializer &
RawResultNode::onDeserializeResult(ResultDeserializer & is)
{
    return is.getResult(*this);
}

void
RawResultNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "value", _value);
}

void
RawResultNode::set(const ResultNode & rhs)
{
    char buf[32];
    ConstBufferRef b(rhs.getString(BufferRef(buf, sizeof(buf))));
    setBuffer(b.data(), b.size());
}
void
RawResultNode::setBuffer(const void *buf, size_t sz)
{
    _value.resize(sz + 1);
    memcpy(&_value[0], buf, sz);
    _value.back() = 0;
    _value.resize(sz);
}

namespace {
    const vespalib::string TRUE = "true";
    const vespalib::string FALSE = "false";
}

ResultNode::ConstBufferRef
RawResultNode::onGetString(size_t, BufferRef ) const {
    return ConstBufferRef(&_value[0], _value.size());
}

ResultNode::ConstBufferRef
EnumResultNode::onGetString(size_t, BufferRef buf) const {
    int numWritten(std::min(buf.size(), (size_t)std::max(0, snprintf(buf.str(), buf.size(), "%" PRId64, getValue()))));
    return ConstBufferRef(buf.str(), numWritten);
}

ResultNode::ConstBufferRef
BoolResultNode::onGetString(size_t, BufferRef) const {
    return getValue() ? ConstBufferRef(TRUE.data(), TRUE.size()) : ConstBufferRef(FALSE.data(), FALSE.size());
}

ResultNode::ConstBufferRef
Int8ResultNode::onGetString(size_t, BufferRef buf) const {
    int numWritten(std::min(buf.size(), (size_t)std::max(0, snprintf(buf.str(), buf.size(), "%d", getValue()))));
    return ConstBufferRef(buf.str(), numWritten);
}

ResultNode::ConstBufferRef
Int16ResultNode::onGetString(size_t, BufferRef buf) const {
    int numWritten(std::min(buf.size(), (size_t)std::max(0, snprintf(buf.str(), buf.size(), "%d", getValue()))));
    return ConstBufferRef(buf.str(), numWritten);
}

ResultNode::ConstBufferRef
Int32ResultNode::onGetString(size_t, BufferRef buf) const {
    int numWritten(std::min(buf.size(), (size_t)std::max(0, snprintf(buf.str(), buf.size(), "%d", getValue()))));
    return ConstBufferRef(buf.str(), numWritten);
}

ResultNode::ConstBufferRef
Int64ResultNode::onGetString(size_t, BufferRef buf) const {
    int numWritten(std::min(buf.size(), (size_t)std::max(0, snprintf(buf.str(), buf.size(), "%" PRId64, getValue()))));
    return ConstBufferRef(buf.str(), numWritten);
}

}
