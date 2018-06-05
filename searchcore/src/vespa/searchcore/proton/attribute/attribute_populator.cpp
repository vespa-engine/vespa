// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_populator.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/searchlib/attribute/attributevector.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.attribute_populator");

using search::IDestructorCallback;

namespace proton {

namespace {

class PopulateDoneContext : public IDestructorCallback
{
    std::shared_ptr<document::Document> _doc;
public:
    PopulateDoneContext(const std::shared_ptr<document::Document> &doc)
        : _doc(doc)
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
        EventLogger::populateAttributeComplete(getNames(),
                _currSerialNum - _initSerialNum);
    }
}

void
AttributePopulator::handleExisting(uint32_t lid, const std::shared_ptr<document::Document> &doc)
{
    search::SerialNum serialNum(nextSerialNum());
    auto populateDoneContext = std::make_shared<PopulateDoneContext>(doc);
    _writer.put(serialNum, *doc, lid, true, populateDoneContext);
}

void
AttributePopulator::done()
{
    auto mgr = _writer.getAttributeManager();
    auto flushTargets = mgr->getFlushTargets();
    for (const auto &flushTarget : flushTargets) {
        assert(flushTarget->getFlushedSerialNum() < _configSerialNum);
        auto task = flushTarget->initFlush(_configSerialNum);
        // shrink target only return task if able to shrink.
        if (task) {
            task->run();
        }
        assert(flushTarget->getFlushedSerialNum() == _configSerialNum);
    }
}

} // namespace proton
