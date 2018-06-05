// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_writer.h"
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_reader.h>

namespace proton {

/**
 * Class used to populate attribute vectors based on visiting the content of a document store.
 */
class AttributePopulator : public IReprocessingReader
{
private:
    AttributeWriter  _writer;
    search::SerialNum _initSerialNum;
    search::SerialNum _currSerialNum;
    search::SerialNum _configSerialNum;
    vespalib::string  _subDbName;

    search::SerialNum nextSerialNum();

    std::vector<vespalib::string> getNames() const;

public:
    typedef std::shared_ptr<AttributePopulator> SP;

    AttributePopulator(const proton::IAttributeManager::SP &mgr,
                       search::SerialNum initSerialNum,
                       const vespalib::string &subDbName,
                       search::SerialNum configSerialNum);
    ~AttributePopulator();

    const IAttributeWriter &getWriter() const { return _writer; }

    // Implements IReprocessingReader
    virtual void handleExisting(uint32_t lid, const std::shared_ptr<document::Document> &doc) override;
    virtual void done() override;
};

} // namespace proton

