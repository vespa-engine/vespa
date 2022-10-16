# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# @author Vegard Sjonfjell
# @author Arnstein Ressem

function(vespa_add_module_dependency OTHER_TARGET_OR_LIB)
    if (TARGET ${OTHER_TARGET_OR_LIB})
        get_target_property(OTHER_TARGET_TYPE ${OTHER_TARGET_OR_LIB} TYPE)
        if(OTHER_TARGET_TYPE STREQUAL OBJECT_LIBRARY)
            include_directories($<TARGET_PROPERTY:${OTHER_TARGET_OR_LIB},INTERFACE_INCLUDE_DIRECTORIES>)
            return()
        endif()
    endif()

    link_libraries(${OTHER_TARGET_OR_LIB})
endfunction()

function(vespa_add_target_dependency TARGET OTHER_TARGET)
    get_target_property(TARGET_TYPE ${TARGET} TYPE)

    set_property(GLOBAL APPEND PROPERTY TARGET_${OTHER_TARGET}_MODULE_DEPENDENTS ${MODULE_NAME})
    # (Weak) dependency between object library and other target
    if(TARGET_TYPE STREQUAL OBJECT_LIBRARY)
        target_include_directories(${TARGET} PRIVATE $<TARGET_PROPERTY:${OTHER_TARGET},INTERFACE_INCLUDE_DIRECTORIES>)
        return()
    endif()

    if(TARGET_TYPE STREQUAL INTERFACE_LIBRARY)
        set(VISIBILITY INTERFACE)
    else()
        set(VISIBILITY PUBLIC)
    endif()

    target_link_libraries(${TARGET} ${VISIBILITY} ${OTHER_TARGET})
endfunction()

function(vespa_add_target_external_dependency TARGET LIB)
    get_target_property(TARGET_TYPE ${TARGET} TYPE)

    # TODO: Use generator expressions in target_link_libraries
    if(TARGET_TYPE STREQUAL OBJECT_LIBRARY)
        return()
    elseif(TARGET_TYPE STREQUAL INTERFACE_LIBRARY)
        set(VISIBILITY INTERFACE)
    else()
        set(VISIBILITY PUBLIC)
    endif()

    target_link_libraries(${TARGET} ${VISIBILITY} ${LIB})
endfunction()

function(vespa_add_package_dependency PACKAGE_NAME)
    find_package(${PACKAGE_NAME} REQUIRED)
    string(TOUPPER ${PACKAGE_NAME} PACKAGE_NAME)
    string(REPLACE "-" "_" PACKAGE_NAME ${PACKAGE_NAME})
    set(PACKAGE_INCLUDE_DIR ${${PACKAGE_NAME}_INCLUDE_DIR})
    set(PACKAGE_LIBRARIES ${${PACKAGE_NAME}_LIBRARIES})
    link_libraries(${PACKAGE_LIBRARIES})
    include_directories(${PACKAGE_INCLUDE_DIR})
endfunction()

# TODO: Merge this function into add_target_system_dependency
function(vespa_add_target_package_dependency TARGET PACKAGE_NAME)
    find_package(${PACKAGE_NAME} REQUIRED)
    string(TOUPPER ${PACKAGE_NAME} PACKAGE_NAME)
    string(REPLACE "-" "_" PACKAGE_NAME ${PACKAGE_NAME})
    set(PACKAGE_INCLUDE_DIR ${${PACKAGE_NAME}_INCLUDE_DIR})
    set(PACKAGE_LIBRARIES ${${PACKAGE_NAME}_LIBRARIES})
    target_link_libraries(${TARGET} PUBLIC ${PACKAGE_LIBRARIES})
    target_include_directories(${TARGET} PUBLIC ${PACKAGE_INCLUDE_DIR})
endfunction()

function(vespa_add_target_system_dependency TARGET PACKAGE_NAME)
    get_target_property(TARGET_TYPE ${TARGET} TYPE)

    if(TARGET_TYPE STREQUAL INTERFACE_LIBRARY)
        set(VISIBILITY INTERFACE)
    else()
        set(VISIBILITY PUBLIC)
    endif()

    if(TARGET_TYPE STREQUAL OBJECT_LIBRARY)
        return()
    endif()

    if(TARGET_TYPE STREQUAL INTERFACE_LIBRARY)
        return()
    endif()

    # Hacks <3
    if(${PACKAGE_NAME} STREQUAL llvm)
        set(PACKAGE_NAME LLVM)
    endif()

    # If third (optional) parameter is STATIC, link with static library.
    # Library name specified by last optional parameter
    if (ARGV2 STREQUAL "STATIC")
        target_link_libraries(${TARGET} PUBLIC ${PACKAGE_DIR}/lib64/lib${ARGV3}.a)
        # If third (optional) parameter is something else, link with shared library with that name
    elseif (ARGV2)
        target_link_libraries(${TARGET} PUBLIC ${ARGV2})
    else()
        target_link_libraries(${TARGET} PUBLIC ${PACKAGE_NAME})
    endif()
