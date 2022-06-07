// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "integerresultnode.h"
#include "floatresultnode.h"
#include "stringresultnode.h"
#include "rawresultnode.h"
#include "enumresultnode.h"
#include "constantnode.h"
#include "relevancenode.h"
#include "addfunctionnode.h"
#include "dividefunctionnode.h"
#include "multiplyfunctionnode.h"
#include "modulofunctionnode.h"
#include "minfunctionnode.h"
#include "maxfunctionnode.h"
#include "andfunctionnode.h"
#include "orfunctionnode.h"
#include "xorfunctionnode.h"
#include "negatefunctionnode.h"
#include "sortfunctionnode.h"
#include "reversefunctionnode.h"
#include "strlenfunctionnode.h"
#include "numelemfunctionnode.h"
#include "tostringfunctionnode.h"
#include "torawfunctionnode.h"
#include "catfunctionnode.h"
#include "tointfunctionnode.h"
#include "tofloatfunctionnode.h"
#include "strcatfunctionnode.h"
#include "xorbitfunctionnode.h"
#include "md5bitfunctionnode.h"
#include "binaryfunctionnode.h"
#include "nullresultnode.h"
#include "positiveinfinityresultnode.h"
#include "resultvector.h"
#include "catserializer.h"
#include "strcatserializer.h"
#include "normalizesubjectfunctionnode.h"
#include "arrayoperationnode.h"
#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/deserializer.hpp>
#include <vespa/vespalib/stllike/asciistream.h>

#include <map>
#include <vespa/vespalib/util/md5.h>

