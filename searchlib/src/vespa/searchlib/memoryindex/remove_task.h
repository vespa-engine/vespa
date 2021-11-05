// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vector>

namespace search::memoryindex {

class FieldInverter;
class InvertContext;
class UrlFieldInverter;

/*
 * Task to remove a document from a set of field inverters and uri
 * field inverters.
 */
class RemoveTask : public vespalib::Executor::Task
{
    const InvertContext&                                  _context;
    const std::vector<std::unique_ptr<FieldInverter>>&    _inverters;
    const std::vector<std::unique_ptr<UrlFieldInverter>>& _uri_inverters;
    std::vector<uint32_t>                                 _lids;
public:
    RemoveTask(const InvertContext& context, const std::vector<std::unique_ptr<FieldInverter>>& inverters,  const std::vector<std::unique_ptr<UrlFieldInverter>>& uri_inverters, const std::vector<uint32_t>& lids);
    ~RemoveTask() override;
    void run() override;
};

}