endfunction()

function(vespa_add_source_target TARGET)
    cmake_parse_arguments(
        ARG
        ""
        ""
        "DEPENDS"
        ${ARGN}
    )
    add_custom_target(${TARGET} DEPENDS ${ARG_DEPENDS})
    __add_source_target_to_module(${TARGET})
endfunction()

function(vespa_generate_config TARGET RELATIVE_CONFIG_DEF_PATH)
    # Generate config-<name>.cpp and config-<name>.h from <name>.def
    # Destination directory is always where generate_config is called (or, in the case of out-of-source builds, in the build-tree parallel)
    # This may not be the same directory as where the .def file is located.

    # Third parameter lets the user change <name> for the generated files
    if (ARGC GREATER 2)
        set(CONFIG_NAME ${ARGV2})
    else()
        get_filename_component(CONFIG_NAME ${RELATIVE_CONFIG_DEF_PATH} NAME_WE)
    endif()

    # Make config path an absolute_path
    set(CONFIG_DEF_PATH ${CMAKE_CURRENT_LIST_DIR}/${RELATIVE_CONFIG_DEF_PATH})

    # Config destination is the current source directory (or parallel in build tree)
    # configgen.jar takes the destination dirname as a property parameter
    set(CONFIG_DEST_DIR ${CMAKE_CURRENT_BINARY_DIR})

    # Get destination dirname
    get_filename_component(CONFIG_DEST_DIRNAME ${CMAKE_CURRENT_BINARY_DIR} NAME)

    set(CONFIG_H_PATH ${CONFIG_DEST_DIR}/config-${CONFIG_NAME}.h)
    set(CONFIG_CPP_PATH ${CONFIG_DEST_DIR}/config-${CONFIG_NAME}.cpp)

    add_custom_command(
        OUTPUT ${CONFIG_H_PATH} ${CONFIG_CPP_PATH}
        COMMAND java -Dconfig.spec=${CONFIG_DEF_PATH} -Dconfig.dest=${CONFIG_DEST_DIR} -Dconfig.lang=cpp -Dconfig.dumpTree=false -Xms64m -Xmx64m -jar ${PROJECT_SOURCE_DIR}/configgen/target/configgen.jar
        WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}
        COMMENT "Generating cpp config for ${CONFIG_NAME} in ${CMAKE_CURRENT_SOURCE_DIR}"
        MAIN_DEPENDENCY ${CONFIG_DEF_PATH}
        )

    if (TARGET ${TARGET}_object)
        # Generated config is in implicit object library
        set(TARGET "${TARGET}_object")
    endif()
    # Add generated to sources for target
    target_sources(${TARGET} PRIVATE ${CONFIG_H_PATH} ${CONFIG_CPP_PATH})

    # Needed to be able to do a #include <config-<name>.h> for this target
    # This is used within some unit tests
    target_include_directories(${TARGET} PRIVATE ${CONFIG_DEST_DIR})
    vespa_add_source_target("configgen_${TARGET}_${CONFIG_NAME}" DEPENDS ${CONFIG_H_PATH} ${CONFIG_CPP_PATH})
endfunction()

macro(__split_sources_list)
    unset(SOURCE_FILES)
    unset(NON_TARGET_SOURCE_FILES)
    unset(TARGET_SOURCE_FILES)
    if(ARG_SOURCES)
        set(SOURCE_FILES ${ARG_SOURCES})
        set(TARGET_SOURCE_FILES ${ARG_SOURCES})
        set(NON_TARGET_SOURCE_FILES ${ARG_SOURCES})
        list(FILTER TARGET_SOURCE_FILES INCLUDE REGEX "TARGET_OBJECTS:")
        list(FILTER NON_TARGET_SOURCE_FILES EXCLUDE REGEX "TARGET_OBJECTS:")
    endif()
endmacro()

