-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
-- REGISTER vespa-hadoop.jar  -- Not needed in tests

-- Transform tabular data to a Vespa document operation JSON format
-- as part of storing the data.
DEFINE VespaStorage
       com.yahoo.vespa.hadoop.pig.VespaStorage(
            'create-document-operation=true',
            'operation=put',
            'docid=id:<application>:metrics::<name>-<date>'
       );

-- Load tabular data
metrics = LOAD 'src/test/resources/tabular_data.csv' AS (date:chararray, name:chararray, value:int, application:chararray);

-- Store into Vespa
STORE metrics INTO '$ENDPOINT' USING VespaStorage();


