// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "monitoring_search_iterator.h"
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".queryeval.monitoring_search_iterator");

using vespalib::make_string;

namespace search::queryeval {

MonitoringSearchIterator::Stats::Stats()
    : _numSeeks(0),
      _numUnpacks(0),
      _numDocIdSteps(0),
      _numHitSkips(0)
{
}

void
MonitoringSearchIterator::Dumper::addIndent()
{
    int n = _currIndent;
    if (n < 0) {
        n = 0;
    }
    _str.append(vespalib::string(n, ' '));
}

void
MonitoringSearchIterator::Dumper::addText(const vespalib::string &value)
{
    addIndent();
    _str.append(value.c_str());
    uint32_t extraSpaces = value.size() < _textFormatWidth ? _textFormatWidth - value.size() : 0;
    _str.append(make_string(":%s ", vespalib::string(extraSpaces, ' ').c_str()));
}

void
MonitoringSearchIterator::Dumper::addInt(int64_t value, const vespalib::string &desc)
{
    _str.append(make_string("%*" PRId64 " %s",
                            _intFormatWidth, value, desc.c_str()));
}

void
MonitoringSearchIterator::Dumper::addFloat(double value, const vespalib::string &desc)
{
    _str.append(make_string("%*.*f %s",
                            _floatFormatWidth, _floatFormatPrecision, value, desc.c_str()));
}

void
MonitoringSearchIterator::Dumper::openScope()
{
    _currIndent += _indent;
}

void
MonitoringSearchIterator::Dumper::closeScope()
{
    _currIndent -= _indent;
}

MonitoringSearchIterator::Dumper::Dumper(int indent,
                                         uint32_t textFormatWidth,
                                         uint32_t intFormatWidth,
                                         uint32_t floatFormatWidth,
                                         uint32_t floatFormatPrecision)
    : _indent(indent),
      _textFormatWidth(textFormatWidth),
      _intFormatWidth(intFormatWidth),
      _floatFormatWidth(floatFormatWidth),
      _floatFormatPrecision(floatFormatPrecision),
      _str(),
      _currIndent(0),
      _stack()
{
}

MonitoringSearchIterator::Dumper::~Dumper() {}

void
MonitoringSearchIterator::Dumper::openStruct(const vespalib::string &name, const vespalib::string &type)
{
    if (type == "search::queryeval::MonitoringSearchIterator") {
        _stack.push(ITERATOR);
    } else if (type == "MonitoringSearchIterator::Stats") {
        _stack.push(STATS);
    } else if (name == "children") {
        _stack.push(CHILDREN);
        openScope();
    } else {
        _stack.push(UNKNOWN);
    }
}

void
MonitoringSearchIterator::Dumper::closeStruct()
{
    StructType top = _stack.top();
    _stack.pop();
    if (top == CHILDREN) {
        closeScope();
    }
}

void
MonitoringSearchIterator::Dumper::visitBool(const vespalib::string &name, bool value)
{
    (void) name;
    (void) value;
}

void
MonitoringSearchIterator::Dumper::visitInt(const vespalib::string &name, int64_t value)
{
    if (_stack.top() == STATS) {
        if (name == "numSeeks") {
            addInt(value, "seeks, ");
        } else if (name == "numUnpacks") {
            addInt(value, "unpacks, ");
        }
    }
}

void
MonitoringSearchIterator::Dumper::visitFloat(const vespalib::string &name, double value)
{
    if (_stack.top() == STATS) {
        if (name == "avgDocIdSteps") {
            addFloat(value, "steps/seek, ");
        } else if (name == "avgHitSkips") {
            addFloat(value, "skips/seek, ");
        } else if (name == "numSeeksPerUnpack") {
            addFloat(value, "seeks/unpack\n");
        }
    }
}

void
MonitoringSearchIterator::Dumper::visitString(const vespalib::string &name, const vespalib::string &value)
{
    if (_stack.top() == ITERATOR) {
        if (name == "iteratorName") {
            addText(value);
        }
    }
}

void
MonitoringSearchIterator::Dumper::visitNull(const vespalib::string &name)
{
    (void) name;
}

void
MonitoringSearchIterator::Dumper::visitNotImplemented()
{
}


uint32_t
MonitoringSearchIterator::countHitSkips(uint32_t docId)
{
    uint32_t tmpDocId = _search->getDocId();
    uint32_t numHitSkips = 0;
    for (; ;) {
        _search->seek(tmpDocId + 1);
        tmpDocId = _search->getDocId();
        if (tmpDocId >= docId) {
            break;
        }
        ++numHitSkips;
    }
    return numHitSkips;
}

MonitoringSearchIterator::MonitoringSearchIterator(const vespalib::string &name,
                                                   SearchIterator::UP search,
                                                   bool collectHitSkipStats)
    : _name(name),
      _search(std::move(search)),
      _collectHitSkipStats(collectHitSkipStats),
      _stats()
{
}

MonitoringSearchIterator::~MonitoringSearchIterator() {}

void
MonitoringSearchIterator::doSeek(uint32_t docId)
{
    _stats.seek();
    _stats.step(docId - getDocId());
    if (_collectHitSkipStats) {
        _stats.skip(countHitSkips(docId));
    } else {
        _search->seek(docId);
    }
    LOG(debug, "%s:doSeek(%d) = %d e=%d", _name.c_str(), docId, _search->getDocId(), _search->getEndId());
    setDocId(_search->getDocId());
}

void
MonitoringSearchIterator::doUnpack(uint32_t docId)
{
    LOG(debug, "%s:doUnpack(%d)", _name.c_str(), docId);
    _stats.unpack();
    _search->unpack(docId);
}

const PostingInfo *
MonitoringSearchIterator::getPostingInfo() const
{
    return _search->getPostingInfo();
}

void
MonitoringSearchIterator::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visitor.visitString("iteratorName", _name);
    visitor.visitString("iteratorType", _search->getClassName());
    {
        visitor.openStruct("stats", "MonitoringSearchIterator::Stats");
        visitor.visitInt("numSeeks", _stats.getNumSeeks());
        visitor.visitInt("numDocIdSteps", _stats.getNumDocIdSteps());
        visitor.visitFloat("avgDocIdSteps", _stats.getAvgDocIdSteps());
        visitor.visitInt("numHitSkips", _stats.getNumHitSkips());
        visitor.visitFloat("avgHitSkips", _stats.getAvgHitSkips());
        visitor.visitInt("numUnpacks", _stats.getNumUnpacks());
        visitor.visitFloat("numSeeksPerUnpack", _stats.getNumSeeksPerUnpack());
        visitor.closeStruct();
    }
    _search->visitMembers(visitor);
}

}