function(vespa_add_library TARGET)
    cmake_parse_arguments(ARG
        "STATIC;OBJECT;INTERFACE;TEST;ALLOW_UNRESOLVED_SYMBOLS"
        "INSTALL;OUTPUT_NAME"
        "DEPENDS;EXTERNAL_DEPENDS;AFTER;SOURCES"
        ${ARGN})

    __check_target_parameters()
    __split_sources_list()
    if(NOT ARG_SOURCES)
        # In the case where no source files are given, we include an empty source file to suppress a warning from CMake
        # This way, config-only libraries will not generate lots of build warnings
        set(SOURCE_FILES "${CMAKE_SOURCE_DIR}/empty.cpp")
        set(NON_TARGET_SOURCE_FILES ${SOURCE_FILES})
    endif()

    if(ARG_OBJECT)
        set(LIBRARY_TYPE OBJECT)
        __add_object_target_to_module(${TARGET})
    elseif(ARG_STATIC)
        set(LINKAGE STATIC)
    elseif(ARG_INTERFACE)
        set(LIBRARY_TYPE INTERFACE)
        set(SOURCE_FILES)
    endif()

    if (ARG_OBJECT OR ARG_INTERFACE OR TARGET ${TARGET}_object OR NOT NON_TARGET_SOURCE_FILES)
        unset(VESPA_ADD_IMPLICIT_OBJECT_LIBRARY)
        add_library(${TARGET} ${LINKAGE} ${LIBRARY_TYPE} ${SOURCE_FILES})
    else()
        set(VESPA_ADD_IMPLICIT_OBJECT_LIBRARY True)
        add_library(${TARGET} ${LINKAGE} ${LIBRARY_TYPE} $<TARGET_OBJECTS:${TARGET}_object> ${TARGET_SOURCE_FILES})
    endif()
    __add_dependencies_to_target()

    __handle_test_targets()

    if(ARG_INSTALL)
        install(TARGETS ${TARGET} DESTINATION ${ARG_INSTALL})
        __install_header_files()
    endif()

    if(ARG_OUTPUT_NAME)
        set_target_properties(${TARGET} PROPERTIES OUTPUT_NAME ${ARG_OUTPUT_NAME})
    endif()

    if(NOT ARG_OBJECT AND NOT ARG_STATIC AND NOT ARG_INTERFACE AND NOT ARG_ALLOW_UNRESOLVED_SYMBOLS AND DEFINED VESPA_DISALLOW_UNRESOLVED_SYMBOLS_IN_SHARED_LIBRARIES)
        target_link_options(${TARGET} PRIVATE ${VESPA_DISALLOW_UNRESOLVED_SYMBOLS_IN_SHARED_LIBRARIES})
    endif()

    __add_target_to_module(${TARGET})
    __export_include_directories(${TARGET})
    if(VESPA_ADD_IMPLICIT_OBJECT_LIBRARY)
      unset(VESPA_ADD_IMPLICIT_OBJECT_LIBRARY)
      vespa_add_library(${TARGET}_object OBJECT SOURCES ${NON_TARGET_SOURCE_FILES} DEPENDS ${ARG_DEPENDS})
      add_dependencies(${TARGET} ${TARGET}_object)
    endif()
endfunction()

function(__install_header_files)
    # Currently all header files are installed as they are not explicitly listed for each library. The
    # proper way would be for each module to list its header files to install.

    # Only install header for targets in */src/vespa/*.
    string(REPLACE "/" ";" PATH_COMPONENTS ${CMAKE_CURRENT_SOURCE_DIR})
    list(REVERSE PATH_COMPONENTS)
    list(GET PATH_COMPONENTS 1 SECOND_ELEMENT)
    list(GET PATH_COMPONENTS 2 THIRD_ELEMENT)
    if (${SECOND_ELEMENT} STREQUAL "vespa" AND ${THIRD_ELEMENT} STREQUAL "src")
        # Preserve the name */src/vespa/<name> as not every module has <name>=module name (e.g. vespalog)
        get_filename_component(RELATIVE_TO ${CMAKE_CURRENT_SOURCE_DIR} DIRECTORY)
        file(GLOB_RECURSE HEADERS RELATIVE ${RELATIVE_TO} "*.h" "*.hpp")
        foreach(HEADER ${HEADERS})
            get_filename_component(RELDIR ${HEADER} DIRECTORY)
            install(FILES ${RELATIVE_TO}/${HEADER} DESTINATION include/vespa/${RELDIR})
        endforeach()
   endif()   
endfunction()

function(vespa_add_executable TARGET)
    cmake_parse_arguments(ARG
        "TEST"
        "INSTALL;OUTPUT_NAME"
        "DEPENDS;EXTERNAL_DEPENDS;AFTER;SOURCES"
        ${ARGN})

    __check_target_parameters()
    __split_sources_list()
    if(TARGET ${TARGET}_object OR NOT NON_TARGET_SOURCE_FILES)
        unset(VESPA_ADD_IMPLICIT_OBJECT_LIBRARY)
        add_executable(${TARGET} ${ARG_SOURCES})
    else()
        set(VESPA_ADD_IMPLICIT_OBJECT_LIBRARY True)
        add_executable(${TARGET} $<TARGET_OBJECTS:${TARGET}_object> ${TARGET_SOURCE_FILES})
    endif()
    __add_dependencies_to_target()

    __handle_test_targets()

    if(ARG_INSTALL)
        install(TARGETS ${TARGET} DESTINATION ${ARG_INSTALL})
    endif()

    if(ARG_OUTPUT_NAME)
        set_target_properties(${TARGET} PROPERTIES OUTPUT_NAME ${ARG_OUTPUT_NAME})
    endif()

    __add_target_to_module(${TARGET})
    __export_include_directories(${TARGET})
    if(VESPA_ADD_IMPLICIT_OBJECT_LIBRARY)
        unset(VESPA_ADD_IMPLICIT_OBJECT_LIBRARY)
        vespa_add_library(${TARGET}_object OBJECT SOURCES ${NON_TARGET_SOURCE_FILES} DEPENDS ${ARG_DEPENDS})
        add_dependencies(${TARGET} ${TARGET}_object)
    endif()
