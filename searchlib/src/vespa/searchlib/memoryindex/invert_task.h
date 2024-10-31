// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/executor.h>
#include <vector>

namespace document { class Document; }
namespace vespalib { class IDestructorCallback; }

namespace search::memoryindex {

class DocumentInverterContext;
class FieldInverter;
class InvertContext;
class UrlFieldInverter;

/*
 * Task to invert a set of document fields into related field
 * inverters and uri field inverters.
 */
class InvertTask : public vespalib::Executor::Task
{
    using OnWriteDoneType = std::shared_ptr<vespalib::IDestructorCallback>;
    const DocumentInverterContext&                        _inv_context;
    const InvertContext&                                  _context;
    const std::vector<std::unique_ptr<FieldInverter>>&    _inverters;
    const std::vector<std::unique_ptr<UrlFieldInverter>>& _uri_inverters;
    const document::Document&                             _doc;
    const uint32_t                                        _lid;
    const OnWriteDoneType                                 _on_write_done;
public:
    InvertTask(const DocumentInverterContext& inv_context, const InvertContext& context, const std::vector<std::unique_ptr<FieldInverter>>& inverters, const std::vector<std::unique_ptr<UrlFieldInverter>>& uri_inverters, uint32_t lid, const document::Document& doc, const OnWriteDoneType& on_write_done);
    ~InvertTask() override;
    void run() override;
};

}
