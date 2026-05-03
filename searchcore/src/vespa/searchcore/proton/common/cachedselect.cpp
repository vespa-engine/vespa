// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cachedselect.h"

#include "attributefieldvaluenode.h"
#include "select_utils.h"
#include "selectcontext.h"
#include "selectpruner.h"

#include <vespa/document/select/parser.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/iattributemanager.h>

#include <cassert>

namespace proton {

using document::select::FieldValueNode;
using search::AttributeGuard;
using search::AttributeVector;
using search::attribute::BasicType;
using search::attribute::CollectionType;

using NodeUP = std::unique_ptr<document::select::Node>;

namespace {

class AttrVisitor : public document::select::CloningVisitor {
public:
    using AttrMap = std::map<std::string, uint32_t>;

    AttrMap                          _amap;
    const search::IAttributeManager& _amgr;
    CachedSelect::AttributeVectors&  _attributes;
    uint32_t                         _svAttrs;
    uint32_t                         _mvAttrs;
    uint32_t                         _complexAttrs;

    [[nodiscard]] uint32_t getFieldNodes() const { return _fieldNodes; }

    constexpr static uint32_t invalidIdx() noexcept { return std::numeric_limits<uint32_t>::max(); }

    AttrVisitor(const search::IAttributeManager& amgr, CachedSelect::AttributeVectors& attributes);
    AttrVisitor(const search::IAttributeManager& amgr, CachedSelect::AttributeVectors& attributes,
                AttrMap existing_attr_map);
    ~AttrVisitor() override;

