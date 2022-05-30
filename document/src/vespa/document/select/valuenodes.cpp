// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "valuenodes.h"
#include "visitor.h"
#include "parser.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/fieldvalue/referencefieldvalue.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/util/md5.h>
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/text/lowercase.h>
#include <iomanip>
#include <sys/time.h>

#include <vespa/log/log.h>
LOG_SETUP(".document.select.valuenode");

namespace document::select {

namespace {
    bool documentTypeEqualsName(const DocumentType& type, vespalib::stringref name)
    {
        return (type.getName() == name);
    }
}

InvalidValueNode::InvalidValueNode(vespalib::stringref name)
    : _name(name)
{}


void
InvalidValueNode::visit(Visitor &visitor) const
{
    visitor.visitInvalidValueNode(*this);
}


void
InvalidValueNode::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (hadParentheses()) out << '(';
    out << _name;
    if (hadParentheses()) out << ')';
}

NullValueNode::NullValueNode() = default;

void
NullValueNode::visit(Visitor &visitor) const
{
    visitor.visitNullValueNode(*this);
}


void
NullValueNode::print(std::ostream& out, bool verbose,
                        const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (hadParentheses()) out << '(';
    out << "null";
    if (hadParentheses()) out << ')';
}

StringValueNode::StringValueNode(vespalib::stringref val)
    : _value(val)
{
}


void
StringValueNode::visit(Visitor &visitor) const
{
    visitor.visitStringValueNode(*this);
}


void
StringValueNode::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (hadParentheses()) out << '(';
    out << "\"" << StringUtil::escape(_value) << "\"";
    if (hadParentheses()) out << ')';
}


void
IntegerValueNode::visit(Visitor &visitor) const
{
    visitor.visitIntegerValueNode(*this);
}


void
IntegerValueNode::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (hadParentheses()) out << '(';
    out << _value;
    if (hadParentheses()) out << ')';
}

void
BoolValueNode::visit(Visitor& visitor) const
{
    visitor.visitBoolValueNode(*this);
}

void
BoolValueNode::print(std::ostream& out,
                     [[maybe_unused]] bool verbose,
                     [[maybe_unused]] const std::string& indent) const
{
    if (hadParentheses()) out << '(';
    out << bool_value_str();
    if (hadParentheses()) out << ')';
}


int64_t
CurrentTimeValueNode::getValue() const
{
    struct timeval mytime;
    gettimeofday(&mytime, nullptr);
    return mytime.tv_sec;
}


void
CurrentTimeValueNode::visit(Visitor &visitor) const
{
    visitor.visitCurrentTimeValueNode(*this);
}


void
CurrentTimeValueNode::print(std::ostream& out, bool verbose,
                            const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "now()";
}

std::unique_ptr<Value>
VariableValueNode::getValue(const Context& context) const {
    return context.getValue(_value);
}

void
VariableValueNode::visit(Visitor &visitor) const
{
    visitor.visitVariableValueNode(*this);
}


void
VariableValueNode::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (hadParentheses()) out << '(';
    out << "$" << _value;
    if (hadParentheses()) out << ')';
}


void
FloatValueNode::visit(Visitor &visitor) const
{
    visitor.visitFloatValueNode(*this);
}


void
FloatValueNode::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (hadParentheses()) out << '(';
    out << _value;
    if (hadParentheses()) out << ')';
}

FieldValueNode::FieldValueNode(const vespalib::string& doctype,
                               const vespalib::string& fieldExpression)
    : _doctype(doctype),
      _fieldExpression(fieldExpression),
      _fieldName(extractFieldName(fieldExpression))
{
}

FieldValueNode::~FieldValueNode() = default;

namespace {

size_t first_ident_length_or_npos(const vespalib::string& expr) {
    for (size_t i = 0; i < expr.size(); ++i) {
        switch (expr[i]) {
        case '.':
        case '{':
        case '[':
        case ' ':
        case '\n':
        case '\t':
            return i;
        default:
            continue;
        }
    }
    return vespalib::string::npos;
}

}

// TODO remove this pile of fun in favor of actually parsed AST nodes...!
vespalib::string
FieldValueNode::extractFieldName(const vespalib::string & fieldExpression) {
    // When we get here the actual contents of the field expression shall already
    // have been structurally and syntactically verified by the parser.
    return fieldExpression.substr(0, first_ident_length_or_npos(fieldExpression));
}

