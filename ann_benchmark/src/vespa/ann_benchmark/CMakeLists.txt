# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(DIRECTORY DESTINATION libexec/vespa_ann_benchmark)

vespa_add_library(vespa_ann_benchmark
  ALLOW_UNRESOLVED_SYMBOLS
  SOURCES
  vespa_ann_benchmark.cpp

  INSTALL libexec/vespa_ann_benchmark
  DEPENDS
  pybind11::pybind11
)

if (TARGET pybind11::lto)
  target_link_libraries(vespa_ann_benchmark PRIVATE pybind11::module pybind11::lto)
else()
  target_link_libraries(vespa_ann_benchmark PRIVATE pybind11::module)
endif()

if (COMMAND pybind11_extension)
  pybind11_extension(vespa_ann_benchmark)
else()
  set_target_properties(vespa_ann_benchmark PROPERTIES PREFIX "${PYTHON_MODULE_PREFIX}")
  set_target_properties(vespa_ann_benchmark PROPERTIES SUFFIX "${PYTHON_MODULE_EXTENSION}")
endif()

set_target_properties(vespa_ann_benchmark PROPERTIES CXX_VISIBILITY_PRESET "hidden")

configure_file(setup.py.in setup.py @ONLY)

vespa_install_script(setup.py libexec/vespa_ann_benchmark)
