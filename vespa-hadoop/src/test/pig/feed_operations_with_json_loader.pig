-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
-- REGISTER vespa-hadoop.jar  -- Not needed in tests

-- Define short name for VespaJsonLoader
DEFINE VespaJsonLoader com.yahoo.vespa.hadoop.pig.VespaSimpleJsonLoader();

-- Define short name for VespaStorage
DEFINE VespaStorage com.yahoo.vespa.hadoop.pig.VespaStorage();

-- Load data - one column for json data
metrics = LOAD 'src/test/resources/operations_data.json' USING VespaJsonLoader() AS (data:chararray);

-- Store into Vespa
STORE metrics INTO '$ENDPOINT' USING VespaStorage();