namespace {

class IteratorHandler : public fieldvalue::IteratorHandler {
public:
    IteratorHandler();
    ~IteratorHandler() override;
    bool hasSingleValue() const;
    std::unique_ptr<Value> stealSingleValue() &&;
    std::vector<ArrayValue::VariableValue> stealValues() &&;
private:
    std::unique_ptr<Value> _firstValue;
    std::vector<ArrayValue::VariableValue> _values;

    void onPrimitive(uint32_t fid, const Content &fv) override;
    std::unique_ptr<Value> getInternalValue(const FieldValue &fval) const;
};

IteratorHandler::IteratorHandler() = default;
IteratorHandler::~IteratorHandler() = default;

bool
IteratorHandler::hasSingleValue() const {
    return _firstValue && _values.empty();
}

std::unique_ptr<Value>
IteratorHandler::stealSingleValue() && {
    return std::move(_firstValue);
}

std::vector<ArrayValue::VariableValue>
IteratorHandler::stealValues() && {
    if (_firstValue) {
        _values.insert(_values.begin(), ArrayValue::VariableValue(fieldvalue::VariableMap(), Value::SP(_firstValue.release())));
    }

    return std::move(_values);
}

void
IteratorHandler::onPrimitive(uint32_t fid, const Content& fv) {
    (void) fid;
    if (!_firstValue && getVariables().empty()) {
        _firstValue = getInternalValue(fv.getValue());
    } else {
        _values.emplace_back(stealVariables(), Value::SP(getInternalValue(fv.getValue()).release()));
    }
}

std::unique_ptr<Value>
IteratorHandler::getInternalValue(const FieldValue& fval) const
{
    switch(fval.type()) {
        case FieldValue::Type::BOOL:
        {
            const auto& val(dynamic_cast<const BoolFieldValue&>(fval));
            return std::make_unique<IntegerValue>(val.getAsInt(), false);
        }
        case FieldValue::Type::INT:
        {
            const auto& val(dynamic_cast<const IntFieldValue&>(fval));
            return std::make_unique<IntegerValue>(val.getAsInt(), false);
        }
        case FieldValue::Type::BYTE:
        {
            const auto& val(dynamic_cast<const ByteFieldValue&>(fval));
            return std::make_unique<IntegerValue>(val.getAsByte(), false);
        }
        case FieldValue::Type::LONG:
        {
            const auto& val(dynamic_cast<const LongFieldValue&>(fval));
            return std::make_unique<IntegerValue>(val.getAsLong(), false);
        }
        case FieldValue::Type::FLOAT:
        {
            const auto& val(dynamic_cast<const FloatFieldValue&>(fval));
            return std::make_unique<FloatValue>(val.getAsFloat());
        }
        case FieldValue::Type::DOUBLE:
        {
            const auto& val(dynamic_cast<const DoubleFieldValue&>(fval));
            return std::make_unique<FloatValue>(val.getAsDouble());
        }
        case FieldValue::Type::STRING:
        {
            const auto& val(dynamic_cast<const StringFieldValue&>(fval));
            return std::make_unique<StringValue>(val.getAsString());
        }
        case FieldValue::Type::REFERENCE:
        {
            const auto& val(dynamic_cast<const ReferenceFieldValue&>(fval));
            if (val.hasValidDocumentId()) {
                return std::make_unique<StringValue>(val.getDocumentId().toString());
            } else {
                return std::make_unique<InvalidValue>();
            }
        }
        case FieldValue::Type::ARRAY:
        {
            const auto& val(dynamic_cast<const ArrayFieldValue&>(fval));
            if (val.size() == 0) {
                return std::make_unique<NullValue>();
            } else {
                // TODO: Array comparison.
                return std::make_unique<ArrayValue>(std::vector<ArrayValue::VariableValue>());
            }
        }
        case FieldValue::Type::STRUCT:
        {
            const auto& val(dynamic_cast<const StructFieldValue&>(fval));
            if (val.empty()) {
                return std::make_unique<NullValue>();
            } else {
                StructValue::ValueMap values;
                for (StructFieldValue::const_iterator it(val.begin()); it != val.end(); ++it) {
                    FieldValue::UP fv(val.getValue(it.field()));
                    values[it.field().getName()] = Value::SP(getInternalValue(*fv).release());
                }
                return std::make_unique<StructValue>(std::move(values));
            }
        }
        case FieldValue::Type::MAP:
        {
            const auto& val(static_cast<const MapFieldValue&>(fval));
            if (val.isEmpty()) {
                return std::make_unique<NullValue>();
            } else {
                // TODO: Map comparison
                return std::make_unique<ArrayValue>(std::vector<ArrayValue::VariableValue>());
            }
        }
        default:
            break;
    }
    LOG(warning, "Tried to use unsupported datatype %s in field comparison",
        fval.getDataType()->toString().c_str());
    return std::make_unique<InvalidValue>();
}

}

