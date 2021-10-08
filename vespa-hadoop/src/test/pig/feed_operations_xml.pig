-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
-- REGISTER vespa-hadoop.jar  -- Not needed in tests

-- Define short name for VespaStorage
DEFINE VespaStorage com.yahoo.vespa.hadoop.pig.VespaStorage();

-- Load data - one column for xml data
data = LOAD 'src/test/resources/operations_data.xml' AS (data:chararray);

-- Store into Vespa
STORE data INTO '$ENDPOINT' USING VespaStorage();
