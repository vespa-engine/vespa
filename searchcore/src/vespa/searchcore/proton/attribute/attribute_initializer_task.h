// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include <vespa/searchcore/proton/initializer/load_memory_usage.h>

namespace proton {

class AttributeInitializer;
class DocumentMetaStoreAttribute;
class InitializedAttributesResult;

/*
 * Class used to initialize an attribute vector (e.g. load it from disk), used during proton startup.
 */
class AttributeInitializerTask : public initializer::InitializerTask {
private:
    std::unique_ptr<AttributeInitializer>       _initializer;
    std::shared_ptr<DocumentMetaStoreAttribute> _documentMetaStore;
    InitializedAttributesResult&                _result;
    initializer::LoadMemoryUsage                _load_memory_usage;

public:
    AttributeInitializerTask(std::unique_ptr<AttributeInitializer>       initializer,
                             std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore,
                             InitializedAttributesResult&                result) noexcept;
    ~AttributeInitializerTask() override;
    void run() override;
    [[nodiscard]] initializer::LoadMemoryUsage get_load_memory_usage() const noexcept override;
    void accept_visitor(initializer::InitializerTaskVisitor& visitor) override;
};

} // namespace proton