void
FieldValueNode::initFieldPath(const DocumentType& type) const {
    if (_fieldPath.empty()) {
        type.buildFieldPath(_fieldPath, _fieldExpression);
    }
}

namespace {

bool looks_like_complex_field_path(const vespalib::string& expr) {
    for (const char c : expr) {
        switch (c) {
        case '.':
        case '[':
        case '{':
            return true;
        default: continue;
        }
    }
    return false;
}

bool is_simple_imported_field(const vespalib::string& expr, const DocumentType& doc_type) {
    if (looks_like_complex_field_path(expr)) {
        return false;
    }
    return (doc_type.has_imported_field_name(expr));
}

}

std::unique_ptr<Value>
FieldValueNode::getValue(const Context& context) const
{
    if (context._doc == nullptr) {
        return std::make_unique<InvalidValue>();
    }

    const Document& doc = *context._doc;

    if (!documentTypeEqualsName(doc.getType(), _doctype)) {
        return std::make_unique<InvalidValue>();
    }
    // Imported fields can only be meaningfully evaluated inside Proton, so we
    // explicitly treat them as if they are valid fields with missing values. This
    // will be treated the same as if it's a normal field by the selection operators.
    // This avoids any awkward interaction with Invalid values or having to
    // augment the FieldPath code with knowledge of imported fields.
    // When a selection is running inside Proton, it will patch FieldValueNodes for
    // imported fields, which removes this check entirely.
    if (is_simple_imported_field(_fieldExpression, doc.getType())) {
        return std::make_unique<NullValue>();
    }
    try {
        initFieldPath(doc.getType());

        IteratorHandler handler;
        doc.iterateNested(_fieldPath.getFullRange(), handler);

        if (handler.hasSingleValue()) {
            return std::move(handler).stealSingleValue();
        } else {
            std::vector<ArrayValue::VariableValue> values = std::move(handler).stealValues();

            if (values.empty()) {
                return std::make_unique<NullValue>();
            } else {
                return std::make_unique<ArrayValue>(std::move(values));
            }
        }
    } catch (vespalib::IllegalArgumentException& e) {
        LOG(warning, "Caught exception while fetching field from document: %s", e.what());
        return std::make_unique<InvalidValue>();
    } catch (FieldNotFoundException& e) {
        LOG(warning, "Tried to compare to field %s, not found in document type", _fieldExpression.c_str());
        return std::make_unique<InvalidValue>();
    }
}

void
FieldValueNode::visit(Visitor &visitor) const
{
    visitor.visitFieldValueNode(*this);
}


void
FieldValueNode::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (hadParentheses()) out << '(';
    out << _doctype << "." << _fieldExpression;
    if (hadParentheses()) out << ')';
}


std::unique_ptr<Value>
FieldValueNode::traceValue(const Context &context, std::ostream& out) const
{
    if (context._doc == nullptr) {
        return defaultTrace(getValue(context), out);
    }
    const Document &doc(*context._doc);
    if (!documentTypeEqualsName(doc.getType(), _doctype)) {
        out << "Document is of type " << doc.getType() << " which isn't a "
            << _doctype << " document, thus resolving invalid.\n";
        return std::make_unique<InvalidValue>();
    }
    if (is_simple_imported_field(_fieldExpression, doc.getType())) {
        out << "Field '" << _fieldExpression << "' refers to an imported field; "
            << "returning NullValue to treat this as an unset field value.\n";
        return std::make_unique<NullValue>();
    }
    try {
        initFieldPath(doc.getType());

        IteratorHandler handler;
        doc.iterateNested(_fieldPath.getFullRange(), handler);

        if (handler.hasSingleValue()) {
            return std::move(handler).stealSingleValue();
        } else {
            std::vector<ArrayValue::VariableValue> values = std::move(handler).stealValues();

            if (values.size() == 0) {
                return std::make_unique<NullValue>();
            } else {
                return std::make_unique<ArrayValue>(std::move(values));
            }
        }
    } catch (FieldNotFoundException& e) {
        LOG(warning, "Tried to compare to field %s, not found in document type",
                     _fieldExpression.c_str());
        out << "Field not found in document type " << doc.getType()
            << ". Returning invalid.\n";
        return std::make_unique<InvalidValue>();
    }
}

