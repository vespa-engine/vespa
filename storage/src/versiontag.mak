# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

VTAG := $(shell $(VESPALIB_DIR)/bin/getversion -D $(TOP) )

ifneq (X$(SPECIFIED_VTAG),XDISABLE)
    VTAG += -DV_TAG='"$(SPECIFIED_VTAG)"'
endif