endfunction()

macro(vespa_define_module)
    cmake_parse_arguments(ARG
        ""
        ""
        "DEPENDS;EXTERNAL_DEPENDS;LIBS;LIB_DEPENDS;LIB_EXTERNAL_DEPENDS;APPS;APP_DEPENDS;APP_EXTERNAL_DEPENDS;TESTS;TEST_DEPENDS;TEST_EXTERNAL_DEPENDS"
        ${ARGN})

    __initialize_module()

    # Base target dependencies for whole module
    foreach(DEPENDEE IN LISTS ARG_DEPENDS)
        set(BASE_DEPENDS ${BASE_DEPENDS} ${DEPENDEE})
    endforeach()

    # External library dependencies for whole module
    foreach(DEPENDEE IN LISTS ARG_EXTERNAL_DEPENDS)
        set(BASE_EXTERNAL_DEPENDS ${BASE_EXTERNAL_DEPENDS} ${DEPENDEE})
    endforeach()

    # Dependencies for libraries
    set(MODULE_DEPENDS ${BASE_DEPENDS})
    foreach(DEPENDEE IN LISTS ARG_LIB_DEPENDS)
        set(MODULE_DEPENDS ${MODULE_DEPENDS} ${DEPENDEE})
    endforeach()

    # External library dependencies for libraries
    set(MODULE_EXTERNAL_DEPENDS ${BASE_EXTERNAL_DEPENDS})
    foreach(DEPENDEE IN LISTS ARG_LIB_EXTERNAL_DEPENDS)
        set(MODULE_EXTERNAL_DEPENDS ${MODULE_EXTERNAL_DEPENDS} ${DEPENDEE})
    endforeach()

    # Add libraries
    foreach(DIR IN LISTS ARG_LIBS)
        add_subdirectory(${DIR})
    endforeach()

    # Dependencies for apps
    set(MODULE_DEPENDS ${BASE_DEPENDS})
    foreach(DEPENDEE IN LISTS ARG_APP_DEPENDS)
        set(MODULE_DEPENDS ${MODULE_DEPENDS} ${DEPENDEE})
    endforeach()

    # External library dependencies for apps
    set(MODULE_EXTERNAL_DEPENDS ${BASE_EXTERNAL_DEPENDS})
    foreach(DEPENDEE IN LISTS ARG_APP_EXTERNAL_DEPENDS)
        set(MODULE_EXTERNAL_DEPENDS ${MODULE_EXTERNAL_DEPENDS} ${DEPENDEE})
    endforeach()

    # Add apps
    foreach(DIR IN LISTS ARG_APPS)
        add_subdirectory(${DIR})
    endforeach()

    # Dependencies for tests
    set(MODULE_DEPENDS ${BASE_DEPENDS})
    foreach(DEPENDEE IN LISTS ARG_TEST_DEPENDS)
        set(MODULE_DEPENDS ${MODULE_DEPENDS} ${DEPENDEE})
    endforeach()

    # External library dependencies for tests
    set(MODULE_EXTERNAL_DEPENDS ${BASE_EXTERNAL_DEPENDS})
    foreach(DEPENDEE IN LISTS ARG_TEST_EXTERNAL_DEPENDS)
        set(MODULE_EXTERNAL_DEPENDS ${MODULE_EXTERNAL_DEPENDS} ${DEPENDEE})
    endforeach()

    # Add tests
    foreach(DIR IN LISTS ARG_TESTS)
        add_subdirectory(${DIR})
    endforeach()
endmacro()

function(__is_script_extension COMMAND RESULT_VAR)
    set(SCRIPT_EXTENSIONS ".sh" ".bash" ".py" ".pl" ".rb")

    get_filename_component(COMMAND_EXT ${COMMAND} EXT)
    if(COMMAND_EXT)
        list(FIND SCRIPT_EXTENSIONS ${COMMAND_EXT} RESULT)
        if(NOT RESULT EQUAL -1)
            set(${RESULT_VAR} TRUE PARENT_SCOPE)
            return()
        endif()
    endif()

    set(${RESULT_VAR} FALSE PARENT_SCOPE)
endfunction()

function(__is_command_a_script COMMAND RESULT_VAR)
    list(GET COMMAND 0 FIRST)
    __is_script_extension(${FIRST} IS_SCRIPT_EXT)

    if (IS_SCRIPT_EXT)
        set(THE_SCRIPT ${FIRST})
    endif()
    list(LENGTH COMMAND COMMAND_LENGTH)
    if(COMMAND_LENGTH GREATER 1 AND NOT IS_SCRIPT_EXT)
        list(GET COMMAND 1 SECOND)
        __is_script_extension(${SECOND} IS_SCRIPT_EXT)
        if (IS_SCRIPT_EXT)
            set(THE_SCRIPT ${SECOND})
        endif()
    endif()

    set(${RESULT_VAR} ${IS_SCRIPT_EXT} PARENT_SCOPE)

    if(THE_SCRIPT AND ARGV2)
        set(${ARGV2} ${THE_SCRIPT} PARENT_SCOPE)
    endif()
