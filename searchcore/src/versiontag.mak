# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
VTAG_DATE=$(shell date +%Y.%m.%d-%H.%M.%S)
VTAG_SYSTEM=$(shell uname -s)
VTAG_SYSTEM_REV=$(shell uname -r)
VTAG_BUILDER=$(shell (whoami) 2>/dev/null||logname)@$(shell uname -n)
ifneq (X$(SPECIFIED_VTAG),XDISABLE)
  ifeq (X$(UNAME), XWin32)
    VTAG=-DV_TAG='\"$(SPECIFIED_VTAG)\"'
  else
    VTAG=-DV_TAG='"$(SPECIFIED_VTAG)"'
  endif
else
  ifeq (X$(UNAME), XWin32)
    VTAG=
  else
    VTAG_TAG=$(shell cat $(TOP)/CVS/Tag 2>/dev/null | sed "s/^.//" 2>/dev/null)
    ifeq (X$(VTAG_TAG),X)
      VTAG_TAG=CURRENT
    endif
    ifeq ($(findstring _RELEASE, $(VTAG_TAG)),_RELEASE)
      VTAG_SYSTEM=$(shell uname -s)
      VTAG=-DV_TAG='"$(VTAG_TAG)-$(VTAG_SYSTEM)"'
    else
      VTAG_DATE=$(shell date +%Y.%m.%d-%H:%M:%S)
      VTAG_SYSTEM=$(shell (whoami) 2>/dev/null||logname)@$(shell uname -n)-$(shell uname -s)-$(shell uname -r)
      VTAG=-DV_TAG='"$(VTAG_TAG)-$(VTAG_SYSTEM)-$(VTAG_DATE)"'
    endif
  endif
endif
VTAG+= -DV_TAG_DATE='"$(VTAG_DATE)"'
VTAG+= -DV_TAG_SYSTEM='"$(VTAG_SYSTEM)"'
VTAG+= -DV_TAG_SYSTEM_REV='"$(VTAG_SYSTEM_REV)"'
VTAG+= -DV_TAG_BUILDER='"$(VTAG_BUILDER)"'

