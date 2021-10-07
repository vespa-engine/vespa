// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*! \mainpage Vespalib - C++ utility library for Vespa components
 *
 * \section intro_sec Introduction
 *
 * vespalib is a collection of simple utility classes shared
 * between most Vespa components that are written in C++.
 * Most of these aren't Vespa specific in any way, except that
 * they reflect the Vespa code standard and conventions.
 *
 * \section install_sec Installation
 *
 * install vespa_vespalib_dev
 *
 * \section overview_sec Overview
 *
 * Most of the classes in vespalib can be used by themselves,
 * for a full list see the alphabetical class list.
 * Here are the major groups of classes:
 *
 * Generation counter
 *
 * vespalib::GenCnt
 *
 * Reference counting and atomic operations
 *
 * <BR> vespalib::ReferenceCounter	
 *
 * Simple hashmap
 *
 * \ref vespalib::HashMap<T>
 *
 * Scope guards for easier exception-safety
 *
 * vespalib::CounterGuard	
 * <BR> vespalib::DirPointer
 * <BR> vespalib::FileDescriptor	
 * <BR> vespalib::FilePointer	
 * <BR> \ref vespalib::MaxValueGuard<T>	
 * <BR> \ref vespalib::ValueGuard<T>	
 *
 * General utility macros
 * <BR> \ref VESPA_STRLOC
 * <BR> \ref VESPA_STRINGIZE(str)
 *
 * Standalone testing framework
 *
 * vespalib::TestApp	
 */
