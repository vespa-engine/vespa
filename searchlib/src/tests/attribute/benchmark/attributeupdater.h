// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/util/randomgenerator.h>
#include <vespa/searchlib/util/runnable.h>
#include <vespa/searchlib/attribute/attribute.h>

#define VALIDATOR_STR(str) #str
#define VALIDATOR_ASSERT(rc) reportAssert(rc, __FILE__, __LINE__, VALIDATOR_STR(rc))
#define VALIDATOR_ASSERT_EQUAL(a, b) reportAssertEqual(__FILE__, __LINE__, VALIDATOR_STR(a), VALIDATOR_STR(b), a, b)

namespace search {

class AttributeValidator
{
private:
    uint32_t _totalCnt;

public:
    AttributeValidator() : _totalCnt(0) {}
    uint32_t getTotalCnt() const { return _totalCnt; }
    bool reportAssert(bool rc, const vespalib::string & file, uint32_t line, const vespalib::string & str) {
        _totalCnt++;
        if (!rc) {
            std::cout << "Assert " << _totalCnt << " failed: \"" << str << "\" ("
                << file << ":" << line << ")" << std::endl;
            LOG_ABORT("should not be reached");
        }
        return true;
    }
    template <class A, class B>
    bool reportAssertEqual(const vespalib::string & file, uint32_t line,
                           const vespalib::string & aStr, const vespalib::string & bStr,
                           const A & a, const B & b) {
        _totalCnt++;
        if (!(a == b)) {
            std::cout << "Assert equal failed: " << std::endl;
            std::cout << aStr << ": " << a << std::endl;
            std::cout << bStr << ": " << b << std::endl;
            std::cout << "(" << file << ":" << line << ")" << std::endl;
            LOG_ABORT("should not be reached");
        }
        return true;
    }
};

class AttributeUpdaterStatus
{
public:
    double _totalUpdateTime;
    uint64_t _numDocumentUpdates;
    uint64_t _numValueUpdates;

    AttributeUpdaterStatus() :
        _totalUpdateTime(0), _numDocumentUpdates(0), _numValueUpdates(0) {}
    void reset() {
        _totalUpdateTime = 0;
        _numDocumentUpdates = 0;
        _numValueUpdates = 0;
    }
    void printXML() const {
        std::cout << "<total-update-time>" << _totalUpdateTime << "</total-update-time>" << std::endl;
        std::cout << "<documents-updated>" << _numDocumentUpdates << "</documents-updated>" << std::endl;
        std::cout << "<document-update-throughput>" << documentUpdateThroughput() << "</document-update-throughput>" << std::endl;
        std::cout << "<avg-document-update-time>" << avgDocumentUpdateTime() << "</avg-document-update-time>" << std::endl;
        std::cout << "<values-updated>" << _numValueUpdates << "</values-updated>" << std::endl;
        std::cout << "<value-update-throughput>" << valueUpdateThroughput() << "</value-update-throughput>" << std::endl;
        std::cout << "<avg-value-update-time>" << avgValueUpdateTime() << "</avg-value-update-time>" << std::endl;
    }
    double documentUpdateThroughput() const {
        return _numDocumentUpdates * 1000 / _totalUpdateTime;
    }
    double avgDocumentUpdateTime() const {
        return _totalUpdateTime / _numDocumentUpdates;
    }
    double valueUpdateThroughput() const {
        return _numValueUpdates * 1000 / _totalUpdateTime;
    }
    double avgValueUpdateTime() const {
        return _totalUpdateTime / _numValueUpdates;
    }
};

// AttributeVectorInstance, AttributeVectorType, AttributeVectorBufferType
template <typename Vector, typename T, typename BT>
class AttributeUpdater
{
protected:
    typedef AttributeVector::SP AttributePtr;
    typedef std::map<uint32_t, std::vector<T> > AttributeCommit;

    const AttributePtr & _attrPtr;
    Vector & _attrVec;
    const std::vector<T> & _values;
    std::vector<T> _buffer;
    std::vector<BT> _getBuffer;
    RandomGenerator & _rndGen;
    AttributeCommit _expected;
    FastOS_Time _timer;
    AttributeUpdaterStatus _status;
    AttributeValidator _validator;