namespace search::expression {

using vespalib::asciistream;
using vespalib::nbostream;
using vespalib::Serializer;
using vespalib::Deserializer;
using vespalib::make_string;
using vespalib::Identifiable;
using vespalib::BufferRef;
using vespalib::ConstBufferRef;

IMPLEMENT_ABSTRACT_EXPRESSIONNODE(ExpressionNode,       Identifiable);
IMPLEMENT_ABSTRACT_EXPRESSIONNODE(FunctionNode,         ExpressionNode);
IMPLEMENT_ABSTRACT_EXPRESSIONNODE(MultiArgFunctionNode, FunctionNode);
IMPLEMENT_ABSTRACT_EXPRESSIONNODE(UnaryFunctionNode,    MultiArgFunctionNode);
IMPLEMENT_ABSTRACT_EXPRESSIONNODE(BinaryFunctionNode,   MultiArgFunctionNode);
IMPLEMENT_ABSTRACT_EXPRESSIONNODE(BitFunctionNode,      NumericFunctionNode);
IMPLEMENT_ABSTRACT_EXPRESSIONNODE(UnaryBitFunctionNode, UnaryFunctionNode);

IMPLEMENT_EXPRESSIONNODE(ConstantNode,         ExpressionNode);
IMPLEMENT_EXPRESSIONNODE(AddFunctionNode,      NumericFunctionNode);
IMPLEMENT_EXPRESSIONNODE(DivideFunctionNode,   NumericFunctionNode);
IMPLEMENT_EXPRESSIONNODE(MultiplyFunctionNode, NumericFunctionNode);
IMPLEMENT_EXPRESSIONNODE(ModuloFunctionNode,   NumericFunctionNode);
IMPLEMENT_EXPRESSIONNODE(MinFunctionNode,      NumericFunctionNode);
IMPLEMENT_EXPRESSIONNODE(MaxFunctionNode,      NumericFunctionNode);
IMPLEMENT_EXPRESSIONNODE(XorFunctionNode,      BitFunctionNode);
IMPLEMENT_EXPRESSIONNODE(AndFunctionNode,      BitFunctionNode);
IMPLEMENT_EXPRESSIONNODE(OrFunctionNode,       BitFunctionNode);
IMPLEMENT_EXPRESSIONNODE(CatFunctionNode,      MultiArgFunctionNode);
IMPLEMENT_EXPRESSIONNODE(StrCatFunctionNode,   MultiArgFunctionNode);
IMPLEMENT_EXPRESSIONNODE(NegateFunctionNode,   UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(SortFunctionNode,     UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(ReverseFunctionNode,  UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(StrLenFunctionNode,   UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(NormalizeSubjectFunctionNode,   UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(ToIntFunctionNode,    UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(ToFloatFunctionNode,  UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(NumElemFunctionNode,  UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(ToStringFunctionNode,   UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(ToRawFunctionNode,    UnaryFunctionNode);
IMPLEMENT_EXPRESSIONNODE(XorBitFunctionNode,   UnaryBitFunctionNode);
IMPLEMENT_EXPRESSIONNODE(MD5BitFunctionNode,   UnaryBitFunctionNode);

void ExpressionNode::onArgument(const ResultNode & arg, ResultNode & result) const
{
    (void) arg;
    (void) result;
    throw std::runtime_error(make_string("Class %s does not implement onArgument(const ResultNode & arg, ResultNode & result). Probably an indication that it tries to take a multivalued argument, which it can not.", getClass().name()));
}

void ExpressionNode::executeIterative(const ResultNode & arg, ResultNode & result) const
{
    onArgument(arg, result);
}

void ExpressionNode::wireAttributes(const search::attribute::IAttributeContext &)
{
}


class ArithmeticTypeConversion
{
public:
    ArithmeticTypeConversion() :
      _typeConversion()
    {
        _typeConversion[IntegerResultNode::classId][IntegerResultNode::classId] = Int64ResultNode::classId;
        _typeConversion[IntegerResultNode::classId][FloatResultNode::classId]   = FloatResultNode::classId;
        _typeConversion[IntegerResultNode::classId][StringResultNode::classId]  = Int64ResultNode::classId;
        _typeConversion[IntegerResultNode::classId][RawResultNode::classId]     = Int64ResultNode::classId;
        _typeConversion[FloatResultNode::classId][IntegerResultNode::classId]   = FloatResultNode::classId;
        _typeConversion[FloatResultNode::classId][FloatResultNode::classId]     = FloatResultNode::classId;
        _typeConversion[FloatResultNode::classId][StringResultNode::classId]    = FloatResultNode::classId;
        _typeConversion[FloatResultNode::classId][RawResultNode::classId]       = FloatResultNode::classId;
        _typeConversion[StringResultNode::classId][IntegerResultNode::classId]  = Int64ResultNode::classId;
        _typeConversion[StringResultNode::classId][FloatResultNode::classId]    = FloatResultNode::classId;
        _typeConversion[StringResultNode::classId][StringResultNode::classId]   = StringResultNode::classId;
        _typeConversion[StringResultNode::classId][RawResultNode::classId]      = StringResultNode::classId;
        _typeConversion[RawResultNode::classId][IntegerResultNode::classId]     = Int64ResultNode::classId;
        _typeConversion[RawResultNode::classId][FloatResultNode::classId]       = FloatResultNode::classId;
        _typeConversion[RawResultNode::classId][StringResultNode::classId]      = StringResultNode::classId;
        _typeConversion[RawResultNode::classId][RawResultNode::classId]         = RawResultNode::classId;
    }
    ResultNode::UP getType(const ResultNode & arg1, const ResultNode & arg2);
    static ResultNode::UP getType(const ResultNode & arg);
private:
    static size_t getDimension(const ResultNode & r) {
        if (r.getClass().inherits(ResultNodeVector::classId)) {
            return 1 + getDimension(* r.createBaseType());
        } else {
            return 0;
        }
    }
    static size_t getBaseType(const ResultNode & r);
    static size_t getBaseType2(const ResultNode & r);
    size_t getType(size_t arg1, size_t arg2) const {
        return _typeConversion.find(arg1)->second.find(arg2)->second;
    }
    std::map<size_t, std::map<size_t, size_t> > _typeConversion;
};

ResultNode::UP ArithmeticTypeConversion::getType(const ResultNode & arg1, const ResultNode & arg2)
{
    size_t baseTypeId = getType(getBaseType2(arg1), getBaseType2(arg2));
    size_t dimension = std::max(getDimension(arg1), getDimension(arg2));
    ResultNode::UP result;
    if (dimension == 0) {
        return ResultNode::UP(static_cast<ResultNode *>(Identifiable::classFromId(baseTypeId)->create()));
    } else if (dimension == 1) {
        if (baseTypeId == Int64ResultNode::classId) {
            result.reset(new IntegerResultNodeVector());
        } else if (baseTypeId == FloatResultNode::classId) {
            result.reset(new FloatResultNodeVector());
        } else {
            throw std::runtime_error("We can not handle anything but numbers.");
        }
    } else {
        throw std::runtime_error("We are not able to handle multidimensional arrays");
    }
    return result;
}

ResultNode::UP ArithmeticTypeConversion::getType(const ResultNode & arg)
{
    size_t baseTypeId = getBaseType(arg);
    return ResultNode::UP(static_cast<ResultNode *>(Identifiable::classFromId(baseTypeId)->create()));
}

size_t ArithmeticTypeConversion::getBaseType(const ResultNode & r)
{
    if (r.getClass().inherits(ResultNodeVector::classId)) {
        return getBaseType(* r.createBaseType());
    } else {
        return r.getClass().id();
    }
}

size_t ArithmeticTypeConversion::getBaseType2(const ResultNode & r)
{
    if (r.getClass().inherits(ResultNodeVector::classId)) {
        return getBaseType2(* r.createBaseType());
    } else if (r.getClass().inherits(IntegerResultNode::classId)) {
        return IntegerResultNode::classId;
    } else {
        return getBaseType(r);
    }
}

namespace {
    ArithmeticTypeConversion _ArithmeticTypeConversion;
}


void MultiArgFunctionNode::onPrepare(bool preserveAccurateTypes)
{
    for(size_t i(0), m(_args.size()); i < m; i++) {
        _args[i]->prepare(preserveAccurateTypes);
    }
    prepareResult();
}

void MultiArgFunctionNode::onPrepareResult()
{
    if (_args.size() == 1) {
        setResultType(ArithmeticTypeConversion::getType(*_args[0]->getResult()));
    } else if (_args.size() > 1) {
        setResultType(std::unique_ptr<ResultNode>(static_cast<ResultNode *>(_args[0]->getResult()->clone())));
        for(size_t i(1), m(_args.size()); i < m; i++) {
            if (_args[i]->getResult() != nullptr) {
                setResultType(_ArithmeticTypeConversion.getType(*getResult(), *_args[i]->getResult()));
            }
        }
    }
}

bool MultiArgFunctionNode::onExecute() const
{
    for(size_t i(0), m(_args.size()); i < m; i++) {
        _args[i]->execute();
    }
    return calculate(_args, updateResult());
}

bool MultiArgFunctionNode::onCalculate(const ExpressionNodeVector & args, ResultNode & result) const
{
    result.set(*args[0]->getResult());
    for (size_t i(1), m(args.size()); i < m; i++) {
        executeIterative(*args[i]->getResult(), result);
    }
    return true;
}

void BitFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new Int64ResultNode(0)));
}

void StrCatFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new StringResultNode()));
}

void CatFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new RawResultNode()));
}

void CatFunctionNode::onPrepare(bool preserveAccurateTypes)
{
    (void) preserveAccurateTypes;
    MultiArgFunctionNode::onPrepare(true);
}

void BitFunctionNode::onArgument(const ResultNode & arg, ResultNode & result) const
{
    onArgument(arg, static_cast<Int64ResultNode &>(result));
}

void AddFunctionNode::onArgument(const ResultNode & arg, ResultNode & result)        const { static_cast<NumericResultNode &>(result).add(arg); }
void DivideFunctionNode::onArgument(const ResultNode & arg, ResultNode & result)     const { static_cast<NumericResultNode &>(result).divide(arg); }
void MultiplyFunctionNode::onArgument(const ResultNode & arg, ResultNode & result)   const { static_cast<NumericResultNode &>(result).multiply(arg); }
void ModuloFunctionNode::onArgument(const ResultNode & arg, ResultNode & result)     const { static_cast<NumericResultNode &>(result).modulo(arg); }
void MinFunctionNode::onArgument(const ResultNode & arg, ResultNode & result)        const { static_cast<NumericResultNode &>(result).min(arg); }
void MaxFunctionNode::onArgument(const ResultNode & arg, ResultNode & result)        const { static_cast<NumericResultNode &>(result).max(arg); }
void AndFunctionNode::onArgument(const ResultNode & arg, Int64ResultNode & result) const { result.andOp(arg); }
void OrFunctionNode::onArgument(const ResultNode & arg, Int64ResultNode & result)  const { result.orOp(arg); }
void XorFunctionNode::onArgument(const ResultNode & arg, Int64ResultNode & result) const { result.xorOp(arg); }

ResultNode::CP MaxFunctionNode::getInitialValue() const
{
    ResultNode::CP initial;
    const ResultNode & arg(*getArg(0).getResult());
    if (arg.inherits(FloatResultNodeVector::classId)) {
        initial.reset(new FloatResultNode(std::numeric_limits<double>::min()));
    } else if (arg.inherits(IntegerResultNodeVector::classId)) {
        initial.reset(new Int64ResultNode(std::numeric_limits<int64_t>::min()));
    } else {
        throw std::runtime_error(vespalib::string("Can not choose an initial value for class ") + arg.getClass().name());
    }
    return initial;
}

ResultNode::CP MinFunctionNode::getInitialValue() const
{
    ResultNode::CP initial;
    const ResultNode & arg(*getArg(0).getResult());
    if (arg.inherits(FloatResultNodeVector::classId)) {
        initial.reset(new FloatResultNode(std::numeric_limits<double>::max()));
    } else if (arg.inherits(IntegerResultNodeVector::classId)) {
        initial.reset(new Int64ResultNode(std::numeric_limits<int64_t>::max()));
    } else {
        throw std::runtime_error(vespalib::string("Can not choose an initial value for class ") + arg.getClass().name());
    }
    return initial;
}

ResultNode & ModuloFunctionNode::flatten(const ResultNodeVector &, ResultNode &) const
{
   throw std::runtime_error("ModuloFunctionNode::flatten() const not implemented since it shall never be used.");
}

ResultNode & DivideFunctionNode::flatten(const ResultNodeVector &, ResultNode &) const
{
   throw std::runtime_error("DivideFunctionNode::flatten() const not implemented since it shall never be used.");
}

ResultNode::CP ModuloFunctionNode::getInitialValue() const
{
   throw std::runtime_error("ModuloFunctionNode::getInitialValue() const not implemented since it shall never be used.");
}

ResultNode::CP DivideFunctionNode::getInitialValue() const
{
   throw std::runtime_error("DivideFunctionNode::getInitialValue() const not implemented since it shall never be used.");
}

void UnaryBitFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new RawResultNode()));
}

