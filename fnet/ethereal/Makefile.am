# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# Makefile.am
# Automake file for plugin

INCLUDES = -I$(top_srcdir)

plugindir = @plugindir@

plugin_LTLIBRARIES = fnetrpc.la
fnetrpc_la_SOURCES = packet-fnetrpc.c moduleinfo.h
fnetrpc_la_LDFLAGS = -module -avoid-version
fnetrpc_la_LIBADD = @PLUGIN_LIBS@

# Libs must be cleared, or else libtool won't create a shared module.
# If your module needs to be linked against any particular libraries,
# add them here.
LIBS =

CLEANFILES = \
	fnetrpc \
	*~

MAINTAINERCLEANFILES = \
	Makefile.in

EXTRA_DIST = \
	Makefile.nmake