    /*
     * Mutate field value nodes representing single value attributes into
     * attribute field valulue nodes.
     */
    void visitFieldValueNode(const FieldValueNode& expr) override;
};

AttrVisitor::AttrVisitor(const search::IAttributeManager& amgr, CachedSelect::AttributeVectors& attributes)
    : CloningVisitor(), _amap(), _amgr(amgr), _attributes(attributes), _svAttrs(0u), _mvAttrs(0u), _complexAttrs(0u) {
}

AttrVisitor::AttrVisitor(const search::IAttributeManager& amgr, CachedSelect::AttributeVectors& attributes,
                         AttrMap existing_attr_map)
    : CloningVisitor(),
      _amap(std::move(existing_attr_map)),
      _amgr(amgr),
      _attributes(attributes),
      _svAttrs(0u),
      _mvAttrs(0u),
      _complexAttrs(0u) {
}

AttrVisitor::~AttrVisitor() = default;

bool isSingleValueThatWeHandle(BasicType type) {
    return (type != BasicType::PREDICATE) && (type != BasicType::REFERENCE) && (type != BasicType::RAW);
}

void AttrVisitor::visitFieldValueNode(const FieldValueNode& expr) {
    ++_fieldNodes;
    // Expression has survived select pruner, thus we know that field is
    // valid for document type.
    bool        complex = false;
    std::string name = SelectUtils::extractFieldName(expr, complex);

    auto av = _amgr.readable_attribute_vector(name);
    if (av) {
        if (complex) {
            ++_complexAttrs;
            // Don't try to optimize complex attribute references yet.
            _valueNode = expr.clone();
            _priority = FieldValuePriority;
            return;
        }
        auto        guard = av->makeReadGuard(false);
        const auto* attr = guard->attribute();
        if (attr->getCollectionType() == CollectionType::SINGLE) {
            if (isSingleValueThatWeHandle(attr->getBasicType())) {
                ++_svAttrs;
                auto     it(_amap.find(name));
                uint32_t idx(invalidIdx());
                if (it == _amap.end()) {
                    // Allocate new location for guard
                    idx = _attributes.size();
                    _amap[name] = idx;
                    _attributes.push_back(av);
                } else {
                    // Already allocated location for guard
                    idx = it->second;
                }
                assert(idx != invalidIdx());
                _valueNode = std::make_unique<AttributeFieldValueNode>(expr.getDocType(), name, idx);
            } else {
                ++_complexAttrs;
                // Don't try to optimize predicate/tensor/reference attributes yet.
                _valueNode = expr.clone();
            }
        } else {
            // Don't try to optimize multivalue attribute vectors yet
            ++_mvAttrs;
            _valueNode = expr.clone();
        }
    } else {
        _valueNode = expr.clone();
    }
    _priority = FieldValuePriority;
}

} // namespace

CachedSelect::Session::Session(std::unique_ptr<document::select::Node> docSelect,
                               std::unique_ptr<document::select::Node> preDocOnlySelect,
                               std::unique_ptr<document::select::Node> preDocSelect)
    : _docSelect(std::move(docSelect)),
      _preDocOnlySelect(std::move(preDocOnlySelect)),
      _preDocSelect(std::move(preDocSelect)) {
}

bool CachedSelect::Session::contains_pre_doc(const SelectContext& context) const {
    if (_preDocSelect && (_preDocSelect->contains(context) == document::select::Result::False)) {
        return false;
    }
    return (!_preDocOnlySelect) ||
           (_preDocOnlySelect && (_preDocOnlySelect->contains(context) == document::select::Result::True));
}

bool CachedSelect::Session::contains_doc(const SelectContext& context) const {
    return (_preDocOnlySelect) || (_docSelect && (_docSelect->contains(context) == document::select::Result::True));
}

const document::select::Node& CachedSelect::Session::selectNode() const {
    return (_docSelect ? *_docSelect : *_preDocOnlySelect);
}

void CachedSelect::setDocumentSelect(SelectPruner& docsPruner) {
    _always_false = docsPruner.isFalse();
    _always_true = docsPruner.isTrue();
    _always_invalid = docsPruner.isInvalid();
    _doc_select_resultset = docsPruner.getResultSet();
    _docSelect = std::move(docsPruner.getNode());
    _fieldNodes = docsPruner.getFieldNodes();
    _attrFieldNodes = docsPruner.getAttrFieldNodes();
    _document_id_nodes = docsPruner.get_document_id_nodes();
}

void CachedSelect::setPreDocumentSelect(const search::IAttributeManager& attrMgr, SelectPruner& noDocsPruner) {
    _attributes.clear();
    AttrVisitor allAttrVisitor(attrMgr, _attributes);
    _docSelect->visit(allAttrVisitor);
    assert(_fieldNodes == allAttrVisitor.getFieldNodes());
    assert(_attrFieldNodes == (allAttrVisitor._mvAttrs + allAttrVisitor._svAttrs + allAttrVisitor._complexAttrs));
    _svAttrFieldNodes = allAttrVisitor._svAttrs;
    _pre_doc_select_resultset = noDocsPruner.getResultSet();

    if (_fieldNodes == _svAttrFieldNodes + _document_id_nodes) {
        _preDocOnlySelect = std::move(allAttrVisitor.getNode());
    } else if (_svAttrFieldNodes + _document_id_nodes > 0) {
        // Also let document-level selection use attribute wiring; otherwise imported fields
        // would not resolve to anything, as these do not exist in the concrete document itself.
        _docSelect = std::move(allAttrVisitor.getNode());
        [[maybe_unused]] size_t attrs_before = _attributes.size();
        AttrVisitor             someAttrVisitor(attrMgr, _attributes, std::move(allAttrVisitor._amap));
        assert(_attributes.size() == attrs_before);
        noDocsPruner.getNode()->visit(someAttrVisitor);
        _preDocSelect = std::move(someAttrVisitor.getNode());
    }
}

CachedSelect::CachedSelect()
    : _attributes(),
      _docSelect(),
      _fieldNodes(0u),
      _attrFieldNodes(0u),
      _document_id_nodes(0u),
      _svAttrFieldNodes(0u),
      _always_false(false),
      _always_true(false),
      _always_invalid(false),
      _doc_select_resultset(),
      _pre_doc_select_resultset(),
      _preDocOnlySelect(),
      _preDocSelect() {
}

CachedSelect::~CachedSelect() = default;

void CachedSelect::set(const std::string& selection, const document::IDocumentTypeRepo& repo) {
    try {
        document::select::Parser parser(repo, document::BucketIdFactory());
        _docSelect = parser.parse(selection);
    } catch (document::select::ParsingFailedException&) {
        _docSelect.reset();
    }
    _always_false = !_docSelect;
    _always_true = false;
    _always_invalid = false;
}

void CachedSelect::set(const std::string& selection, const std::string& docTypeName,
                       const document::Document& emptyDoc, const document::IDocumentTypeRepo& repo,
                       const search::IAttributeManager* amgr, bool hasFields, bool has_document_ids) {
    set(selection, repo);
    NodeUP parsed(std::move(_docSelect));
    if (!parsed) {
        return;
    }
    SelectPruner docsPruner(docTypeName, amgr, emptyDoc, repo, hasFields, has_document_ids, true);
    docsPruner.process(*parsed);
    setDocumentSelect(docsPruner);
    if ((amgr == nullptr || _attrFieldNodes == 0u) && _document_id_nodes == 0) {
        return;
    }

    SelectPruner noDocsPruner(docTypeName, amgr, emptyDoc, repo, hasFields, has_document_ids, false);
    noDocsPruner.process(*parsed);
    setPreDocumentSelect(*amgr, noDocsPruner);
}

std::unique_ptr<CachedSelect::Session> CachedSelect::createSession() const {
    return std::make_unique<Session>((_docSelect ? _docSelect->clone() : NodeUP()),
                                     (_preDocOnlySelect ? _preDocOnlySelect->clone() : NodeUP()),
                                     (_preDocSelect ? _preDocSelect->clone() : NodeUP()));
}

} // namespace proton
