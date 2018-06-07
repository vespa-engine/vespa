// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::Printable
 * \ingroup util
 *
 * \brief Interfaces for classes with nice debug output operator defined.
 *
 * Especially during testing, it is convenient to be able to print out the
 * contents of a class. Using this interface one need only to implement the
 * print function to get neat output, and hopefully we can get a more unified
 * looking output.
 */

#pragma once

#include <iosfwd>
#include <string>

namespace document {

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
     * @param out The stream to print itself to.
     * @param verbose Whether to print detailed information or not. For instance
     *                a list might print it's size and properties if not verbose
     *                and print each singel element too if verbose.
     * @param indent This indentation should be printed AFTER each newline
     *               printed. (Not before output in first line)
     */
    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const = 0;

    /**
     * Utility function, since default arguments used in virtual functions is
     * kinda sketchy.
     */
    void print(std::ostream& out) const;
    void print(std::ostream& out, bool verbose) const;
    void print(std::ostream& out, const std::string& indent) const;

    /** Utility function to get this output as a string.  */
    std::string toString(bool verbose=false, const std::string& indent="") const;
};

std::ostream& operator<<(std::ostream& out, const Printable& p);

} // document