    // config
    bool _validate;
    uint32_t _commitFreq;
    uint32_t _minValueCount;
    uint32_t _maxValueCount;

    uint32_t getRandomCount() {
        return _rndGen.rand(_minValueCount, _maxValueCount);
    }
    uint32_t getRandomDoc() {
        return _rndGen.rand(0, _attrPtr->getNumDocs() - 1);
    }
    const T & getRandomValue() {
        return _values[_rndGen.rand(0, _values.size() - 1)];
    }
    void updateValues(uint32_t doc);
    void commit();

public:
    AttributeUpdater(const AttributePtr & attrPtr, const std::vector<T> & values,
                     RandomGenerator & rndGen, bool validate, uint32_t commitFreq,
                     uint32_t minValueCount, uint32_t maxValueCount);
    ~AttributeUpdater();
    void resetStatus() {
        _status.reset();
    }
    const AttributeUpdaterStatus & getStatus() const {
        return _status;
    }
    const AttributeValidator & getValidator() const {
        return _validator;
    }
    void populate();
    void update(uint32_t numUpdates);
};

template <typename Vector, typename T, typename BT>
AttributeUpdater<Vector, T, BT>::AttributeUpdater(const AttributePtr & attrPtr, const std::vector<T> & values,
                 RandomGenerator & rndGen, bool validate, uint32_t commitFreq,
                 uint32_t minValueCount, uint32_t maxValueCount)
    :_attrPtr(attrPtr), _attrVec(*(static_cast<Vector *>(attrPtr.get()))), _values(values), _buffer(),
     _getBuffer(), _rndGen(rndGen), _expected(), _timer(), _status(), _validator(), _validate(validate),
     _commitFreq(commitFreq), _minValueCount(minValueCount), _maxValueCount(maxValueCount)
{}

template <typename Vector, typename T, typename BT>
AttributeUpdater<Vector, T, BT>::~AttributeUpdater() {}

template <typename Vector, typename T, typename BT>
class AttributeUpdaterThread : public AttributeUpdater<Vector, T, BT>, public Runnable
{
private:
    typedef AttributeVector::SP AttributePtr;

public:
    AttributeUpdaterThread(const AttributePtr & attrPtr, const std::vector<T> & values,
                           RandomGenerator & rndGen, bool validate, uint32_t commitFreq,
                           uint32_t minValueCount, uint32_t maxValueCount);
    ~AttributeUpdaterThread();