IdValueNode::IdValueNode(const BucketIdFactory& bucketIdFactory,
                         vespalib::stringref name, vespalib::stringref type,
                         int widthBits, int divisionBits)
    : _bucketIdFactory(bucketIdFactory),
      _id(name),
      _typestring(type),
      _type(ALL),
      _widthBits(widthBits),
      _divisionBits(divisionBits)
{
    if (type.length() > 2) switch (type[0]) {
    case 'b': _type = BUCKET;
        break;
    case 'n': _type = NS;
        break;
    case 'g':
        if (type[1] == 'r') {
            _type = GROUP;
        } else if (type[1] == 'i') {
            _type = GID;
        }
        break;
    case 's': {
        if (type[1] == 'c') { _type = SCHEME; } else { _type = SPEC; }
        break;
    }
    case 't':
        _type = TYPE;
        break;
    case 'u':
        _type = USER;
        break;
        break;
    }
}


std::unique_ptr<Value>
IdValueNode::getValue(const Context& context) const
{
    if (context._doc != NULL) {
        return getValue(context._doc->getId());
    } else if (context._docId != NULL) {
        return getValue(*context._docId);
    } else {
        return getValue(context._docUpdate->getId());
    }
}


std::unique_ptr<Value>
IdValueNode::getValue(const DocumentId& id) const
{
    vespalib::string value;
    switch (_type) {
    case BUCKET:
        return std::make_unique<IntegerValue>(_bucketIdFactory.getBucketId(id).getId(), true);
    case NS:
        value = id.getScheme().getNamespace(); break;
    case SCHEME:
        value = "id";
        break;
    case TYPE:
        if (id.getScheme().hasDocType()) {
            value = id.getScheme().getDocType();
        } else {
            return std::make_unique<InvalidValue>();
        }
        break;
    case SPEC:
        value = id.getScheme().getNamespaceSpecific();
        break;
    case ALL:
        value = id.getScheme().toString();
        break;
    case GROUP:
        if (id.getScheme().hasGroup()) {
            value = id.getScheme().getGroup();
        } else {
            fprintf(stderr, "***** Returning invalid value for %s\n",
                    id.toString().c_str());
            return std::make_unique<InvalidValue>();
        }
        break;
    case GID:
        value = id.getGlobalId().toString();
        break;
    case USER:
        if (id.getScheme().hasNumber()) {
            return std::make_unique<IntegerValue>(id.getScheme().getNumber(), false);
        } else {
            return std::make_unique<InvalidValue>();
        }
    }

    return std::make_unique<StringValue>(value);
}


std::unique_ptr<Value>
IdValueNode::traceValue(const Context& context,
                        std::ostream &out) const
{
    if (context._doc != NULL) {
        return traceValue(context._doc->getId(), out);
    } else if (context._docId != NULL) {
        return traceValue(*context._docId, out);
    } else {
        return traceValue(context._docUpdate->getId(), out);
    }
}