endfunction()

function(vespa_add_test)
    cmake_parse_arguments(ARG "NO_VALGRIND;RUN_SERIAL;BENCHMARK" "NAME;WORKING_DIRECTORY;COST" "COMMAND;DEPENDS;ENVIRONMENT" ${ARGN})

    if(NOT RUN_BENCHMARKS AND ARG_BENCHMARK)
        return()
    endif()

    __is_command_a_script("${ARG_COMMAND}" IS_SCRIPT THE_SCRIPT)
    if(IS_SCRIPT)
        list(APPEND TEST_DEPENDENCIES ${THE_SCRIPT})
    else()
        list(GET ARG_COMMAND 0 COMMAND_FIRST)
        if(TARGET ${COMMAND_FIRST})
            list(APPEND TEST_DEPENDENCIES ${COMMAND_FIRST})
        endif()
    endif()
    if(ARG_DEPENDS)
        list(APPEND TEST_DEPENDENCIES ${ARG_DEPENDS})
    endif()

    list(LENGTH TEST_DEPENDENCIES TEST_DEPENDENCIES_LENGTH)
    if(${TEST_DEPENDENCIES_LENGTH} EQUAL 0)
        message(FATAL_ERROR "Test does not have any dependencies. It's not allowed if the command is neither a target nor a script.")
    endif()

    if(VALGRIND_UNIT_TESTS AND NOT ARG_NO_VALGRIND)
        if(NOT VALGRIND_EXECUTABLE)
            message(FATAL_ERROR "Requested valgrind tests, but could not find valgrind executable.")
        endif()
        if(VESPA_USE_SANITIZER)
            message(FATAL_ERROR "Cannot run sanitizer-instrumented unit tests under Valgrind")
        endif()

        if(IS_SCRIPT)
            # For shell scripts, export a VALGRIND environment variable
            list(APPEND ARG_ENVIRONMENT "VALGRIND=${VALGRIND_COMMAND}")
        else()
            # For targets or other executables, prepend valgrind to the command.
            # Extract first part of command and expand the executable path if it refers to a executable target
            set(COMMAND_REST ${ARG_COMMAND})
            list(GET COMMAND_REST 0 COMMAND_FIRST)
            list(REMOVE_AT COMMAND_REST 0)

            if (TARGET ${COMMAND_FIRST})
                set(COMMAND_FIRST $<TARGET_FILE:${COMMAND_FIRST}>)
            endif()
            set(ARG_COMMAND "${VALGRIND_COMMAND} ${COMMAND_FIRST} ${COMMAND_REST}")
        endif()
    endif()
    if(VESPA_USE_SANITIZER AND VESPA_SANITIZER_ENV)
        list(APPEND ARG_ENVIRONMENT "${VESPA_SANITIZER_ENV}")
    endif()

    separate_arguments(ARG_COMMAND)
    add_test(NAME ${ARG_NAME} COMMAND ${ARG_COMMAND} WORKING_DIRECTORY ${ARG_WORKING_DIRECTORY})

    list(APPEND ARG_ENVIRONMENT "SOURCE_DIRECTORY=${CMAKE_CURRENT_SOURCE_DIR}")
    set_tests_properties(${ARG_NAME} PROPERTIES ENVIRONMENT "${ARG_ENVIRONMENT}")

    if (ARG_COST)
        set_tests_properties(${ARG_NAME} PROPERTIES COST ${ARG_COST})
    endif()
    
    if(ARG_RUN_SERIAL)
        set_tests_properties(${ARG_NAME} PROPERTIES RUN_SERIAL TRUE)
    endif()

    if (AUTORUN_UNIT_TESTS)
        set(CONTROL_FILE "${ARG_NAME}.run")
        add_custom_target("${CONTROL_FILE}_target" ALL DEPENDS ${CONTROL_FILE})
        add_custom_command(OUTPUT ${CONTROL_FILE}
                COMMAND ${CMAKE_CTEST_COMMAND} --output-on-failure -R ${ARG_NAME} || (rm -f ${CONTROL_FILE} && false)
                COMMAND touch ${CONTROL_FILE}
                DEPENDS ${TEST_DEPENDENCIES}
                COMMENT "Executing ${ARG_COMMAND}")
    endif()
endfunction()

function(vespa_install_script)
    if(ARGC GREATER 2)
        install(FILES ${ARGV0} RENAME ${ARGV1} PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE DESTINATION ${ARGV2})
    else()
        install(FILES ${ARGV0} PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE GROUP_READ GROUP_EXECUTE WORLD_READ WORLD_EXECUTE DESTINATION ${ARGV1})
    endif()
endfunction()

