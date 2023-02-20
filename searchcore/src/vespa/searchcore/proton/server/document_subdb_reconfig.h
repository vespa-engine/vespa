// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <memory>

namespace proton {

struct IAttributeManager;
class IAttributeManagerReconfig;
class Matchers;

/**
 * Class representing the result of the prepare step of a IDocumentSubDB reconfig.
 */
class DocumentSubDBReconfig {
private:
    std::shared_ptr<Matchers> _old_matchers;
    std::shared_ptr<Matchers> _new_matchers;
    std::shared_ptr<IAttributeManager> _old_attribute_manager;
    std::shared_ptr<IAttributeManager> _new_attribute_manager;
    std::unique_ptr<IAttributeManagerReconfig> _attribute_manager_reconfig;

public:
    DocumentSubDBReconfig(std::shared_ptr<Matchers> matchers_in, std::shared_ptr<IAttributeManager> attribute_manager_in);
    ~DocumentSubDBReconfig();

    void set_matchers(std::shared_ptr<Matchers> value) {
        _new_matchers = std::move(value);
    }
    bool has_matchers_changed() const noexcept {
        return _old_matchers != _new_matchers;
    }
    std::shared_ptr<Matchers> matchers() const noexcept { return _new_matchers; }
    bool has_attribute_manager_changed() const noexcept {
        return _old_attribute_manager != _new_attribute_manager;
    }
    std::shared_ptr<IAttributeManager> attribute_manager() const noexcept { return _new_attribute_manager; }
    void set_attribute_manager_reconfig(std::unique_ptr<IAttributeManagerReconfig> attribute_manager_reconfig);

    void complete(uint32_t docid_limit, search::SerialNum serial_num);
};

}