std::unique_ptr<Value>
IdValueNode::traceValue(const DocumentId& id, std::ostream& out) const
{
    vespalib::string value;
    switch (_type) {
    case BUCKET:
        {
            document::BucketId bucket(_bucketIdFactory.getBucketId(id));
            out << "Found id.bucket specification. Resolved to " << bucket.toString() << ".\n";
            return std::make_unique<IntegerValue>(bucket.getId(), true);
        }
    case NS:
        value = id.getScheme().getNamespace();
        out << "Resolved id.namespace to value\"" << value << "\".\n";
        break;
    case SCHEME:
        value = "id";
        out << "Resolved id.scheme to value\"" << value << "\".\n";
        break;
    case TYPE:
        if (id.getScheme().hasDocType()) {
            value = id.getScheme().getDocType();
            out << "Resolved id.type to value\"" << value << "\".\n";
        } else {
            out << "Could not resolve type of doc " << id << ".\n";
            return std::make_unique<InvalidValue>();
        }
        break;
    case SPEC:
        value = id.getScheme().getNamespaceSpecific();
        out << "Resolved id.specific to value\"" << value << "\".\n";
        break;
    case ALL:
        value = id.getScheme().toString();
        out << "Resolved id to \"" << value << "\".\n";
        break;
    case GROUP:
        if (id.getScheme().hasGroup()) {
            value = id.getScheme().getGroup();
            out << "Resolved group of doc (type id) to \"" << value << "\".\n";
        } else {
            out << "Can't resolve group of doc \"" << id << "\".\n";
            return std::make_unique<InvalidValue>();
        }
        break;
    case GID:
        value = id.getGlobalId().toString();
        out << "Resolved gid to \"" << value << "\".\n";
        break;
    case USER:
        if (id.getScheme().hasNumber()) {
            auto result = std::make_unique<IntegerValue>(id.getScheme().getNumber(), false);
            out << "Resolved user of doc type 'id' to " << *result << ".\n";
            return result;
        } else {
            out << "Could not resolve user of doc " << id << ".\n";
            return std::make_unique<InvalidValue>();
        }
    }

    return std::make_unique<StringValue>(value);
}


void
IdValueNode::visit(Visitor &visitor) const
{
    visitor.visitIdValueNode(*this);
}


void
IdValueNode::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    (void) verbose; (void) indent;
    if (hadParentheses()) out << '(';
    out << _id;
    if (_type != ALL) {
        out << '.' << _typestring;
    }
    if (hadParentheses()) out << ')';
}

namespace {
    union HashUnion {
        unsigned char _key[16];
        int64_t       _hash[2];
    };
    int64_t hash(const void* data, uint32_t len) {
        HashUnion hash;
        fastc_md5sum((const unsigned char*) data, len, hash._key);
        return hash._hash[0];
    }
}

FunctionValueNode::FunctionValueNode(vespalib::stringref name,
                                     std::unique_ptr<ValueNode> src)
    : _function(),
      _funcname(name),
      _source(std::move(src))
{
    if (name == "lowercase") {
        _function = LOWERCASE;
    } else if (name == "hash") {
        _function = HASH;
    } else if (name == "abs") {
        _function = ABS;
    } else {
        throw ParsingFailedException("No function '"+name+"' exist.",
                                     VESPA_STRLOC);
    }
}

std::unique_ptr<Value>
FunctionValueNode::getValue(std::unique_ptr<Value> val) const
{
    switch (val->getType()) {
        case Value::String:
        {
            StringValue& sval(static_cast<StringValue&>(*val));
            if (_function == LOWERCASE) {
                return std::make_unique<StringValue>(vespalib::LowerCase::convert(sval.getValue()));
            } else if (_function == HASH) {
                return std::make_unique<IntegerValue>(hash(sval.getValue().c_str(), sval.getValue().size()), false);
            }
            break;
        }
        case Value::Float:
        {
            FloatValue& fval(static_cast<FloatValue&>(*val));
            if (_function == HASH) {
                FloatValue::ValueType ffval = fval.getValue();
                return std::make_unique<IntegerValue>(hash(&ffval, sizeof(ffval)), false);
            } else if (_function == ABS) {
                FloatValue::ValueType ffval = fval.getValue();
                if (ffval < 0) ffval *= -1;
                return std::make_unique<FloatValue>(ffval);
            }
            break;
        }
        case Value::Integer:
        {
            IntegerValue& ival(static_cast<IntegerValue&>(*val));
            if (_function == HASH) {
                IntegerValue::ValueType iival = ival.getValue();
                return std::make_unique<IntegerValue>(hash(&iival, sizeof(iival)), false);
            } else if (_function == ABS) {
                IntegerValue::ValueType iival = ival.getValue();
                if (iival < 0) iival *= -1;
                return std::make_unique<IntegerValue>(iival, false);
            }
            break;
        }
        case Value::Bucket:
        {
            throw ParsingFailedException(
                    "No functioncalls are allowed on value of type bucket",
                    VESPA_STRLOC);
            break;
        }

        case Value::Array: break;
        case Value::Struct: break;
        case Value::Invalid: break;
        case Value::Null: break;
    }
    return std::make_unique<InvalidValue>();
}