void UnaryBitFunctionNode::onPrepare(bool preserveAccurateTypes)
{
    (void) preserveAccurateTypes;
    UnaryFunctionNode::onPrepare(true);
}

void UnaryFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(getArg().getResult()->clone()));
}

void ToStringFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new StringResultNode()));
}

bool ToStringFunctionNode::onExecute() const
{
    getArg().execute();
    updateResult().set(*getArg().getResult());
    return true;
}

void ToRawFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new RawResultNode()));
}

bool ToRawFunctionNode::onExecute() const
{
    getArg().execute();
    updateResult().set(*getArg().getResult());
    return true;
}

void ToIntFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new Int64ResultNode()));
}

bool ToIntFunctionNode::onExecute() const
{
    getArg().execute();
    updateResult().set(*getArg().getResult());
    return true;
}

void ToFloatFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new FloatResultNode()));
}

bool ToFloatFunctionNode::onExecute() const
{
    getArg().execute();
    updateResult().set(*getArg().getResult());
    return true;
}

void StrLenFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new Int64ResultNode()));
}

bool StrLenFunctionNode::onExecute() const
{
    getArg().execute();
    char buf[32];
    static_cast<Int64ResultNode &> (updateResult()).set(getArg().getResult()->getString(BufferRef(buf, sizeof(buf))).size());
    return true;
}

void NormalizeSubjectFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new StringResultNode()));
}

bool NormalizeSubjectFunctionNode::onExecute() const
{
    getArg().execute();
    char buf[32];
    ConstBufferRef tmp(getArg().getResult()->getString(BufferRef(buf, sizeof(buf))));

    int pos = 0;
    if (tmp.size() >= 4) {
        if ((tmp[0] == 'R') && ((tmp[1] | 0x20) == 'e') && (tmp[2] == ':') && (tmp[3] == ' ')) {
            pos = 4;
        } else if ((tmp[0] == 'F') && ((tmp[1] | 0x20) == 'w')) {
            if ((tmp[2] == ':') && (tmp[3] == ' ')) {
                pos = 4;
            } else if (((tmp[2] | 0x20) == 'd') && (tmp[3] == ':') && (tmp[4] == ' ')) {
                pos = 5;
            }
        }
    }
    static_cast<StringResultNode &> (updateResult()).set(vespalib::stringref(tmp.c_str() + pos, tmp.size() - pos));
    return true;
}

void NumElemFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new Int64ResultNode(1)));
}

bool NumElemFunctionNode::onExecute() const
{
    getArg().execute();
    if (getArg().getResult()->inherits(ResultNodeVector::classId)) {
        static_cast<Int64ResultNode &> (updateResult()).set(static_cast<const ResultNodeVector &>(*getArg().getResult()).size());
    }
    return true;
}