function(vespa_install_data)
    if(ARGC GREATER 2)
        install(FILES ${ARGV0} RENAME ${ARGV1} PERMISSIONS OWNER_READ OWNER_WRITE GROUP_READ WORLD_READ DESTINATION ${ARGV2})
    else()
        install(FILES ${ARGV0} PERMISSIONS OWNER_READ OWNER_WRITE GROUP_READ WORLD_READ DESTINATION ${ARGV1})
    endif()
endfunction()

function(vespa_workaround_gcc_bug_67055 SOURCE_FILE)
    if(CMAKE_COMPILER_IS_GNUCC)
        execute_process(COMMAND ${CMAKE_CPP_COMPILER} -dumpversion OUTPUT_VARIABLE GCC_VERSION)
        if (GCC_VERSION VERSION_LESS "5.3.0")
            set_source_files_properties(${SOURCE_FILE} PROPERTIES COMPILE_FLAGS -fno-ipa-icf)
        endif()
    endif()
endfunction()

macro(__initialize_module)
    # Set a couple of useful variables for this module
    set(MODULE_ROOT ${CMAKE_CURRENT_BINARY_DIR})
    get_filename_component(MODULE_NAME ${MODULE_ROOT} NAME)

    get_property(VESPA_MODULES GLOBAL PROPERTY VESPA_MODULES)
    set(VESPA_MODULES ${VESPA_MODULES} ${MODULE_NAME})
    set_property(GLOBAL PROPERTY VESPA_MODULES ${VESPA_MODULES})

    # Add "src" to the module's include directories so it's possible to use nonrelative includes
    # This include path is later exported to other targets that depend on targets within this module
    include_directories(${CMAKE_CURRENT_SOURCE_DIR}/src)

    # For generated files that are placed in the build tree (e.g. config-<name>.h)
    include_directories(${CMAKE_CURRENT_BINARY_DIR}/src)
endmacro()

macro(__check_target_parameters)
    if(ARG_UNPARSED_ARGUMENTS)
        if(NOT ARG_SOURCES)
            set(AUX_MESSAGE "\nMaybe you forgot to add the SOURCES parameter?")
        endif()
        message(FATAL_ERROR "Unrecognized parameters: ${ARG_UNPARSED_ARGUMENTS}.${AUX_MESSAGE}")
    endif()
endmacro()

macro(__add_dependencies_to_target)
    # Link with other targets or libraries
    foreach(DEPENDEE IN LISTS ARG_DEPENDS)
        vespa_add_target_dependency(${TARGET} ${DEPENDEE})
    endforeach()

    # Link with other targets defined as module dependencies
    foreach(DEPENDEE IN LISTS MODULE_DEPENDS)
        vespa_add_target_dependency(${TARGET} ${DEPENDEE})
    endforeach()

    # Link with other external libraries defined as external dependencies
    foreach(DEPENDEE IN LISTS ARG_EXTERNAL_DEPENDS)
        vespa_add_target_external_dependency(${TARGET} ${DEPENDEE})
    endforeach()

    # Link with other external libraries defined as module external dependencies
    foreach(DEPENDEE IN LISTS MODULE_EXTERNAL_DEPENDS)
        vespa_add_target_external_dependency(${TARGET} ${DEPENDEE})
    endforeach()

    # Build after these targets
    foreach(OTHER_TARGET IN LISTS ARG_AFTER)
        add_dependencies(${TARGET} ${OTHER_TARGET})
        target_include_directories(${TARGET} PRIVATE $<TARGET_PROPERTY:${OTHER_TARGET},INTERFACE_INCLUDE_DIRECTORIES>)
    endforeach()
endmacro()

function(__add_target_to_module TARGET)
    set_property(GLOBAL APPEND PROPERTY MODULE_${MODULE_NAME}_TARGETS ${TARGET})
endfunction()

function(__add_test_target_to_module TARGET)
    set_property(GLOBAL APPEND PROPERTY MODULE_${MODULE_NAME}_TEST_TARGETS ${TARGET})
endfunction()

function(__add_source_target_to_module TARGET)
    set_property(GLOBAL APPEND PROPERTY MODULE_${MODULE_NAME}_SOURCE_TARGETS ${TARGET})
endfunction()

function(__add_object_target_to_module TARGET)
    set_property(GLOBAL APPEND PROPERTY MODULE_${MODULE_NAME}_OBJECT_TARGETS ${TARGET})
endfunction()

macro(__handle_test_targets)
    # If this is a test executable, add it to the test target for this module
    # If building of unit tests is not specified, exclude this target from the all target
    if(ARG_TEST)
        __add_test_target_to_module(${TARGET})

        if(EXCLUDE_TESTS_FROM_ALL)
            set_target_properties(${TARGET} PROPERTIES EXCLUDE_FROM_ALL TRUE)
        endif()
    endif()
endmacro()

