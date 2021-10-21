# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import os
import sys
import platform
import distutils.sysconfig
from setuptools import setup, Extension
from setuptools.command.build_ext import build_ext

class PreBuiltExt(build_ext):
    def build_extension(self, ext):
        print("Using prebuilt extension library")
        libdir="lib.%s-%s-%s" % (sys.platform, platform.machine(), distutils.sysconfig.get_python_version())
        os.system("mkdir -p build/%s" % libdir)
        os.system("cp -p vespa_ann_benchmark.*.so build/%s" % libdir)

setup(
  name="vespa_ann_benchmark",
  version="0.1.0",
  author="Tor Egge",
  author_email="Tor.Egge@yahooinc.com",
  description="Python binding for the Vespa implementation of an HNSW index for nearest neighbor search",
  long_description="Python binding for the Vespa implementation of an HNSW index for nearest neighbor search used for low-level benchmarking",
  ext_modules=[Extension("vespa_ann_benchmark", sources=[])],
  cmdclass={"build_ext": PreBuiltExt},
  zip_safe=False,
)