    virtual void doRun() override;
};

template <typename Vector, typename T, typename BT>
AttributeUpdaterThread<Vector, T, BT>::AttributeUpdaterThread(const AttributePtr & attrPtr, const std::vector<T> & values,
                                               RandomGenerator & rndGen, bool validate, uint32_t commitFreq,
                                               uint32_t minValueCount, uint32_t maxValueCount)
    : AttributeUpdater<Vector, T, BT>(attrPtr, values, rndGen, validate, commitFreq, minValueCount, maxValueCount),
      Runnable()
{}
template <typename Vector, typename T, typename BT>
AttributeUpdaterThread<Vector, T, BT>::~AttributeUpdaterThread() { }


template <typename Vector, typename T, typename BT>
void
AttributeUpdater<Vector, T, BT>::updateValues(uint32_t doc)
{
    uint32_t valueCount = getRandomCount();

    if (_validate) {
        _buffer.clear();
        if (_attrPtr->hasMultiValue()) {
            _attrPtr->clearDoc(doc);
            for (uint32_t j = 0; j < valueCount; ++j) {
                T value = getRandomValue();
                if (_attrPtr->hasWeightedSetType()) {
                    bool exists = false;
                    for (typename std::vector<T>::iterator iter = _buffer.begin(); iter != _buffer.end(); ++iter) {
                        if (iter->getValue() == value.getValue()) {
                            exists = true;
                            iter->setWeight(value.getWeight());
                            break;
                        }
                    }
                    if (!exists) {
                        _buffer.push_back(value);
                    }
                } else {
                    _buffer.push_back(value);
                }
                _attrVec.append(doc, value.getValue(), value.getWeight());
            }
        } else {
            _buffer.push_back(getRandomValue());
            _attrVec.update(doc, _buffer.back().getValue());
        }
        _expected[doc] = _buffer;

    } else {
        if (_attrPtr->hasMultiValue()) {
            _attrPtr->clearDoc(doc);
            for (uint32_t j = 0; j < valueCount; ++j) {
                T value = getRandomValue();
                _attrVec.append(doc, value.getValue(), value.getWeight());
            }
        } else {
            _attrVec.update(doc, getRandomValue().getValue());
        }
    }

    _status._numDocumentUpdates++;
    _status._numValueUpdates += (_attrPtr->hasMultiValue() ? valueCount: 1);
}

template <typename Vector, typename T, typename BT>
void
AttributeUpdater<Vector, T, BT>::commit()
{
    AttributeGuard guard(this->_attrPtr);
    if (_validate) {
        _attrPtr->commit();
        _getBuffer.resize(_maxValueCount);
        for (typename AttributeCommit::iterator iter = _expected.begin();
             iter != _expected.end(); ++iter)
        {
            uint32_t valueCount = _attrPtr->get(iter->first, &_getBuffer[0], _getBuffer.size());
            _validator.VALIDATOR_ASSERT(_minValueCount <= valueCount && valueCount <= _maxValueCount);
            if (valueCount != iter->second.size()) {
                std::cout << "validate(" << iter->first << ")" << std::endl;
                std::cout << "expected(" << iter->second.size() << ")" << std::endl;
                for (size_t i = 0; i < iter->second.size(); ++i) {
                    std::cout << "    [" << iter->second[i].getValue() << ", " << iter->second[i].getWeight() << "]" << std::endl;
                }
                std::cout << "actual(" << valueCount << ")" << std::endl;
                for (size_t i = 0; i < valueCount; ++i) {
                    std::cout << "    [" << _getBuffer[i].getValue() << ", " << _getBuffer[i].getWeight() << "]" << std::endl;
                }
            }
            _validator.VALIDATOR_ASSERT_EQUAL(valueCount, iter->second.size());
            for (uint32_t i = 0; i < valueCount; ++i) {
                _validator.VALIDATOR_ASSERT_EQUAL(_getBuffer[i].getValue(), iter->second[i].getValue());
                _validator.VALIDATOR_ASSERT_EQUAL(_getBuffer[i].getWeight(), iter->second[i].getWeight());
            }
        }
        _expected.clear();
    } else {
        _attrPtr->commit();
    }
}

template <typename Vector, typename T, typename BT>
void
AttributeUpdater<Vector, T, BT>::populate()
{
    _timer.SetNow();
    for (uint32_t doc = 0; doc < _attrPtr->getNumDocs(); ++doc) {
        updateValues(doc);
        if (doc % _commitFreq == (_commitFreq - 1)) {
            commit();
        }
    }
    commit();
    _status._totalUpdateTime += _timer.MilliSecsToNow();
}


template <typename Vector, typename T, typename BT>
void
AttributeUpdater<Vector, T, BT>::update(uint32_t numUpdates)
{
    _timer.SetNow();
    for (uint32_t i = 0; i < numUpdates; ++i) {
        uint32_t doc = getRandomDoc();
        updateValues(doc);
        if (i % _commitFreq == (_commitFreq - 1)) {
            commit();
        }
    }
    commit();
    _status._totalUpdateTime += _timer.MilliSecsToNow();
}


template <typename Vector, typename T, typename BT>
void
AttributeUpdaterThread<Vector, T, BT>::doRun()
{
    this->_timer.SetNow();
    while(!_done) {
        uint32_t doc = this->getRandomDoc();
        this->updateValues(doc);
        if (this->_status._numDocumentUpdates % this->_commitFreq == (this->_commitFreq - 1)) {
            this->commit();
        }
    }
    this->commit();
    this->_status._totalUpdateTime += this->_timer.MilliSecsToNow();
}


} // search

