.. pyvespa documentation master file, created by
   sphinx-quickstart on Wed Aug 26 11:11:55 2020.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.

Vespa python API
================

.. toctree::
   :hidden:

   install
   connect-to-vespa-instance
   reference-api

``pyvespa`` provides a python API to vespa.ai_. It allow us to create, modify, deploy and interact with
running Vespa instances. The main goal of the library is to allow for faster prototyping and to facilitate
Machine Learning experiments around Vespa applications.

.. _vespa.ai: https://vespa.ai/

Install
+++++++

You can install ``pyvespa`` via ``pip``:

.. code:: bash

	pip install pyvespa

Connecting to a running Vespa instance
++++++++++++++++++++++++++++++++++++++

If you have a running Vespa instance that you would like to experiment with, you connect to it with :ref:`vespa-class`.

.. code-block:: python

   from vespa.application import Vespa
   app = Vespa(url = "https://api.cord19.vespa.ai")



Indices and tables
==================

* :ref:`genindex`
* :ref:`modindex`
* :ref:`search`
