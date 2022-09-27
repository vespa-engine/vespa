// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "cachedselect.h"
#include "attributefieldvaluenode.h"
#include "select_utils.h"
#include "selectcontext.h"
#include "selectpruner.h"
#include <vespa/document/select/parser.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <cassert>

namespace proton {

using search::AttributeVector;
using search::AttributeGuard;
using document::select::FieldValueNode;
using search::attribute::CollectionType;
using search::attribute::BasicType;

using NodeUP = std::unique_ptr<document::select::Node>;

namespace {

class AttrVisitor : public document::select::CloningVisitor
{
public:
    using AttrMap = std::map<vespalib::string, uint32_t>;

    AttrMap _amap;
    const search::IAttributeManager &_amgr;
    CachedSelect::AttributeVectors &_attributes;
    uint32_t _svAttrs;
    uint32_t _mvAttrs;
    uint32_t _complexAttrs;

    uint32_t getFieldNodes() const { return _fieldNodes; }

    static uint32_t invalidIdx() {
        return std::numeric_limits<uint32_t>::max();
    }
    
    AttrVisitor(const search::IAttributeManager &amgr, CachedSelect::AttributeVectors &attributes);
    ~AttrVisitor() override;

    /*
     * Mutate field value nodes representing single value attributes into
     * attribute field valulue nodes.
     */
    void visitFieldValueNode(const FieldValueNode &expr) override;
};


AttrVisitor::AttrVisitor(const search::IAttributeManager &amgr, CachedSelect::AttributeVectors &attributes)
    : CloningVisitor(),
      _amap(),
      _amgr(amgr),
      _attributes(attributes),
      _svAttrs(0u),
      _mvAttrs(0u),
      _complexAttrs(0u)
{}

AttrVisitor::~AttrVisitor() = default;

bool isSingleValueThatWeHandle(BasicType type) {
    return (type != BasicType::PREDICATE) && (type != BasicType::TENSOR) && (type != BasicType::REFERENCE);
}

void
AttrVisitor::visitFieldValueNode(const FieldValueNode &expr)
{
    ++_fieldNodes;
    // Expression has survived select pruner, thus we know that field is
    // valid for document type.
    bool complex = false;
    vespalib::string name = SelectUtils::extractFieldName(expr, complex);

    auto av = _amgr.readable_attribute_vector(name);
    if (av) {
        if (complex) {
            ++_complexAttrs;
            // Don't try to optimize complex attribute references yet.
            _valueNode = expr.clone();
            return;
        }
        auto guard = av->makeReadGuard(false);
        const auto* attr = guard->attribute();
        if (attr->getCollectionType() == CollectionType::SINGLE) {
            if (isSingleValueThatWeHandle(attr->getBasicType())) {
                ++_svAttrs;
                auto it(_amap.find(name));
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
}

}

CachedSelect::Session::Session(std::unique_ptr<document::select::Node> docSelect,
                               std::unique_ptr<document::select::Node> preDocOnlySelect,
                               std::unique_ptr<document::select::Node> preDocSelect)
    : _docSelect(std::move(docSelect)),
      _preDocOnlySelect(std::move(preDocOnlySelect)),
      _preDocSelect(std::move(preDocSelect))
{
}

bool
CachedSelect::Session::contains(const SelectContext &context) const
{
    if (_preDocSelect && (_preDocSelect->contains(context) == document::select::Result::False)) {
        return false;
    }
    return (!_preDocOnlySelect) ||
            (_preDocOnlySelect && (_preDocOnlySelect->contains(context) == document::select::Result::True));
}

bool
CachedSelect::Session::contains(const document::Document &doc) const
{
    return (_preDocOnlySelect) ||
            (_docSelect && (_docSelect->contains(doc) == document::select::Result::True));
}

const document::select::Node &
CachedSelect::Session::selectNode() const
{
    return (_docSelect ? *_docSelect : *_preDocOnlySelect);
}

void
CachedSelect::setDocumentSelect(SelectPruner &docsPruner)
{
    _allFalse = docsPruner.isFalse();
    _allTrue = docsPruner.isTrue();
    _allInvalid = docsPruner.isInvalid();
    _docSelect = std::move(docsPruner.getNode());
    _fieldNodes = docsPruner.getFieldNodes();
    _attrFieldNodes = docsPruner.getAttrFieldNodes();
}

void
CachedSelect::setPreDocumentSelect(const search::IAttributeManager &attrMgr,
                                   SelectPruner &noDocsPruner)
{
    _attributes.clear();
    AttrVisitor allAttrVisitor(attrMgr, _attributes);
    _docSelect->visit(allAttrVisitor);
    assert(_fieldNodes == allAttrVisitor.getFieldNodes());
    assert(_attrFieldNodes == (allAttrVisitor._mvAttrs + allAttrVisitor._svAttrs + allAttrVisitor._complexAttrs));
    _svAttrFieldNodes = allAttrVisitor._svAttrs;

    if (_fieldNodes == _svAttrFieldNodes) {
        _preDocOnlySelect = std::move(allAttrVisitor.getNode());
    } else if (_svAttrFieldNodes > 0) {
        _attributes.clear();
        AttrVisitor someAttrVisitor(attrMgr, _attributes);
        noDocsPruner.getNode()->visit(someAttrVisitor);
        _preDocSelect = std::move(someAttrVisitor.getNode());
    }
}

CachedSelect::CachedSelect()
    : _attributes(),
      _docSelect(),
      _fieldNodes(0u),
      _attrFieldNodes(0u),
      _svAttrFieldNodes(0u),
      _allFalse(false),
      _allTrue(false),
      _allInvalid(false),
      _preDocOnlySelect(),
      _preDocSelect()
{ }

CachedSelect::~CachedSelect() = default;

void
CachedSelect::set(const vespalib::string &selection,
                  const document::DocumentTypeRepo &repo)
{
    try {
        document::select::Parser parser(repo, document::BucketIdFactory());
        _docSelect = parser.parse(selection);
    } catch (document::select::ParsingFailedException &) {
        _docSelect.reset(nullptr);
    }
    _allFalse = !_docSelect;
    _allTrue = false;
    _allInvalid = false;
}

                  
void
CachedSelect::set(const vespalib::string &selection,
                  const vespalib::string &docTypeName,
                  const document::Document &emptyDoc,
                  const document::DocumentTypeRepo &repo,
                  const search::IAttributeManager *amgr,
                  bool hasFields)
{                  
    set(selection, repo);
    NodeUP parsed(std::move(_docSelect));
    if (!parsed) {
        return;
    }
    SelectPruner docsPruner(docTypeName,
                            amgr,
                            emptyDoc,
                            repo,
                            hasFields,
                            true);
    docsPruner.process(*parsed);
    setDocumentSelect(docsPruner);
    if (amgr == nullptr || _attrFieldNodes == 0u) {
        return;
    }

    SelectPruner noDocsPruner(docTypeName,
                              amgr,
                              emptyDoc,
                              repo,
                              hasFields,
                              false);
    noDocsPruner.process(*parsed);
    setPreDocumentSelect(*amgr, noDocsPruner);
}

std::unique_ptr<CachedSelect::Session>
CachedSelect::createSession() const
{
    return std::make_unique<Session>((_docSelect ? _docSelect->clone() : NodeUP()),
                                     (_preDocOnlySelect ? _preDocOnlySelect->clone() : NodeUP()),
                                     (_preDocSelect ? _preDocSelect->clone() : NodeUP()));
}

}