bool NegateFunctionNode::onExecute() const
{
    getArg().execute();
    updateResult().assign(*getArg().getResult());
    updateResult().negate();
    return true;
}

bool SortFunctionNode::onExecute() const
{
    getArg().execute();
    updateResult().assign(*getArg().getResult());
    updateResult().sort();
    return true;
}

bool ReverseFunctionNode::onExecute() const
{
    getArg().execute();
    updateResult().assign(*getArg().getResult());
    updateResult().reverse();
    return true;
}

bool StrCatFunctionNode::onExecute() const
{
    asciistream os;
    StrCatSerializer nos(os);
    for(size_t i(0), m(getNumArgs()); i < m; i++) {
        getArg(i).execute();
        getArg(i).getResult()->serialize(nos);
    }
    static_cast<StringResultNode &>(updateResult()).set(os.str());
    return true;
}

bool CatFunctionNode::onExecute() const
{
    nbostream os;
    CatSerializer nos(os);
    for(size_t i(0), m(getNumArgs()); i < m; i++) {
        getArg(i).execute();
        getArg(i).getResult()->serialize(nos);
    }
    static_cast<RawResultNode &>(updateResult()).setBuffer(os.data(), os.size());
    return true;
}

XorBitFunctionNode::XorBitFunctionNode() {}
XorBitFunctionNode::~XorBitFunctionNode() {}

XorBitFunctionNode::XorBitFunctionNode(ExpressionNode::UP arg, unsigned numBits) :
    UnaryBitFunctionNode(std::move(arg), numBits),
    _tmpXor(getNumBytes(), 0)
{}

