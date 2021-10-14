// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdio.h>
#include <dirent.h>
#include <unistd.h>

namespace vespalib {

/**
 * @brief A FilePointer wraps a bald FILE pointer inside a guarding
 * object.
 *
 * The underlying file is closed when the FilePointer object is
 * destructed.
 **/
class FilePointer
{
private:
    FILE *_fp;
    FilePointer(const FilePointer &);
    FilePointer &operator=(const FilePointer &);
public:
    /**
     * @brief Create a FilePointer from a FILE pointer.
     *
     * @param file the underlying FILE pointer
     **/
    explicit FilePointer(FILE *file = NULL) : _fp(file) {}
    /**
     * @brief Close the file if it is still open.
     **/
    ~FilePointer() { reset(); }
    /**
     * @brief Check whether we have a FILE pointer (not NULL)
     *
     * @return true if we have an underlying FILE pointer
     **/
    bool valid() const { return (_fp != NULL); }
    /**
     * @brief Obtain the internal FILE pointer
     *
     * @return internal FILE pointer
     **/
    FILE *fp() const { return _fp; }
    /**
     * @brief Implicit cast to obtain internal FILE pointer
     *
     * @return internal FILE pointer
     **/
    operator FILE*() { return _fp; }
    /**
     * @brief Take ownership of a new FILE pointer.
     *
     * The previously owned FILE pointer is closed, if present.
     **/
    void reset(FILE *file = NULL) {
        if (valid()) {
            fclose(_fp);
        }
        _fp = file;
    }
    /**
     * @brief Release ownership of the current FILE pointer.
     *
     * The file will no longer be closed by the destructor.
     *
     * @return the released FILE pointer
     **/
    FILE *release() {
        FILE *tmp = _fp;
        _fp = NULL;
        return tmp;
    }
};


/**
 * @brief A DirPointer wraps a bald DIR pointer inside a guarding object.
 *
 * The underlying directory is closed when the DirPointer object is
 * destructed.
 **/
class DirPointer
{
private:
    DIR *_dp;
    DirPointer(const DirPointer &);
    DirPointer &operator=(const DirPointer &);
public:
    /**
     * @brief Create a DirPointer from a DIR pointer.
     *
     * @param dir the underlying DIR pointer
     **/
    explicit DirPointer(DIR *dir = NULL) : _dp(dir) {}
    /**
     * Close the directory if it is still open.
     **/
    ~DirPointer() { reset(); }
    /**
     * @brief Check whether we have a DIR pointer (not NULL)
     *
     * @return true if we have an underlying DIR pointer
     **/
    bool valid() const { return (_dp != NULL); }
    /**
     * @brief Obtain the internal DIR pointer
     *
     * @return internal DIR pointer
     **/
    DIR *dp() const { return _dp; }
    /**
     * @brief Implicit cast to obtain internal DIR pointer
     *
     * @return internal DIR pointer
     **/
    operator DIR*() { return _dp; }
    /**
     * @brief Take ownership of a new DIR pointer.
     *
     * The previously owned DIR pointer is closed, if present.
     **/
    void reset(DIR *dir = NULL) {
        if (valid()) {
            closedir(_dp);
        }
        _dp = dir;
    }
    /**
     * @brief Release ownership of the current DIR pointer.
     *
     * The directory will no longer be closed by the destructor.
     *
     * @return the released DIR pointer
     **/
    DIR *release() {
        DIR *tmp = _dp;
        _dp = NULL;
        return tmp;
    }
};


/**
 * @brief A FileDescriptor wraps a file descriptor inside a guarding object.
 *
 * The underlying file is closed when the FileDescriptor object is
 * destructed.
 **/
class FileDescriptor
{
private:
    int _fd;
    FileDescriptor(const FileDescriptor &);
    FileDescriptor &operator=(const FileDescriptor &);
public:
    /**
     * @brief Create a FileDescriptor from a file descriptor.
     *
     * @param file the underlying file descriptor
     **/
    explicit FileDescriptor(int file = -1) : _fd(file) {}
    /**
     * @brief Close the file if it is still open.
     **/
    ~FileDescriptor() { reset(); }
    /**
     * @brief Check whether we have a file descriptor (not -1)
     *
     * @return true if we have an underlying file descriptor
     **/
    bool valid() const { return (_fd >= 0); }
    /**
     * @brief Obtain the internal file descriptor
     *
     * @return internal file descriptor
     **/
    int fd() const { return _fd; }
    /**
     * @brief Take ownership of a new file descriptor.
     *
     * The previously owned file descriptor is closed, if present.
     **/
    void reset(int file = -1) {
        if (valid()) {
            close(_fd);
        }
        _fd = file;
    }
    /**
     * @brief Release ownership of the current file descriptor.
     *
     * The file will no longer be closed by the destructor.
     *
     * @return the released file descriptor
     **/
    int release() {
        int tmp = _fd;
        _fd = -1;
        return tmp;
    }
};


/**
 * @brief A CounterGuard increases an int in the constructor and decreases
 * it in the destructor.
 *
 * This is typically used to keep track of how many threads are inside
 * a given scope at a given time (for example, how many threads that
 * are currently waiting for a specific signal on a monitor).
 **/
class CounterGuard
{
private:
    int &_cnt;
    CounterGuard(const CounterGuard &);
    CounterGuard &operator=(const CounterGuard &);
public:
    /**
     * @brief Increase the value
     *
     * @param cnt a reference to the value that will be modified
     **/
    explicit CounterGuard(int &cnt) : _cnt(cnt) { ++cnt; }
    /**
     * @brief Decrease the value
     **/
    ~CounterGuard() { --_cnt; }
};


/**
 * @brief A ValueGuard is used to set a variable to a specific value
 * when the ValueGuard is destructed.
 *
 * This can be used to revert a variable if an exception is thrown.
 * However, you must remember to dismiss the guard if you don't want
 * it to set the value when it goes out of scope.
 **/
template<typename T>
class ValueGuard
{
private:
    bool _active;
    T   &_ref;
    T    _value;