std::unique_ptr<Value>
FunctionValueNode::traceValue(std::unique_ptr<Value> val, std::ostream& out) const
{
    switch (val->getType()) {
        case Value::String:
        {
            StringValue& sval(static_cast<StringValue&>(*val));
            if (_function == LOWERCASE) {
                auto result = std::make_unique<StringValue>(vespalib::LowerCase::convert(sval.getValue()));
                out << "Performed lowercase function on '" << sval
                    << "' => '" << *result << "'.\n";
                return result;
            } else if (_function == HASH) {
                auto result = std::make_unique<IntegerValue>(hash(sval.getValue().c_str(), sval.getValue().size()), false);
                out << "Performed hash on string '" << sval << "' -> "
                    << *result << "\n";
                return result;
            }
            break;
        }
        case Value::Float:
        {
            FloatValue& fval(static_cast<FloatValue&>(*val));
            if (_function == HASH) {
                FloatValue::ValueType ffval = fval.getValue();
                auto result = std::make_unique<IntegerValue>(hash(&ffval, sizeof(ffval)), false);
                out << "Performed hash on float " << ffval << " -> " << *result
                    << "\n";
                return result;
            } else if (_function == ABS) {
                FloatValue::ValueType ffval = fval.getValue();
                if (ffval < 0) ffval *= -1;
                out << "Performed abs on float " << fval.getValue() << " -> "
                    << ffval << "\n";
                return std::make_unique<FloatValue>(ffval);
            }
            break;
        }
        case Value::Integer:
        {
            IntegerValue& ival(static_cast<IntegerValue&>(*val));
            if (_function == HASH) {
                IntegerValue::ValueType iival = ival.getValue();
                auto result = std::make_unique<IntegerValue>(hash(&iival, sizeof(iival)), false);
                out << "Performed hash on float " << iival << " -> " << *result
                    << "\n";
                return result;
            } else if (_function == ABS) {
                IntegerValue::ValueType iival = ival.getValue();
                if (iival < 0) iival *= -1;
                out << "Performed abs on integer " << ival.getValue() << " -> "
                    << iival << "\n";
                return std::make_unique<IntegerValue>(iival, false);
            }
            break;
        }
        case Value::Bucket: break;
        case Value::Array: break;
        case Value::Struct: break;
        case Value::Invalid: break;
        case Value::Null: break;
    }
    out << "Cannot use function " << _function << " on a value of type "
        << val->getType() << ". Resolving invalid.\n";
    return std::make_unique<InvalidValue>();
}


void
FunctionValueNode::visit(Visitor &visitor) const
{
    visitor.visitFunctionValueNode(*this);
}


void
FunctionValueNode::print(std::ostream& out, bool verbose,
                         const std::string& indent) const
{
    if (hadParentheses()) out << '(';
    _source->print(out, verbose, indent);
    out << '.' << _funcname << "()";
    if (hadParentheses()) out << ')';
}

ArithmeticValueNode::ArithmeticValueNode(
        std::unique_ptr<ValueNode> left, vespalib::stringref op,
        std::unique_ptr<ValueNode> right)
    : ValueNode(std::max(left->max_depth(), right->max_depth()) + 1),
      _operator(),
      _left(std::move(left)),
      _right(std::move(right))
{
    if (op.size() == 1) switch (op[0]) {
        case '+': _operator = ADD; return;
        case '-': _operator = SUB; return;
        case '*': _operator = MUL; return;
        case '/': _operator = DIV; return;
        case '%': _operator = MOD; return;
    }
    throw ParsingFailedException(
            "Arithmetic operator '"+op+"' does not exist.", VESPA_STRLOC);
}

const char*
ArithmeticValueNode::getOperatorName() const
{
    switch (_operator) {
        case ADD: return "+";
        case SUB: return "-";
        case MUL: return "*";
        case DIV: return "/";
        case MOD: return "%";
    }
    return "UNKNOWN";
}



