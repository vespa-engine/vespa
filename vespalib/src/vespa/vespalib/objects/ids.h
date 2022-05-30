// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

/*
 * These are the namespaces of ids used by identifiable classes.
 * Should correspond to actual C++ namespace names.
 */

#define DOCUMENT_CID(v)      (0x1000 + v)
#define STORAGEAPI_CID(v)    (0x2000 + v)
#define STORAGECLIENT_CID(v) (0x3000 + v)
#define SEARCHLIB_CID(v)     (0x4000 + v)
#define VESPALIB_CID(v)      (0x5000 + v)
#define CONFIGD_CID(v)       (0x6000 + v)
#define VESPA_CONFIGMODEL_CID(v)  (0x7000 + v)

/*
 * Here are all the ids in the vespalib namespace:
 */

#define CID_vespalib_NamedObject                   VESPALIB_CID(9)

