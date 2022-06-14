// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_populator.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_populator");

using vespalib::IDestructorCallback;

namespace proton {

namespace {

class PopulateDoneContext : public IDestructorCallback
{
    std::shared_ptr<document::Document> _doc;
public:
    PopulateDoneContext(std::shared_ptr<document::Document> doc) noexcept
        : _doc(std::move(doc))
    {
    }
    ~PopulateDoneContext() override = default;
};

}

search::SerialNum
AttributePopulator::nextSerialNum()
{
    assert(_currSerialNum <= _configSerialNum);
    return _currSerialNum++;
}

std::vector<vespalib::string>
AttributePopulator::getNames() const
{
    std::vector<search::AttributeGuard> attrs;
    _writer.getAttributeManager()->getAttributeList(attrs);
    std::vector<vespalib::string> names;
    names.reserve(attrs.size());
    for (const search::AttributeGuard &attr : attrs) {
        names.push_back(_subDbName + ".attribute." + attr->getName());
    }
    return names;
}

AttributePopulator::AttributePopulator(const proton::IAttributeManager::SP &mgr,
                                       search::SerialNum initSerialNum,
                                       const vespalib::string &subDbName,
                                       search::SerialNum configSerialNum)
    : _writer(mgr),
      _initSerialNum(initSerialNum),
      _currSerialNum(initSerialNum),
      _configSerialNum(configSerialNum),
      _subDbName(subDbName)
{
    if (LOG_WOULD_LOG(event)) {
        EventLogger::populateAttributeStart(getNames());
    }
}

AttributePopulator::~AttributePopulator()
{
    if (LOG_WOULD_LOG(event)) {
        EventLogger::populateAttributeComplete(getNames(),_currSerialNum - _initSerialNum);
    }
}

void
AttributePopulator::handleExisting(uint32_t lid, const std::shared_ptr<document::Document> &doc)
{
    search::SerialNum serialNum(nextSerialNum());
    _writer.put(serialNum, *doc, lid, std::make_shared<PopulateDoneContext>(doc));
    vespalib::Gate gate;
    _writer.forceCommit(serialNum, std::make_shared<vespalib::GateCallback>(gate));
    gate.await();
}

void
AttributePopulator::done()
{
    auto mgr = _writer.getAttributeManager();
    auto flushTargets = mgr->getFlushTargets();
    for (const auto &flushTarget : flushTargets) {
        assert(flushTarget->getFlushedSerialNum() < _configSerialNum);
        auto task = flushTarget->initFlush(_configSerialNum, std::make_shared<search::FlushToken>());
        // shrink target only return task if able to shrink.
        if (task) {
            task->run();
        }
        assert(flushTarget->getFlushedSerialNum() == _configSerialNum);
    }
}

} // namespace proton
