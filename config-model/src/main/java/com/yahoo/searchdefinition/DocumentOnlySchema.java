// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.searchdefinition.document.SDDocumentType;

/**
 * A search that was derived from an sd file containing no search element(s), only
 * document specifications, so the name of this is decided by parsing and adding the document instance.
 *
 * @author vegardh
 */
public class DocumentOnlySchema extends Schema {

    public DocumentOnlySchema(Application application, FileRegistry fileRegistry, DeployLogger deployLogger, ModelContext.Properties properties) {
        super(application, fileRegistry, deployLogger, properties);
    }

    @Override
    public void addDocument(SDDocumentType docType) {
        if (getName() == null) {
            setName(docType.getName());
        }
        super.addDocument(docType);
    }

}
