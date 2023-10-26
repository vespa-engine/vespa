// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "parametervalidator.h"
#include "fieldtype.h"
#include "fieldinfo.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <boost/lexical_cast.hpp>

using vespalib::make_string;

namespace search::fef {

using CollectionType = FieldInfo::CollectionType;

namespace {

bool checkCollectionType(ParameterCollection::Enum accept, CollectionType actual) {
    switch (accept) {
    case ParameterCollection::NONE:        return false;
    case ParameterCollection::SINGLE:      return (actual == CollectionType::SINGLE);
    case ParameterCollection::ARRAY:       return (actual == CollectionType::ARRAY);
    case ParameterCollection::WEIGHTEDSET: return (actual == CollectionType::WEIGHTEDSET);
    case ParameterCollection::ANY:         return true;
    }
    return false;
}

bool checkDataType(ParameterDataTypeSet accept, search::index::schema::DataType actual) {
    return accept.allowedType(actual);
}

class ValidateException
{
public:
    ValidateException(const vespalib::string & message) : _message(message) { }
    const vespalib::string & getMessage() const { return _message; }
private:
    vespalib::string _message;
};

} // namespace search::fef::<unnamed>

ParameterValidator::Result::Result(size_t tag) :
    _params(),
    _tag(tag),
    _errorStr(),
    _valid(true)
{
}

ParameterValidator::Result::Result(const Result &) = default;
ParameterValidator::Result & ParameterValidator::Result::operator=(const Result &) = default;

ParameterValidator::Result::~Result() = default;

void
ParameterValidator::validateField(ParameterType::Enum type,
                                  ParameterDataTypeSet dataTypeSet,
                                  ParameterCollection::Enum collection,
                                  size_t i, Result & result)
{
    const FieldInfo * field = _indexEnv.getFieldByName(_params[i]);
    if (field == NULL) {
        throw ValidateException(make_string("Param[%zu]: Field '%s' was not found in the index environment",
                                            i, _params[i].c_str()));
    }
    if (type == ParameterType::INDEX_FIELD) {
        if (field->type() != FieldType::INDEX) {
            throw ValidateException(make_string("Param[%zu]: Expected field '%s' to be an index field, but it was not",
                                                i, _params[i].c_str()));
        }
    } else if (type == ParameterType::ATTRIBUTE_FIELD) {
        if (field->type() != FieldType::ATTRIBUTE) {
            throw ValidateException(make_string("Param[%zu]: Expected field '%s' to be an attribute field, but it was not",
                                                i, _params[i].c_str()));
        }
    } else if (type == ParameterType::ATTRIBUTE) {
        if (!field->hasAttribute()) {
            throw ValidateException(make_string("Param[%zu]: Expected field '%s' to support attribute lookup, but it does not",
                            i, _params[i].c_str()));
        }
    }
    if (!checkDataType(dataTypeSet, field->get_data_type())) {
        throw ValidateException(make_string("Param[%zu]: field '%s' has inappropriate data type",
                                            i, _params[i].c_str()));
    }
    if (!checkCollectionType(collection, field->collection())) {
        throw ValidateException(make_string("Param[%zu]: field '%s' has inappropriate collection type",
                                            i, _params[i].c_str()));
    }
    result.addParameter(Parameter(type, _params[i]).setField(field));
}

void
ParameterValidator::validateNumber(ParameterType::Enum type, size_t i, Result & result)
{
    try {
        double doubleVal = boost::lexical_cast<double>(_params[i]);
        int64_t intVal = static_cast<int64_t>(doubleVal);
        result.addParameter(Parameter(type, _params[i]).setInteger(intVal).setDouble(doubleVal));
    } catch (const boost::bad_lexical_cast &) {
        throw ValidateException(make_string("Param[%zu]: Could not convert '%s' to a number", i, _params[i].c_str()));
    }
}

ParameterValidator::Result
ParameterValidator::validate(const ParameterDescriptions::Description & desc)
{
    Result result(desc.getTag());
    if (desc.hasRepeat()) {
        size_t minParams = desc.getParams().size() - desc.getRepeat(); // the repeat params can occur 0-n times
        if (minParams > _params.size() ||
            ((_params.size() - desc.getParams().size()) % desc.getRepeat() != 0))
        {
            throw ValidateException(make_string("Expected %zd+%zdx parameter(s), but got %zd",
                                                minParams, desc.getRepeat(), _params.size()));
        }
    } else if (desc.getParams().size() != _params.size()) {
        throw ValidateException(make_string("Expected %zd parameter(s), but got %zd", desc.getParams().size(), _params.size()));
    }
    for (size_t i = 0; i < _params.size(); ++i) {
        ParamDescItem param = desc.getParam(i);
        ParameterType::Enum type = param.type;
        switch (type) {
        case ParameterType::FIELD:
        case ParameterType::INDEX_FIELD:
        case ParameterType::ATTRIBUTE_FIELD:
        case ParameterType::ATTRIBUTE:
            validateField(type, param.dataTypeSet, param.collection, i, result);
            break;
        case ParameterType::NUMBER:
            validateNumber(type, i, result);
            break;
        case ParameterType::FEATURE:
        case ParameterType::STRING:
            result.addParameter(Parameter(type, _params[i]));
            break;
        default:
            break;
        }
    }
    return result;
}

ParameterValidator::ParameterValidator(const IIndexEnvironment & indexEnv,
                                       const StringVector & params,
                                       const ParameterDescriptions & descs) :
    _indexEnv(indexEnv),
    _params(params),
    _descs(descs)
{
}

ParameterValidator::Result
ParameterValidator::validate()
{
    Result invalid;
    for (size_t i = 0; i < _descs.getDescriptions().size(); ++i) {
        try {
            return validate(_descs.getDescriptions()[i]);
        } catch (const ValidateException & e) {
            if (invalid.valid()) {
                Result tmp(_descs.getDescriptions()[i].getTag());
                tmp.setError(e.getMessage());
                invalid = tmp;
            }
        }
    }
    return invalid;
}

}
