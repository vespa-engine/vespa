// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "test.h"

namespace fast::testsuite {

Test::Test(std::ostream* osptr, const char*name) :
    m_osptr(osptr),
    name_(name),
    m_nPass(0),
    m_nFail(0),
    m_index(0),
    m_description()
{
    m_pchar[0]= '|';
    m_pchar[1]= '-';
}

Test::Test(const char*name) :
    Test(nullptr, name)
{
}

const char *Test::get_name() const {
    return (name_ == NULL) ? "Test " : name_;
}

const std::string& Test::GetSourceDirectory()
{
    static const std::string srcDir = [] () {
        std::string dir(".");
        const char* env = getenv("SOURCE_DIRECTORY");
        if (env) {
            dir = env;
        }
        if (*dir.rbegin() != '/') {
            dir += "/";
        }
        return dir;
    } ();
    return srcDir;
}

long Test::GetNumPassed() const
{
    return m_nPass;
}

long Test::GetNumFailed() const
{
    return m_nFail;
}

const std::ostream* Test::GetStream() const
{
    return m_osptr;
}

void Test::SetStream(std::ostream* osptr)
{
    m_osptr = osptr;
}

void Test::_Succeed()
{
    ++m_nPass;
}

void Test::Reset()
{
    m_nPass = m_nFail = 0;
}

void Test::PushDesc(const std::string& desc)
{
    m_description.push_back(desc);
}

void Test::PopDesc()
{
    m_description.pop_back();
}

size_t Test::print_desc() const
{
    std::copy(m_description.begin(), m_description.end(),
              std::ostream_iterator<std::string>(*m_osptr));
    return m_description.size();
}

void Test::print_progress() {
    ++m_index;
    m_index = m_index % 2;
    *m_osptr << '\b' <<'\b' <<'\b';
    *m_osptr <<' ' << m_pchar[m_index] << ' ' << std::flush;
}

bool Test::do_fail(const std::string& lbl, const char* fname, long lineno,
                   bool addEndl)
{
    ++m_nFail;
    if (m_osptr) {
        *m_osptr << std::endl
                 << fname << ':' << lineno << ": "
                 << get_name() << " failure: (" << lbl << ")"
                 << std::endl;
        if (addEndl && print_desc() > 0)
            *m_osptr << std::endl << std::endl;
    }
    return false;
}

bool Test::do_test(bool cond, const std::string& lbl,
                   const char* fname, long lineno)
{
    if (!cond) {
        return do_fail(lbl, fname, lineno);
    }
    else {
        _Succeed();
        print_progress();
        return true;
    }
}

long Test::Report(int padSpaces) const
{
    if (m_osptr) {
        *m_osptr << std::endl << get_name();

        // Pad the name with the given number of spaces
        for (int i= 0; i < padSpaces; ++i) *m_osptr << ' ';

        *m_osptr << "\tPassed: " << m_nPass
                 << "\tFailed: " << m_nFail
                 << std::endl;
    }
    return m_nFail;
}

}