std::unique_ptr<Value>
ArithmeticValueNode::getValue(std::unique_ptr<Value> lval,
                              std::unique_ptr<Value> rval) const
{
    switch (_operator) {
        case ADD:
        {
            if (lval->getType() == Value::String &&
                rval->getType() == Value::String)
            {
                StringValue& slval(static_cast<StringValue&>(*lval));
                StringValue& srval(static_cast<StringValue&>(*rval));
                return std::make_unique<StringValue>(slval.getValue() + srval.getValue());
            }
        }
        [[fallthrough]];
        case SUB:
        case MUL:
        case DIV:
        {
            if (lval->getType() == Value::Integer &&
                rval->getType() == Value::Integer)
            {
                IntegerValue& ilval(static_cast<IntegerValue&>(*lval));
                IntegerValue& irval(static_cast<IntegerValue&>(*rval));
                IntegerValue::ValueType res = 0;
                switch (_operator) {
                    case ADD: res = ilval.getValue() + irval.getValue(); break;
                    case SUB: res = ilval.getValue() - irval.getValue(); break;
                    case MUL: res = ilval.getValue() * irval.getValue(); break;
                    case DIV:
                        if (irval.getValue() != 0) {
                            res = ilval.getValue() / irval.getValue();
                        } else {
                            throw vespalib::IllegalArgumentException("Division by zero");
                        }
                        break;
                    case MOD: assert(0);
                }
                return std::make_unique<IntegerValue>(res, false);
            }
            NumberValue* nlval(dynamic_cast<NumberValue*>(lval.get()));
            NumberValue* nrval(dynamic_cast<NumberValue*>(rval.get()));
            if (nlval != 0 && nrval != 0) {
                NumberValue::CommonValueType res = 0;
                switch (_operator) {
                    case ADD: res = nlval->getCommonValue()
                                  + nrval->getCommonValue(); break;
                    case SUB: res = nlval->getCommonValue()
                                  - nrval->getCommonValue(); break;
                    case MUL: res = nlval->getCommonValue()
                                  * nrval->getCommonValue(); break;
                    case DIV:
                        if (nrval->getCommonValue() != 0) {
                            res = nlval->getCommonValue()
                                  / nrval->getCommonValue();
                        } else {
                            throw vespalib::IllegalArgumentException("Division by zero");
                        }
                        break;
                    case MOD: assert(0);
                }
                return std::make_unique<FloatValue>(res);
            }
        }
        break;
        case MOD:
        {
            if (lval->getType() == Value::Integer &&
                rval->getType() == Value::Integer)
            {
                IntegerValue& ilval(static_cast<IntegerValue&>(*lval));
                IntegerValue& irval(static_cast<IntegerValue&>(*rval));
                if (irval.getValue() != 0) {
                    return std::make_unique<IntegerValue>(ilval.getValue() % irval.getValue(), false);
                } else {
                    throw vespalib::IllegalArgumentException("Division by zero");
                }
            }
        }
        break;
    }
    return std::make_unique<InvalidValue>();
}