function(__create_module_targets PROPERTY_POSTFIX TARGET_POSTFIX)
    get_property(VESPA_MODULES GLOBAL PROPERTY VESPA_MODULES)
    set(OUTPUT_ALL_TARGET "all_${TARGET_POSTFIX}s")
    add_custom_target(${OUTPUT_ALL_TARGET})

    foreach(MODULE IN LISTS VESPA_MODULES)
        get_property(TARGETS GLOBAL PROPERTY MODULE_${MODULE}_${PROPERTY_POSTFIX})
        set(OUTPUT_TARGET "${MODULE}+${TARGET_POSTFIX}")
        add_custom_target(${OUTPUT_TARGET})

        foreach(TARGET IN LISTS TARGETS)
            add_dependencies(${OUTPUT_TARGET} ${TARGET})
        endforeach()

        add_dependencies(${OUTPUT_ALL_TARGET} ${OUTPUT_TARGET})
    endforeach()
endfunction()

function(__create_module_source_dependencies)
    get_property(VESPA_MODULES GLOBAL PROPERTY VESPA_MODULES)
    foreach(MODULE IN LISTS VESPA_MODULES)
        get_property(TARGETS GLOBAL PROPERTY MODULE_${MODULE}_TARGETS)
        get_property(TEST_TARGETS GLOBAL PROPERTY MODULE_${MODULE}_TEST_TARGETS)
        list(APPEND TARGETS ${TEST_TARGETS})
        if(TARGETS)
            list(REMOVE_DUPLICATES TARGETS)
            set(SOURCE_TARGET "${MODULE}+source")

            unset(MODULE_DEPENDENTS)
            foreach(TARGET IN LISTS TARGETS)
                get_property(TARGET_MODULE_DEPENDENTS GLOBAL PROPERTY TARGET_${TARGET}_MODULE_DEPENDENTS)
                list(APPEND MODULE_DEPENDENTS ${TARGET_MODULE_DEPENDENTS})
                add_dependencies(${TARGET} ${SOURCE_TARGET})
            endforeach()
            if (MODULE_DEPENDENTS)
                list(REMOVE_DUPLICATES MODULE_DEPENDENTS)
                foreach(MODULE_DEPENDENT IN LISTS MODULE_DEPENDENTS)
                    add_dependencies(${MODULE_DEPENDENT}+source ${SOURCE_TARGET})
                endforeach()
            endif()
        endif()
    endforeach()
endfunction()

function(__export_include_directories TARGET)
    get_directory_property(LOCAL_INCLUDE_DIRS INCLUDE_DIRECTORIES)
    get_target_property(TARGET_TYPE ${TARGET} TYPE)
    if(TARGET_TYPE STREQUAL INTERFACE_LIBRARY)
        target_include_directories(${TARGET} INTERFACE ${LOCAL_INCLUDE_DIRS})
    else()
        target_include_directories(${TARGET} PUBLIC ${LOCAL_INCLUDE_DIRS})
    endif()
endfunction()

function(install_config_definition)
    if(ARGC GREATER 1)
        install(FILES ${ARGV0} RENAME ${ARGV1} DESTINATION  share/vespa/configdefinitions)
    else()
        install(FILES ${ARGV0} DESTINATION  share/vespa/configdefinitions)
    endif()
endfunction()

function(install_config_definitions)
    if(ARGC EQUAL 0)
        set(DEFINITIONS_DIR src/main/resources/configdefinitions)
    else()
        set(DEFINITIONS_DIR ${ARGV0})
    endif()
    install(DIRECTORY ${DEFINITIONS_DIR}/ DESTINATION share/vespa/configdefinitions FILES_MATCHING PATTERN "*.def")
endfunction()

function(install_jar FILENAME)
    install(FILES "target/${FILENAME}" DESTINATION lib/jars/)
endfunction()

function(install_jar_dependencies NAME)
    install(DIRECTORY "target/dependency/" DESTINATION lib/jars FILES_MATCHING PATTERN "*.jar")
endfunction()

