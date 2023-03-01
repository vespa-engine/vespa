<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
CMake Guide
===========

This is a guide describing how to use the Vespa specific CMake functions and macros.
The Vespa CMake setup wraps or combines together a number of CMake functions in order to simplify defining library and executable targets, and to automatically create targets that depend on a subset of targets.

# `vespa_add_library`
The `vespa_add_library` function is used to define a library.
At the least, it takes a target name, and the library's source files.

    vespa_add_library(<target-name> [STATIC|OBJECT|INTERFACE|TEST]
        [SOURCES <source-file> [source-file ...]]
        [OUTPUT_NAME <name-of-library>]
        [DEPENDS <other-target|external-library> [other-target|external-library ...]]
        [AFTER <other-target> [other-target ...]]
    )

## Parameters

### STATIC
Parameter denoting that this is a static library.
If this parameter is omitted, the library is shared.

### OBJECT
Parameter denoting that this is an object library.
This parameter is optional.

### INTERFACE
Parameter denoting that this is an interface library, that is, a library producing no object files, for example a collection of headers.
This parameter is optional.

### TEST
Parameter denoting that this is a "test library".
Test libraries are not added to the `all` target if the CMake cached variable `EXCLUDE_TESTS_FROM_ALL` is true.

### SOURCES [source-file ...]
The `SOURCES` parameter takes a list of source files for this library.
This parameter can be omitted if you're defining an interface or config-only library.

### OUTPUT_NAME <name-of-library>
Specifies the base name of the produced library file (SONAME). E.g., in Linux, if the output name is `stroustrup`, the library file would be named `libstroustrup.so`
This parameter is optional.
If it is not set, the library takes its name from the target name.

### DEPENDS [other-target-or-lib ...]
The `DEPENDS` parameter takes a list of targets or external libraries to link to, with the following exception:
* If `target-name` is an object library and `other-target-or-lib` is another target, instead add a dependency on `other-target-or-lib` (like `AFTER`).
This parameter is optional.

### AFTER [other-target ...]
Add a (weak) build dependency on every target in the given list, requiring only that the target is built before `target-name`.
This parameter is optional.


# `vespa_add_executable`
The `vespa_add_executable` function is used to define an executable/application.
At the least, the function takes a target name, and the executable's source files.

    vespa_add_executable(<target-name> [TEST]
        SOURCES <source-file> [source-file ...]
        [OUTPUT_NAME <name-of-executable>]
        [DEPENDS <other-target|external-library> [other-target|external-library ...]]
        [AFTER <other-target> [other-target ...]]
    )

### TEST
Parameter denoting that this is a "test executable".
Test executables are not added to the `all` target if the CMake cached variable `EXCLUDE_TESTS_FROM_ALL` is set to a true value.

### SOURCES [source_file ...]
The `SOURCES` parameter takes a list of source files for this executable.
This parameter is required.

### OUTPUT-NAME <name-of-executable>
Specifies the base name of the produced executable file (omitting the file extension).
This parameter is optional.
If this parameter is not set, the executable takes its name from the target name.

### DEPENDS [other-target-or-lib ...]
The `DEPENDS` parameter takes a list of targets or external libraries to link to.
This parameter is optional.

### AFTER [other-target ...]
Add a (weak) build dependency on every target in the given list, requiring only that the target is built before `target-name`.
This parameter is optional.


# `vespa_generate_config`

    vespa_generate_config(<for-target-name> <config-def-path>)
    
`vespa_generate_config` generates from config definition (`.def`) files.

`<for-target-name>` is the name of the target you want the generated config object to be linked to.
This target needs to have been defined before you call `vespa_generate_config`.


# `vespa_define_module`

The `vespa_define_module` defines a *module*.
A module is a collection of targets under a directory root (e.g. `searchlib`)
The name of the module is the same as the directory name where the module is defined.
The module definition is used to specify common dependencies for every target defined (by use of `vespa_add_library/executable`) under the module.


        vespa_define_module(
            DEPENDS
            vespalib
            bjarnelib

            EXTERNAL_DEPENDS
            xml2

            LIBS
            src/vespa/lib1
            src/vespa/lib2
            
            LIB_DEPENDS
            docproc2000
            
            LIB_EXTERNAL_DEPENDS
            andrei_allocators

            APPS
            src/apps/app1
            src/apps/app2
            
            APP_DEPENDS
            searchlib
            
            APP_EXTERNAL_DEPENDS
            stroustrup_utils

            TESTS
            src/tests/app1_test
            src/tests/app2_test
            
            TEST_DEPENDS
            homemade_printf_from_uni
            
            TEST_EXTERNAL_DEPENDS
            andrei_allocators
            cppunit
        )

Directories containing `CMakeLists.txt` with library/executable definitions are divided into three categories by the use of the following `vespa_define_module` parameters: `LIBS` (libraries), `APPS` (applications), and `TESTS`.

You can define common target dependencies for all targets defined with `vespa_add_library/executable` by listing them using the `LIB_DEPENDS`/`APP_DEPENDS`/`TEST_DEPENDS` parameters.
Use `LIB_EXTERNAL_DEPENDS`/`APP_EXTERNAL_DEPENDS`/`TEST_EXTERNAL_DEPENDS` for external library dependencies (this is different from `DEPENDS` in `vespa_add_library/executable` which can be used to specify both internal target dependencies and external library dependencies).
Finally, you can use `DEPENDS` and `EXTERNAL_DEPENDS` to specify, respectively, common target dependencies and common external library dependencies for all the categories.

The definitions within these directories need not strictly adhere to categories in which their parent directory are listed; the categories are simply a way to split common dependencies into three disjoint sets.


# `vespa_add_test`

    vespa_add_test(NAME <test-name> [NO_VALGRIND|RUN_SERIAL|BENCHMARK]
        COMMAND <command> [command ...]
        [WORKING_DIRECTORY <working-directory>]
        [ENVIRONMENT <environment-stringlist>]
    )
    
`vespa_add_test` defines a test using the CTest integration of CMake.
Have in mind that tests are **not** targets, so they can't depend on other targets.
If your test depends on a target being built for it to run, you need to build that target first.
Running the test will not trigger building targets required by the test.

`vespa_add_test` takes the following parameters.

### NAME <test-name>
Name of test as shown by the output of CTest and used as an identifier for running tests based on a regex match on test names.
This parameter is required.

### NO_VALGRIND
Do not run this test under Valgrind even if the CMake variable `VALGRIND_UNIT_TESTS` is true. 
This parameter is optional and defaults to false.

### RUN_SERIAL
Do not run this test in parallel with any other test.
This parameter is optional and defaults to false.

### BENCHMARK
Only run this test if the CMake variable `RUN_BENCHMARKS` is true.
This parameter is optional and defaults to false.

### COMMAND
Either the name of a target that produces an executable (whereby the test will run the executable) or a command line that is to be executed when the test runs.
This parameter is required.

### WORKING_DIRECTORY
Working directory when running the test.
This parameter is optional and defaults to `CMAKE_CURRENT_BINARY_DIR`.

### ENVIRONMENT
A quoted list in the form `"NAME1=value1;NAME2=value2"`


# `vespa_install_script`

    vespa_install_script(<filename> [<to-filename>] <destination-dir>)

Install (copy) a shell script `<filename>`, optionally as the destination filename `<to-filename>`, to the directory `<destination-dir>` under the install prefix, and set correct executable permissions.
