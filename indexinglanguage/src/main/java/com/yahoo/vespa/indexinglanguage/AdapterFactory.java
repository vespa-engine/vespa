// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentUpdate;

import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public interface AdapterFactory {

    DocumentAdapter newDocumentAdapter(Document doc);

    List<UpdateAdapter> newUpdateAdapterList(DocumentUpdate upd);

}
