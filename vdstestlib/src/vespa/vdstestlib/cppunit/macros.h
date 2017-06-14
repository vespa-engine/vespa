// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This file contains additional CPPUNIT macros to simplify tests.
 */
#pragma once
#include <cppunit/extensions/HelperMacros.h>


// Wrapper for CPPUNIT_ASSERT_EQUAL_MESSAGE to prevent it from evaluating
// message if val1 is equal to val2
#define CPPUNIT_ASSERT_EQUAL_MSG(message, val1, val2) \
    { \
        if (!((val1) == (val2))) { \
            CPPUNIT_ASSERT_EQUAL_MESSAGE(message, val1, val2); \
        } \
    }

#define CPPUNIT_ASSERT_EQUAL_ESCAPED(val1, val2) \
    { \
        if (!((val1) == (val2))) { \
            std::ostringstream out1; \
            std::ostringstream out2; \
            out1 << "[" << val1 << "]"; \
            out2 << "[" << val2 << "]"; \
            CPPUNIT_ASSERT_EQUAL( \
                    document::StringUtil::escape(out1.str()), \
                    document::StringUtil::escape(out2.str())); \
        } \
    }

// Wrapper for CPPUNIT_ASSERT_MESSAGE to prevent it from evaluating message if
// val is true
#define CPPUNIT_ASSERT_MSG(message, val) \
    { \
        if (!(val)) { \
            CPPUNIT_ASSERT_MESSAGE(message, val); \
        } \
    }

// Assert that value starts with prefix
#define CPPUNIT_ASSERT_PREFIX(prefix, value) \
    { \
        std::ostringstream pre; \
        pre << prefix; \
        std::ostringstream val; \
        val << value; \
        if (val.str().find(pre.str()) != 0) { \
            CPPUNIT_FAIL("Value of '" + val.str() + "' does not contain " \
                         "prefix '" + pre.str() + "'."); \
        } \
    }

// Assert that value contains given substring
#define CPPUNIT_ASSERT_CONTAIN(contained, value) \
    { \
        std::ostringstream cont; \
        cont << contained; \
        std::ostringstream val; \
        val << value; \
        if (val.str().find(cont.str()) == std::string::npos) { \
            CPPUNIT_FAIL("Value of '" + val.str() + "' does not contain '" \
                         + cont.str() + "'."); \
        } \
    }

// Assert that value contains given substring, add message to output on error
#define CPPUNIT_ASSERT_CONTAIN_MESSAGE(message, contained, value) \
    { \
        std::ostringstream cont; \
        cont << contained; \
        std::ostringstream val; \
        val << value; \
        std::string mess = message; \
        if (val.str().find(cont.str()) == std::string::npos) { \
            CPPUNIT_FAIL(mess + ": Value of '" + val.str() \
                         + "' does not contain '" + cont.str() + "'."); \
        } \
    }

// Assert that given expression matches the given regular expression.
#include <vespa/vespalib/util/regexp.h>
#define CPPUNIT_ASSERT_MATCH_REGEX(expression, value) \
    { \
        std::ostringstream _ost_; \
        _ost_ << value; \
        std::string _s_(_ost_.str()); \
        vespalib::Regexp _myregex_(expression); \
        if (!_myregex_.match(_s_)) { \
            CPPUNIT_FAIL("Value of '" + _s_ + "' does not match regex '" \
                         + expression + "'."); \
        } \
    }

// Assert that given expression matches the given regular expression.
#include <vespa/vespalib/util/regexp.h>
#define CPPUNIT_ASSERT_MATCH_REGEX_MSG(message, expression, value) \
    { \
        std::ostringstream _ost_; \
        _ost_ << value; \
        std::string _s_(_ost_.str()); \
        vespalib::Regexp _myregex_(expression); \
        std::string mess = message; \
        if (!_myregex_.match(_s_)) { \
            CPPUNIT_FAIL("Value of '" + _s_ + "' does not match regex '" \
                         + expression + "'. Message: '" + mess + "'"); \
        } \
    }

#define CPPUNIT_ASSERT_FILE_CONTAINS(expected, filename) \
    { \
        std::ostringstream value; \
        value << expected; \
        std::ostringstream ost; \
        std::string line; \
        std::ifstream input(filename); \
        while (std::getline(input, line, '\n')) { \
            ost << line << '\n'; \
        } \
        CPPUNIT_ASSERT_EQUAL(value.str(), ost.str()); \
    }

#define CPPUNIT_ASSERT_SUBSTRING_COUNT(source, expectedCount, substring) \
    { \
        uint32_t count = 0; \
        std::ostringstream value; /* Let value be non-strings */ \
        value << source; \
        std::string s(value.str()); \
        std::string::size_type pos = s.find(substring); \
        while (pos != std::string::npos) { \
            ++count; \
            pos = s.find(substring, pos+1); \
        } \
        if (count != (uint32_t) expectedCount) { \
            std::ostringstream error; \
            error << "Value of '" << s << "' contained " << count \
                  << " instances of substring '" << substring << "', not " \
                  << expectedCount << " as expected."; \
            CPPUNIT_FAIL(error.str()); \
        } \
    }

#include <ostream>
#include <map>
#include <unordered_map>
#include <vector>

// Create output operator for containers.
// Needed so we can use CPPUNIT_ASSERT_EQUAL with them.

// TODO: Remove these functions from the std namespace.
namespace std {
    template<typename T>
    inline std::ostream& operator<<(std::ostream& out, const std::vector<T>& v)
    {
        out << "std::vector(" << v.size() << ") {";
        for (uint32_t i=0, n=v.size(); i<n; ++i) {
            out << "\n  " << v[i];
        }
        if (v.size() > 0) out << "\n";
        return out << "}";
    }
    template<typename T>
    inline std::ostream& operator<<(std::ostream& out, const std::set<T>& v)
    {
        out << "std::set(" << v.size() << ") {";
        for (typename std::set<T>::const_iterator it = v.begin(); it != v.end();
             ++it)
        {
            out << "\n  " << *it;
        }
        if (v.size() > 0) out << "\n";
        return out << "}";
    }
    template<typename S, typename T>
    inline std::ostream& operator<<(std::ostream& out, const std::map<S, T>& m)
    {
        out << "std::map(" << m.size() << ") {";
        for (typename std::map<S, T>::const_iterator it = m.begin();
             it != m.end(); ++it)
        {
            out << "\n  " << *it;
        }
        if (m.size() > 0) out << "\n";
        return out << "}";
    }
    template<typename S, typename T>
    inline std::ostream& operator<<(std::ostream& out, const std::pair<S, T>& p)
    {
        return out << "std::pair(" << p.first << ", " << p.second << ")";
    }
}

template<typename S, typename T>
std::ostream&
operator<<(std::ostream& out, const std::unordered_map<S, T>& umap)
{
    out << "std::unordered_map(" << umap.size() << ") {";
    for (auto keyValue : umap) {
        out << "\n  " << keyValue.first << ": " << keyValue.second;
    }
    if (!umap.empty()) {
        out << "\n";
    }
    out << "}";
    return out;
}
