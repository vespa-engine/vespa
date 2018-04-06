// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.content.utils.ApplicationPackageBuilder;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.DocType;
import com.yahoo.vespa.model.content.utils.SearchDefinitionBuilder;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.assertEqualActions;
import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.normalizeServicesInActions;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class StreamingSearchClusterChangeValidatorTest {

    private static class Fixture {
        VespaModel currentModel;
        VespaModel nextModel;
        StreamingSearchClusterChangeValidator validator;

        public Fixture(VespaModel currentModel, VespaModel nextModel) {
            this.currentModel = currentModel;
            this.nextModel = nextModel;
            validator = new StreamingSearchClusterChangeValidator();
        }

        public static Fixture withOneDocType(String currentSd, String nextSd) {
            return new Fixture(createOneDocModel(currentSd), createOneDocModel(nextSd));
        }

        public static VespaModel createOneDocModel(String sdContent) {
            return new ApplicationPackageBuilder()
                    .addCluster(new ContentClusterBuilder().name("foo").docTypes(Arrays.asList(DocType.streaming("d1"))))
                    .addSearchDefinition(new SearchDefinitionBuilder().name("d1").content(sdContent).build())
                    .buildCreator().create();
        }

        public static Fixture withTwoDocTypes(String currentSd, String nextSd) {
            return new Fixture(createTwoDocModel(currentSd, currentSd), createTwoDocModel(nextSd, nextSd));
        }

        public static VespaModel createTwoDocModel(String d1Content, String d2Content) {
            return new ApplicationPackageBuilder()
                    .addCluster(new ContentClusterBuilder().name("foo").docTypes(Arrays.asList(DocType.streaming("d1"), DocType.streaming("d2"))))
                    .addSearchDefinition(new SearchDefinitionBuilder().name("d1").content(d1Content).build())
                    .addSearchDefinition(new SearchDefinitionBuilder().name("d2").content(d2Content).build())
                    .buildCreator().create();
        }

        public static Fixture withTwoClusters(String currentSd, String nextSd) {
            return new Fixture(createTwoClusterModel(currentSd, currentSd), createTwoClusterModel(nextSd, nextSd));
        }

        public static VespaModel createTwoClusterModel(String d1Content, String d2Content) {
            return new ApplicationPackageBuilder()
                    .addCluster(new ContentClusterBuilder().name("foo").docTypes(Arrays.asList(DocType.streaming("d1"))))
                    .addCluster(new ContentClusterBuilder().name("bar").docTypes(Arrays.asList(DocType.streaming("d2"))))
                    .addSearchDefinition(new SearchDefinitionBuilder().name("d1").content(d1Content).build())
                    .addSearchDefinition(new SearchDefinitionBuilder().name("d2").content(d2Content).build())
                    .buildCreator().create();
        }

        public List<ConfigChangeAction> validate() {
            return normalizeServicesInActions(validator.validate(currentModel, nextModel,
                    ValidationOverrides.empty, Instant.now()));
        }

        public void assertValidation() {
            assertThat(validate().size(), is(0));
        }

        public void assertValidation(ConfigChangeAction exp) {
            assertValidation(Arrays.asList(exp));
        }

        public void assertValidation(List<ConfigChangeAction> exp) {
            assertEqualActions(exp, validate());
        }
    }

    private static String STRING_FIELD = "field f1 type string { indexing: summary }";
    private static String INT_FIELD = "field f1 type int { indexing: summary }";
    private static String ATTRIBUTE_INT_FIELD = "field f1 type int { indexing: attribute | summary }";
    private static String ATTRIBUTE_FAST_ACCESS_INT_FIELD = "field f1 type int { indexing: attribute | summary \n attribute: fast-access }";
    private static List<ServiceInfo> FOO_SERVICE = Arrays.asList(
            new ServiceInfo("searchnode", "null", null, null, "foo/search/0", "null"));
    private static List<ServiceInfo> BAR_SERVICE = Arrays.asList(
            new ServiceInfo("searchnode2", "null", null, null, "bar/search/0", "null"));

    @Test
    public void changing_field_type_requires_refeed() {
        Fixture.withOneDocType(STRING_FIELD, INT_FIELD)
                .assertValidation(createFieldTypeChangeRefeedAction("d1", FOO_SERVICE));
    }

    @Test
    public void changes_in_multiple_streaming_clusters_are_discovered() {
        Fixture.withTwoClusters(STRING_FIELD, INT_FIELD)
                .assertValidation(Arrays.asList(
                        createFieldTypeChangeRefeedAction("d1", FOO_SERVICE),
                        createFieldTypeChangeRefeedAction("d2", BAR_SERVICE)));
    }

    @Test
    public void changes_in_multiple_document_types_are_discovered() {
        Fixture.withTwoDocTypes(STRING_FIELD, INT_FIELD)
                .assertValidation(Arrays.asList(
                        createFieldTypeChangeRefeedAction("d1", FOO_SERVICE),
                        createFieldTypeChangeRefeedAction("d2", FOO_SERVICE)));
    }

    @Test
    public void adding_fast_access_to_an_attribute_requires_restart() {
        Fixture.withOneDocType(INT_FIELD, ATTRIBUTE_FAST_ACCESS_INT_FIELD)
                .assertValidation(createAddFastAccessRestartAction());

        Fixture.withOneDocType(ATTRIBUTE_INT_FIELD, ATTRIBUTE_FAST_ACCESS_INT_FIELD)
                .assertValidation(createAddFastAccessRestartAction());
    }

    @Test
    public void removing_fast_access_from_an_attribute_requires_restart() {
        Fixture.withOneDocType(ATTRIBUTE_FAST_ACCESS_INT_FIELD, INT_FIELD)
                .assertValidation(createRemoveFastAccessRestartAction());

        Fixture.withOneDocType(ATTRIBUTE_FAST_ACCESS_INT_FIELD, ATTRIBUTE_INT_FIELD)
                .assertValidation(createRemoveFastAccessRestartAction());
    }

    @Test
    public void adding_attribute_field_is_ok() {
        Fixture.withOneDocType(INT_FIELD, ATTRIBUTE_INT_FIELD).assertValidation();
    }

    @Test
    public void removing_attribute_field_is_ok() {
        Fixture.withOneDocType(ATTRIBUTE_INT_FIELD, INT_FIELD).assertValidation();
    }

    @Test
    public void unchanged_fast_access_attribute_field_is_ok() {
        Fixture.withOneDocType(ATTRIBUTE_FAST_ACCESS_INT_FIELD, ATTRIBUTE_FAST_ACCESS_INT_FIELD).assertValidation();
    }

    @Test
    public void adding_streaming_cluster_is_ok() {
        new Fixture(Fixture.createOneDocModel(STRING_FIELD), Fixture.createTwoClusterModel(STRING_FIELD, STRING_FIELD)).assertValidation();
    }

    @Test
    public void removing_streaming_cluster_is_ok() {
        new Fixture(Fixture.createTwoClusterModel(STRING_FIELD, STRING_FIELD), Fixture.createOneDocModel(STRING_FIELD)).assertValidation();
    }

    private static VespaConfigChangeAction createFieldTypeChangeRefeedAction(String docType, List<ServiceInfo> service) {
        return ConfigChangeTestUtils.newRefeedAction("field-type-change",
                ValidationOverrides.empty,
                "Document type '" + docType + "': Field 'f1' changed: data type: 'string' -> 'int'",
                service, docType, Instant.now());
    }

    private static VespaConfigChangeAction createAddFastAccessRestartAction() {
        return ConfigChangeTestUtils.newRestartAction("Document type 'd1': Field 'f1' changed: add fast-access attribute", FOO_SERVICE);
    }

    private static VespaConfigChangeAction createRemoveFastAccessRestartAction() {
        return ConfigChangeTestUtils.newRestartAction("Document type 'd1': Field 'f1' changed: remove fast-access attribute", FOO_SERVICE);
    }

}
