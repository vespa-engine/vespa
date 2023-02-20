// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/macro.h>
#include <vespa/vespalib/util/error.h>
#include <exception>

#define VESPALIB_EXCEPTION_USEBACKTRACES

/**
 * @def VESPA_DEFINE_EXCEPTION_SPINE(MyClass)
 * @brief Define common methods needed for exception classes
 *
 * If you make an exception class inheriting (directly or indirectly)
 * from vespalib::Exception you should use this macro inside the body
 * of your class to define the methods getName(), clone()
 * and throwSelf() that always need to be defined in the same way.
 * This must be matched by
 * \ref VESPA_IMPLEMENT_EXCEPTION_SPINE for implementhing the methods.
 * If you're just making an exception type without any special
 * content you can use
 * \ref VESPA_DEFINE_EXCEPTION
 * instead.
 *
 * @param MyClass the name of your class
 **/
#define VESPA_DEFINE_EXCEPTION_SPINE(MyClass) \
  const char *getName() const override;       \
  Exception *clone() const override;          \
  void throwSelf() const override;            \
  MyClass& setCause(const vespalib::Exception& cause);

/**
 * @def VESPA_IMPLEMENT_EXCEPTION_SPINE(MyClass)
 * @brief Implement common methods needed for exception classes
 *
 * This will implement the methods defined by
 * \ref VESPA_DEFINE_EXCEPTION_SPINE
 * @param MyClass the name of your class
 **/
#define VESPA_IMPLEMENT_EXCEPTION_SPINE(MyClass)                             \
  const char *MyClass::getName() const { return VESPA_STRINGIZE(MyClass); }  \
  vespalib::Exception *MyClass::clone() const { return new MyClass(*this); } \
  void MyClass::throwSelf() const { throw MyClass(*this); }                  \
  MyClass& MyClass::setCause(const vespalib::Exception& cause)               \
        { _cause = vespalib::ExceptionPtr(cause); return *this; }

/**
 * @def VESPA_DEFINE_EXCEPTION(MyClass, Parent)
 * @brief Define simple exception class
 *
 * Use this macro to define a new exception class
 * with no extra special content inheriting (directly or
 * indirectly) from vespalib::Exception. This must be matched by
 * \ref VESPA_IMPLEMENT_EXCEPTION
 * for implementing the methods.
 * @param MyClass the name of your class
 * @param Parent the name of the parent class (often just Exception)
 **/
#define VESPA_DEFINE_EXCEPTION(MyClass, Parent)                            \
class MyClass : public Parent {                                            \
public:                                                                    \
    explicit MyClass(vespalib::stringref msg,                              \
            vespalib::stringref location = "", int skipStack = 0);         \
    MyClass(vespalib::stringref msg, const Exception &cause,               \
            vespalib::stringref location = "", int skipStack = 0);         \
    MyClass(const MyClass &);                                              \
    MyClass & operator=(const MyClass &) = delete;                         \
    MyClass(MyClass &&) noexcept;                                          \
    MyClass & operator=(MyClass &&) noexcept;                              \
    ~MyClass() override;                                                   \
    VESPA_DEFINE_EXCEPTION_SPINE(MyClass)                                  \
};

/**
 * @def VESPA_IMPLEMENT_EXCEPTION(MyClass)
 * @brief Implement common methods needed for exception classes
 *
 * This will implement the methods defined by
 * \ref VESPA_DEFINE_EXCEPTION
 * @param MyClass the name of your class
 **/
#define VESPA_IMPLEMENT_EXCEPTION(MyClass, Parent)                           \
    MyClass::MyClass(vespalib::stringref msg,                                \
            vespalib::stringref location, int skipStack)                     \
        : Parent(msg, location, skipStack + 1) {}                            \
    MyClass::MyClass(vespalib::stringref msg, const Exception &cause,        \
            vespalib::stringref location, int skipStack)                     \
        : Parent(msg, cause, location, skipStack + 1) {}                     \
    MyClass::MyClass(const MyClass &) = default;                             \
    MyClass::MyClass(MyClass &&) noexcept = default;                         \
    MyClass & MyClass::operator=(MyClass &&) noexcept = default;             \
    MyClass::~MyClass() = default;                                           \
    VESPA_IMPLEMENT_EXCEPTION_SPINE(MyClass)

namespace vespalib {

class Exception;
class ExceptionPtr;

/**
 * @class ExceptionPtr
 * @brief Object holding an Exception.
 *
 * If you need to store an \ref Exception, use an instance of this class to
 * manage ownership and copying.  It will use clone() when needed so
 * you always get a deep copy, so you can even pass it around to
 * a different thread if needed.
 **/
class ExceptionPtr
{
private:
    Exception *_ref;
public:
    /** @brief default constructor (object will contain a null pointer until assigned). */
    ExceptionPtr();