std::unique_ptr<Value>
ArithmeticValueNode::traceValue(std::unique_ptr<Value> lval,
                                std::unique_ptr<Value> rval,
                                std::ostream& out) const
{
    switch (_operator) {
        case ADD:
        {
            if (lval->getType() == Value::String &&
                rval->getType() == Value::String)
            {
                StringValue& slval(static_cast<StringValue&>(*lval));
                StringValue& srval(static_cast<StringValue&>(*rval));
                auto result = std::make_unique<StringValue>(slval.getValue() + srval.getValue());
                out << "Appended strings '" << slval << "' + '" << srval
                    << "' -> '" << *result << "'.\n";
                return result;
            }
        }
        [[fallthrough]];
        case SUB:
        case MUL:
        case DIV:
        {
            if (lval->getType() == Value::Integer &&
                rval->getType() == Value::Integer)
            {
                IntegerValue& ilval(static_cast<IntegerValue&>(*lval));
                IntegerValue& irval(static_cast<IntegerValue&>(*rval));
                IntegerValue::ValueType res = 0;
                switch (_operator) {
                    case ADD: res = ilval.getValue() + irval.getValue(); break;
                    case SUB: res = ilval.getValue() - irval.getValue(); break;
                    case MUL: res = ilval.getValue() * irval.getValue(); break;
                    case DIV: res = ilval.getValue() / irval.getValue(); break;
                    case MOD: assert(0);
                }
                auto result = std::make_unique<IntegerValue>(res, false);
                out << "Performed integer operation " << ilval << " "
                    << getOperatorName() << " " << irval << " = " << *result
                    << "\n";
                return result;
            }
            NumberValue* nlval(dynamic_cast<NumberValue*>(lval.get()));
            NumberValue* nrval(dynamic_cast<NumberValue*>(lval.get()));
            if (nlval != 0 && nrval != 0) {
                NumberValue::CommonValueType res = 0;
                switch (_operator) {
                    case ADD: res = nlval->getCommonValue()
                                  + nrval->getCommonValue(); break;
                    case SUB: res = nlval->getCommonValue()
                                  - nrval->getCommonValue(); break;
                    case MUL: res = nlval->getCommonValue()
                                  * nrval->getCommonValue(); break;
                    case DIV: res = nlval->getCommonValue()
                                  / nrval->getCommonValue(); break;
                    case MOD: assert(0);
                }
                auto result = std::make_unique<FloatValue>(res);
                out << "Performed float operation " << nlval << " "
                    << getOperatorName() << " " << nrval << " = " << *result
                    << "\n";
                return result;
            }
        }
        break;
        case MOD:
        {
            if (lval->getType() == Value::Integer &&
                rval->getType() == Value::Integer)
            {
                IntegerValue& ilval(static_cast<IntegerValue&>(*lval));
                IntegerValue& irval(static_cast<IntegerValue&>(*rval));
                auto result = std::make_unique<IntegerValue>(ilval.getValue() % irval.getValue(), false);
                out << "Performed integer operation " << ilval << " "
                    << getOperatorName() << " " << irval << " = " << *result
                    << "\n";
                return result;
            }
        }
        break;
    }
    out << "Failed to do operation " << getOperatorName()
        << " on values of type " << lval->getType() << " and "
        << rval->getType() << ". Resolving invalid.\n";
    return std::make_unique<InvalidValue>();
}


void
ArithmeticValueNode::visit(Visitor &visitor) const
{
    visitor.visitArithmeticValueNode(*this);
}


void
ArithmeticValueNode::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    if (hadParentheses()) out << '(';
    _left->print(out, verbose, indent);
    switch (_operator) {
        case ADD: out << " + "; break;
        case SUB: out << " - "; break;
        case MUL: out << " * "; break;
        case DIV: out << " / "; break;
        case MOD: out << " % "; break;
    }
    _right->print(out, verbose, indent);
    if (hadParentheses()) out << ')';
}

FieldExprNode::~FieldExprNode() = default;

std::unique_ptr<FieldValueNode> FieldExprNode::convert_to_field_value() const {
    const auto& doctype = resolve_doctype();
    // FIXME deprecate manual post-parsing of field expressions in favor of
    // actually using the structural parser in the way nature intended.
    vespalib::string mangled_expression;
    build_mangled_expression(mangled_expression);
    return std::make_unique<FieldValueNode>(doctype, mangled_expression);
}

std::unique_ptr<FunctionValueNode> FieldExprNode::convert_to_function_call() const {
    // Right hand expr string contains function call, lhs contains field spec on which
    // the function is to be invoked.
    if ((_left_expr == nullptr) || (_left_expr->_left_expr == nullptr)) {
        throw vespalib::IllegalArgumentException(
                vespalib::make_string("Cannot call function '%s' directly on document type", _right_expr.c_str()));
    }
    auto lhs = _left_expr->convert_to_field_value();
    const auto& function_name = _right_expr;
    return std::make_unique<FunctionValueNode>(function_name, std::move(lhs));
}

void FieldExprNode::build_mangled_expression(vespalib::string& dest) const {
    // Leftmost node is doctype, which should not be emitted as part of mangled expression.
    if (_left_expr && _left_expr->_left_expr) {
        _left_expr->build_mangled_expression(dest);
        dest.push_back('.');
    }
    dest.append(_right_expr);
}

const vespalib::string& FieldExprNode::resolve_doctype() const {
    const auto* leftmost = this;
    while (leftmost->_left_expr) {
        leftmost = leftmost->_left_expr.get();
    }
    return leftmost->_right_expr;
}

}
