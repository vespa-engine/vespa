-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
-- REGISTER vespa-hadoop.jar  -- Not needed in tests

-- Create valid Vespa put operations
DEFINE VespaPutOperation
       com.yahoo.vespa.hadoop.pig.VespaDocumentOperation(
            'operation=put',
            'docid=id:<application>:metrics::<name>-<date>'
       );

-- By default, VespaStorage assumes it's feeding valid Vespa operations
DEFINE VespaStorage
       com.yahoo.vespa.hadoop.pig.VespaStorage();

-- Load tabular data
metrics = LOAD 'src/test/resources/tabular_data.csv' AS (date:chararray, name:chararray, value:int, application:chararray);

-- Transform tabular data to a Vespa document operation JSON format
metrics = FOREACH metrics GENERATE VespaPutOperation(*);

-- Store into Vespa
STORE metrics INTO '$ENDPOINT' USING VespaStorage();