function(checked_execute_process_at_install)
    list(JOIN ARGV " " my_process_to_execute)
    install(CODE "execute_process(RESULT_VARIABLE my_cmd_exit_status COMMAND ${my_process_to_execute})
                  if (NOT my_cmd_exit_status EQUAL \"0\")
                      message(FATAL_ERROR \"failed: ${my_process_to_execute}\")
                  endif()")
endfunction(checked_execute_process_at_install)

function(install_absolute_symlink TARGET LINK)
    get_filename_component(target_directory ${LINK} DIRECTORY)
    install(DIRECTORY DESTINATION ${target_directory} MESSAGE_NEVER)
    checked_execute_process_at_install(ln -snf ${TARGET} \$ENV{DESTDIR}${CMAKE_INSTALL_PREFIX}/${LINK})
    install(CODE "message(\"-- Installing: ${LINK} -> ${TARGET})\")")
endfunction(install_absolute_symlink)

function(install_symlink TARGET LINK)
    install_absolute_symlink(${CMAKE_INSTALL_PREFIX}/${TARGET} ${LINK})
endfunction(install_symlink)

function(install_configserver_component NAME)
    cmake_parse_arguments(
        PARAM
        ""
        "CLASSIFIER"
        ""
        ${ARGN}
    )
    if(NOT PARAM_CLASSIFIER)
       SET(PARAM_CLASSIFIER "jar-with-dependencies")
    endif()
    install_jar("${NAME}-${PARAM_CLASSIFIER}.jar")
    install_app_component_symlink(configserver-app "${NAME}" CLASSIFIER "${PARAM_CLASSIFIER}")
endfunction()

function(install_app_component_symlink APPNAME NAME)
    cmake_parse_arguments(
        PARAM
        ""
        "CLASSIFIER"
        ""
        ${ARGN}
    )
    if(NOT PARAM_CLASSIFIER)
       SET(PARAM_CLASSIFIER "jar-with-dependencies")
    endif()
    install(DIRECTORY DESTINATION conf/${APPNAME}/components)
    install_symlink(lib/jars/${NAME}-${PARAM_CLASSIFIER}.jar conf/${APPNAME}/components/${NAME}.jar)
endfunction()

function(add_extra_projects)
    if(EXTRA_PROJECTS)
        foreach(PROJECT ${EXTRA_PROJECTS})
            get_filename_component(RELDIR ${PROJECT} NAME)
            add_subdirectory(${PROJECT} ${RELDIR})
        endforeach()
    endif()
endfunction()

function(vespa_detect_build_platform)
  if(EXISTS /etc/os-release)
    file(STRINGS /etc/os-release OS_DISTRO REGEX "^ID=")
    string(REGEX REPLACE "ID=\"?([^\"]+)\"?" "\\1" OS_DISTRO ${OS_DISTRO})
    file(STRINGS /etc/os-release OS_DISTRO_VERSION REGEX "^VERSION_ID=")
    if(OS_DISTRO_VERSION)
      string(REGEX REPLACE "VERSION_ID=\"?([^\"]+)\"?" "\\1" OS_DISTRO_VERSION ${OS_DISTRO_VERSION})
    else()
      if (OS_DISTRO STREQUAL "debian")
        set(OS_DISTRO_VERSION "sid")
      else()
        message(FATAL_ERROR "-- Could not determine ${OS_DISTRO} version")
      endif()
    endif()
    file(STRINGS /etc/os-release OS_DISTRO_NAME REGEX "^NAME=")
    if (OS_DISTRO_NAME)
      string(REGEX REPLACE "NAME=\"?([^\"]+)\"?" "\\1" OS_DISTRO_NAME ${OS_DISTRO_NAME})
    endif()
  elseif(EXISTS /etc/redhat-release)
    set(OS_DISTRO "rhel")
    file(STRINGS "/etc/redhat-release" OS_DISTRO_VERSION)
    string(REGEX REPLACE ".* release ([0-9.]+) .*" "\\1" OS_DISTRO_VERSION ${OS_DISTRO_VERSION})
  elseif(APPLE)
    set(OS_DISTRO "darwin")
    set(OS_DISTRO_VERSION ${CMAKE_SYSTEM_VERSION})
  endif()
  if(OS_DISTRO)
    set(VESPA_OS_DISTRO ${OS_DISTRO} PARENT_SCOPE)
    set(VESPA_OS_DISTRO_VERSION ${OS_DISTRO_VERSION} PARENT_SCOPE)
    string(CONCAT OS_DISTRO_COMBINED ${OS_DISTRO} " " ${OS_DISTRO_VERSION})
    set(VESPA_OS_DISTRO_COMBINED ${OS_DISTRO_COMBINED} PARENT_SCOPE)
    if (OS_DISTRO_NAME)
      set(VESPA_OS_DISTRO_NAME ${OS_DISTRO_NAME} PARENT_SCOPE)
    endif()
  else()
    message(FATAL_ERROR "-- Could not determine vespa build platform")
  endif()
endfunction()

function(vespa_suppress_warnings_for_protobuf_sources)
    cmake_parse_arguments(
        ARG
        ""
        ""
        "SOURCES"
        ${ARGN}
    )
  # protoc-generated files emit compiler warnings that we normally treat as errors.
  # Instead of rolling our own compiler plugin we'll pragmatically disable the noise.
  if (NOT "${CMAKE_CXX_COMPILER_ID}" STREQUAL "Clang" AND NOT "${CMAKE_CXX_COMPILER_ID}" STREQUAL "AppleClang")
    if(Protobuf_VERSION VERSION_LESS "3.7.0")
      set(VESPA_DISABLE_UNUSED_WARNING "-Wno-unused-parameter")
    else()
      unset(VESPA_DISABLE_UNUSED_WARNING)
    endif()
    set_source_files_properties(${ARG_SOURCES} PROPERTIES COMPILE_FLAGS "-Wno-array-bounds -Wno-suggest-override -Wno-inline ${VESPA_DISABLE_UNUSED_WARNING}")
  endif()
endfunction()