bool UnaryBitFunctionNode::onExecute() const
{
    _tmpOs.clear();
    getArg().execute();
    CatSerializer os(_tmpOs);
    getArg().getResult()->serialize(os);
    return internalExecute(_tmpOs);
}

void XorBitFunctionNode::onPrepareResult()
{
    UnaryBitFunctionNode::onPrepareResult();
    _tmpXor.resize(getNumBytes());
}

bool XorBitFunctionNode::internalExecute(const nbostream & os) const
{
    const size_t numBytes(_tmpXor.size());
    memset(&_tmpXor[0], 0, numBytes);
    const char * s(os.data());
    for (size_t i(0), m(os.size()/numBytes); i < m; i++) {
        for (size_t j(0), k(numBytes); j < k; j++) {
            _tmpXor[j] ^= s[j + k*i];
        }
    }
    for (size_t i((os.size()/numBytes)*numBytes); i < os.size(); i++) {
        _tmpXor[i%numBytes] = os.data()[i];
    }
    static_cast<RawResultNode &>(updateResult()).setBuffer(&_tmpXor[0], numBytes);
    return true;
}

bool MD5BitFunctionNode::internalExecute(const nbostream & os) const
{
    const unsigned int MD5_DIGEST_LENGTH = 16;
    unsigned char md5ScratchPad[MD5_DIGEST_LENGTH];
    fastc_md5sum(os.data(), os.size(), md5ScratchPad);
    static_cast<RawResultNode &>(updateResult()).setBuffer(md5ScratchPad, std::min(sizeof(md5ScratchPad), getNumBytes()));
    return true;
}

Serializer & FunctionNode::onSerialize(Serializer & os) const
{
    return os << _tmpResult;
}
Deserializer & FunctionNode::onDeserialize(Deserializer & is)
{
    return is >> _tmpResult;
}

void
ConstantNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "Value", _result);
}

Serializer & ConstantNode::onSerialize(Serializer & os) const
{
    return os << _result;
}
Deserializer & ConstantNode::onDeserialize(Deserializer & is)
{
    return is >> _result;
}



void
FunctionNode::visitMembers(vespalib::ObjectVisitor & visitor) const
{
    visit(visitor, "tmpResult", _tmpResult);
}

void FunctionNode::selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation)
{
    if (_tmpResult.get()) {
        _tmpResult->select(predicate, operation);
    }
}

void MultiArgFunctionNode::selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation)
{
    FunctionNode::selectMembers(predicate, operation);
    for(size_t i(0), m(_args.size()); i < m; i++) {
        _args[i]->select(predicate, operation);
    }
}

Serializer & MultiArgFunctionNode::onSerialize(Serializer & os) const
{
    FunctionNode::onSerialize(os);
    os << _args;
    return os;
}
Deserializer & MultiArgFunctionNode::onDeserialize(Deserializer & is)
{
    FunctionNode::onDeserialize(is);
    return is >> _args;
}

MultiArgFunctionNode::MultiArgFunctionNode() : FunctionNode() { }
MultiArgFunctionNode::MultiArgFunctionNode(const MultiArgFunctionNode &) = default;
MultiArgFunctionNode & MultiArgFunctionNode::operator = (const MultiArgFunctionNode &) = default;

MultiArgFunctionNode::~MultiArgFunctionNode() {}

void
MultiArgFunctionNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    FunctionNode::visitMembers(visitor);
    visit(visitor, "args", _args);
}

Serializer & UnaryBitFunctionNode::onSerialize(Serializer & os) const
{
    UnaryFunctionNode::onSerialize(os);
    return os << _numBits;
}
Deserializer & UnaryBitFunctionNode::onDeserialize(Deserializer & is)
{
    UnaryFunctionNode::onDeserialize(is);
    return is >> _numBits;
}

void
UnaryBitFunctionNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    UnaryFunctionNode::visitMembers(visitor);
    visit(visitor, "numBits", _numBits);
}

}
