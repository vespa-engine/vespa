// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class vespalib::Printable
 * \ingroup util
 *
 * \brief Utility class for printing of class instances.
 *
 * By implementing this class, you can implement only the print function to get:
 *   - A toString() implementation.
 *   - An operator<< implementation.
 *   - Indentation support to simplify printing objects recursively.
 *
 * A verbose flag is also given. Non-verbose mode is used by default, but for
 * debugging use it may be helpful to look at more detailed state of objects.
 *
 * \class vespalib::AsciiPrintable
 * \ingroup util
 *
 * \brief Similar utility as Printable, but for vespalib string/stream.
 *
 * Sadly, std::string may have performance issues in some contexts, as it does
 * some synchronization to allow sharing content, and requires heap allocation.
 * std::ostream also have issues causing it to require some synchronization.
 *
 * The AsciiPrintable class implements similar functionality as Printable,
 * using the vespalib classes on top of the STL functionality. Using this class
 * instead, toString() and operator<< can be more efficient, while it is still
 * backward compatible with STL variants.
 */

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace vespalib {

class asciistream;

class Printable {
public:
    virtual ~Printable() {}

    /**
     * Print instance textual to the given stream.
     *
     * This function is expected to NOT add a newline after the last line
     * printed.
     *
     * You should be properly indented before calling this function. The indent
     * variable tells you what you need to add after each newline to get
     * indented as far as your first line was. Thus, single line output don't
     * need to worry about indentation.
     *
     * A typical multiline print would thus be something like this:
     * <pre>
     *   out << "MyClass() {\n"
     *       << "\n" << indent << "  some info"
     *       << "\n" << indent << "}";
     * </pre>
     *
     * By not adding whitespace in either end of the output, we give maximum
     * freedom for nesting output. For instance, if I wanted to create output of
     * class OtherClass that inherited MyClass and I wanted it to contain all
     * the information I could write OtherClass output like this:
     * <pre>
     *   out << "OtherClass() : ";
     *   MyClass::print(out, verbose, indent + "  ");
     *   out << "\n" << indent << {\n"
     *       << "\n" << indent << "  some more info"
     *       << "\n" << indent << "}";
     * </pre>
     *
     * @param out The stream to print itself to.
     * @param verbose Whether to print detailed information or not. For instance
     *                a list might print it's size and properties if not verbose
     *                and print each singel element too if verbose. Default for
     *                toString() and output operators is false.
     * @param indent This indentation should be printed AFTER each newline
     *               printed. (Not before output in first line)
     */
    virtual void print(std::ostream& out,
                       bool verbose = false,
                       const std::string& indent = "") const = 0;

    /** Utility functions to get print() output as a string.  */
    std::string toString(bool verbose = false,
                         const std::string& indent = "") const;

};

class AsciiPrintable : public Printable {
public:
    virtual ~AsciiPrintable() {}

    enum PrintMode {
        NORMAL,
        VERBOSE
    };

    class PrintProperties {
        PrintMode _mode;
        vespalib::string _indent;

    public:
        PrintProperties(PrintMode mode = NORMAL, stringref indent_ = "")
            : _mode(mode), _indent(indent_) {}

        PrintProperties indentedCopy() const
            { return PrintProperties(_mode, _indent + "  "); }
        bool verbose() const { return (_mode == VERBOSE); }
        const vespalib::string& indent() const { return _indent; }
        vespalib::string indent(uint32_t extraLevels) const;
    };

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    virtual void print(vespalib::asciistream&, const PrintProperties& = PrintProperties()) const = 0;

    vespalib::string toString(const PrintProperties& = PrintProperties()) const;
};

std::ostream& operator<<(std::ostream& out, const Printable& p);
vespalib::asciistream& operator<<(vespalib::asciistream& out, const AsciiPrintable& p);

template<typename T>
void print(const std::vector<T> & v, vespalib::asciistream& out, const AsciiPrintable::PrintProperties& p);

} // vespalib
