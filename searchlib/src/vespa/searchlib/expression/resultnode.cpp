// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultnode.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exception.h>
#include <stdexcept>

namespace search {
namespace expression {

int64_t
ResultNode::onGetEnum(size_t index) const {
    (void) index;
    throw vespalib::Exception("search::expression::ResultNode onGetEnum is not implemented");
}

uint64_t ResultNode::radixAsc(const void * buf) const
{
    (void) buf;
    throw std::runtime_error("ResultNode::radixAsc(const void * buf) must be overloaded by'" + vespalib::string(getClass().name()) + "'.");
}

uint64_t ResultNode::radixDesc(const void * buf) const
{
    (void) buf;
    throw std::runtime_error("ResultNode::radixDesc(const void * buf) must be overloaded by'" + vespalib::string(getClass().name()) + "'.");
}

size_t ResultNode::hash(const void * buf) const
{
    (void) buf;
    throw std::runtime_error("ResultNode::hash(const void * buf) must be overloaded by'" + vespalib::string(getClass().name()) + "'.");
}

void ResultNode::decode(const void * buf)
{
    (void) buf;
    throw std::runtime_error("ResultNode::decode(const void * buf) must be overloaded by'" + vespalib::string(getClass().name()) + "'.");
}

void ResultNode::encode(void * buf) const
{
    (void) buf;
    throw std::runtime_error("ResultNode::encode(void * buf) const must be overloaded by'" + vespalib::string(getClass().name()) + "'.");
}

void ResultNode::swap(void * buf)
{
    (void) buf;
    throw std::runtime_error("ResultNode::swap(void * buf) must be overloaded by'" + vespalib::string(getClass().name()) + "'.");
}

void ResultNode::create(void * buf) const
{
    (void) buf;
    throw std::runtime_error("ResultNode::create(void * buf) const must be overloaded by'" + vespalib::string(getClass().name()) + "'.");
}

void ResultNode::destroy(void * buf) const
{
    (void) buf;
    throw std::runtime_error("ResultNode::destroy(void * buf) const must be overloaded by'" + vespalib::string(getClass().name()) + "'.");
}

int ResultNode::cmpMem(const void * a, const void *b) const
{
    (void) a;
    (void) b;
    throw std::runtime_error("ResultNode::cmpMem(const void * a, const void *b) const must be overloaded by'" + vespalib::string(getClass().name()) + "'.");
}

size_t ResultNode::getRawByteSize() const
{
    throw std::runtime_error("ResultNode::getRawByteSize() const must be overloaded by '" + vespalib::string(getClass().name()) + "'.");
}

const BucketResultNode&
ResultNode::getNullBucket() const {
    throw std::runtime_error(vespalib::make_string("No null bucket defined for this type"));
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_resultnode() {}