    ValueGuard(const ValueGuard &);
    ValueGuard &operator=(const ValueGuard &);
public:
    /**
     * @brief Create a ValueGuard for the given variable.
     *
     * The variable will be reverted to its original value in the destructor.
     *
     * @param ref the variable that will be modified
     **/
    explicit ValueGuard(T &ref) : _active(true), _ref(ref), _value(ref) {}
    /**
     * @brief Create a ValueGuard for the given variable.
     *
     * The variable will be set to the given value in the destructor.
     *
     * @param ref the variable that will be modified
     * @param val the value it will be set to
     **/
    ValueGuard(T &ref, const T &val) : _active(true), _ref(ref), _value(val) {}
    /**
     * @brief Reset the variable.
     *
     * Set the variable to the value defined in the constructor or the
     * update method. If dismiss has been invoked, the variable is not
     * modified.
     **/
    ~ValueGuard() {
        if (_active) {
            _ref = _value;
        }
    }
    /**
     * @brief Dismiss this guard.
     *
     * When a guard has been dismissed, the destructor will not modify
     * the variable. The dismiss method is typically used to indicate
     * that everything went ok, and that we no longer need to protect
     * the variable from exceptions.
     **/
    void dismiss() { _active = false; }
    /// @brief See dismiss
    void deactivate() { dismiss(); }
    /**
     * @brief Update the value the variable will be set to in the
     * destructor.
     *
     * This can be used to set revert points during execution.
     **/
    void update(const T &val) { _value = val; }
    void operator=(const T& val) { update(val); }
};


/**
 * @brief A MaxValueGuard is used to enfore an upper bound on the
 * value of a variable when the MaxValueGuard is destructed.
 *
 * This can be used to revert a variable if an exception is thrown.
 * However, you must remember to dismiss the guard if you don't want
 * it to set the value when it goes out of scope.
 **/
template<typename T>
class MaxValueGuard {
    bool _active;
    T   &_ref;
    T    _value;

    MaxValueGuard(const MaxValueGuard &);
    MaxValueGuard &operator=(const MaxValueGuard &);
public:
    /**
     * @brief Create a MaxValueGuard for the given variable.
     *
     * The variable will be reverted back to its original value in the
     * destructor if it has increased.
     *
     * @param ref the variable that will be modified
     **/
    explicit MaxValueGuard(T &ref) : _active(true), _ref(ref), _value(ref) {}
    /**
     * @brief Create a ValueGuard for the given variable.
     *
     * The given upper bound will be enforced in the destructor.
     *
     * @param ref the variable that will be modified
     * @param val upper bound for the variable
     **/
    MaxValueGuard(T& ref, const T& val) : _active(true), _ref(ref), _value(val) {}
    /**
     * @brief Enforce the upper bound.
     *
     * If the current value of the variable is greater than the upper
     * bound, it is set to the upper bound as defined in the
     * constructor or the update method. If dismiss has been invoked,
     * the variable is not modified.
     **/
    ~MaxValueGuard() {
        if (_active && _ref > _value) {
            _ref = _value;
        }
    }
    /**
     * @brief Dismiss this guard.
     *
     * When a guard is dismissed, the destructor will not modify the
     * variable. The dismiss method is typically used to indicate that
     * everything went ok, and that we no longer need to protect the
     * variable from exceptions.
     **/
    void dismiss() { _active = false; }
    /// @brief See dismiss
    void deactivate() { dismiss(); }
    /**
     * @brief Update the upper bound that will be enforced in the
     * destructor.
     *
     * This can be used to set revert points during execution.
     **/
    void update(const T &val) { _value = val; }
    /// @brief See update.
    void operator=(const T& val) { update(val); }
};

} // namespace vespalib

