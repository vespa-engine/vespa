// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporarySDField;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author geirst
 */
public class VsmFieldsTestCase {

    @Test
    public void reference_type_field_is_unsearchable() {
        Search search = new Search("test", MockApplicationPackage.createEmpty(), new MockFileRegistry(), new TestableDeployLogger(), new TestProperties());
        search.addDocument(new SDDocumentType("test"));
        SDField refField = new TemporarySDField("ref_field", ReferenceDataType.createWithInferredId(TemporaryStructuredDataType.create("parent_type")));
        refField.parseIndexingScript("{ summary }");
        search.getDocument().addField(refField);

        VsmFields vsmFields = new VsmFields(search);
        VsmfieldsConfig.Builder cfgBuilder = new VsmfieldsConfig.Builder();
        vsmFields.getConfig(cfgBuilder);
        VsmfieldsConfig cfg = cfgBuilder.build();

        assertEquals(1, cfg.fieldspec().size());
        VsmfieldsConfig.Fieldspec fieldSpec = cfg.fieldspec().get(0);
        assertEquals("ref_field", fieldSpec.name());
        assertEquals(VsmfieldsConfig.Fieldspec.Searchmethod.NONE, fieldSpec.searchmethod());
    }
}