    /** @brief constructor making a copy of an existing exception. */
    explicit ExceptionPtr(const Exception &e);

    /** @brief copy constructor (deep copy) */
    ExceptionPtr(const ExceptionPtr &rhs);

    /** @brief assignment making a copy of an existing exception */
    ExceptionPtr &operator=(const Exception &rhs);

    /** @brief assignment making a deep copy of another ExceptionPtr */
    ExceptionPtr &operator=(const ExceptionPtr &rhs);

    /** @brief swaps contents with another ExceptionPtr */
    void swap(ExceptionPtr &other);

    /** @brief destructor doing cleanup if needed */
    ~ExceptionPtr();

    /** @brief test if this object actually contains an exception */
    [[nodiscard]] bool isSet() const { return (_ref != nullptr); }

    /** @brief get pointer to currently held exception, returns NULL if not set */
    [[nodiscard]] const Exception *get() const { return _ref; }

    /** @brief use pointer to currently held exception, will crash if not set */
    const Exception *operator->() const { return _ref; }
};

/** swaps contents of two ExceptionPtr objects */
void swap(ExceptionPtr &a, ExceptionPtr &b);

//-----------------------------------------------------------------------------

/**
 * @class Exception
 * @brief Common exception class used by VESPA.
 *
 * This class should be used as parent for all exceptions thrown by VESPA.
 * It adds functionality for rethrowing and adding other exceptions as cause
 * of the rethrow. It adds facility to get what codepiece threw the exception,
 * and even stacktrace functionality. To avoid the significant performance hit
 * associated with resolving stack frame symbols, resolving is done lazily
 * and only when what() or toString() is invoked.
 *
 * Give the \ref VESPA_STRLOC
 * macro as input to location to get the exception to report where it came from.
 *
 * When inheriting from this class you should always use one of the
 * \ref VESPA_DEFINE_EXCEPTION
 * or \ref VESPA_DEFINE_EXCEPTION_SPINE
 * macros.
 *
 **/
class Exception : public std::exception
{
private:
    static const int STACK_FRAME_BUFFER_SIZE = 25;

    mutable string _what;
    string         _msg;
    string         _location;
    void*          _stack[STACK_FRAME_BUFFER_SIZE];
    int            _stackframes;
    int            _skipStack;

protected:
    ExceptionPtr   _cause;

public:
    /**
     * @brief Construct an exception with a message and a source code location.
     * @param msg A user-readable message describing the problem
     * @param location The location in the source code where the problem cause is.
     *                 use the VESPA_STRLOC to get a standard-format string describing
     *                 the current source code location.
     * @param skipStack Normally use the default-provided 0 value; subclasses
     *                  should send (skipStack + 1) to the parent constructor (see
     *                  \ref VESPA_DEFINE_EXCEPTION for subclass implementation).
     **/
    explicit Exception(stringref msg, stringref location = "", int skipStack = 0);
    /**
     * @brief Construct an exception with a message, a causing exception, and a source code location.
     * @param msg A user-readable message describing the problem
     * @param cause Another exception that was the underlying cause of the problem
     * @param location The location in the source code where the problem cause is.
     *                 use the VESPA_STRLOC to get a standard-format string describing
     *                 the current source code location.
     * @param skipStack Normally use the default-provided 0 value; subclasses
     *                  should send (skipStack + 1) to the parent constructor (see
     *                  \ref VESPA_DEFINE_EXCEPTION for subclass implementation).
     **/
    Exception(stringref msg, const Exception &cause,
              stringref location = "", int skipStack = 0);
    Exception(const Exception &);
    Exception & operator = (const Exception &);
    Exception(Exception &&) noexcept;
    Exception & operator = (Exception &&) noexcept;
    ~Exception() override;


    /** @brief Returns a string describing the current exception, including cause if any */
    const char *what() const noexcept override; // should not be overridden

    /** @brief Returns a pointer to underlying cause (or NULL if no cause) */
    const Exception *getCause() const { return _cause.get(); }

    /** @brief Returns the msg parameter that this Exception was constructed with */
    const string &getMessage() const { return _msg; }

    /** @brief Returns the message string */
    const char *message() const { return _msg.c_str(); }

    /** @brief Returns the location parameter that this Exception was constructed with */
    const string &getLocation() const { return _location; }

    /** @brief Returns the actual class name of the exception */
    virtual const char *getName() const;

    /** @brief Clones the actual object */
    virtual Exception *clone() const;

    /** @brief Throw a copy of this object */
    virtual void throwSelf() const;

    /** @brief make a string describing the current object, not including cause */
    virtual string toString() const;
};

} // namespace vespalib
