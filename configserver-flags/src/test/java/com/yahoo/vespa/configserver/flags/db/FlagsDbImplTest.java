// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.db;

import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.JsonNodeRawFlag;
import com.yahoo.vespa.flags.json.Condition;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.flags.json.Rule;
import com.yahoo.vespa.flags.json.WhitelistCondition;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 */
public class FlagsDbImplTest {
    @Test
    public void test() {
        MockCurator curator = new MockCurator();
        FlagsDbImpl db = new FlagsDbImpl(curator);

        var params = new Condition.CreateParams(FetchVector.Dimension.HOSTNAME).withValues("host1");
        Condition condition1 = WhitelistCondition.create(params);
        Rule rule1 = new Rule(Optional.of(JsonNodeRawFlag.fromJson("13")), condition1);
        FlagId flagId = new FlagId("id");
        FlagData data = new FlagData(flagId, new FetchVector().with(FetchVector.Dimension.ZONE_ID, "zone-a"), rule1);
        db.setValue(flagId, data);

        Optional<FlagData> dataCopy = db.getValue(flagId);
        assertTrue(dataCopy.isPresent());

        assertEquals("{\"id\":\"id\",\"rules\":[{\"conditions\":[{\"type\":\"whitelist\",\"dimension\":\"hostname\"," +
                "\"values\":[\"host1\"]}],\"value\":13}],\"attributes\":{\"zone\":\"zone-a\"}}",
                dataCopy.get().serializeToJson());

        FlagId flagId2 = new FlagId("id2");
        FlagData data2 = new FlagData(flagId2, new FetchVector().with(FetchVector.Dimension.ZONE_ID, "zone-a"), rule1);
        db.setValue(flagId2, data2);
        Map<FlagId, FlagData> flags = db.getAllFlagData();
        assertThat(flags.size(), equalTo(2));
        assertThat(flags.get(flagId), notNullValue());
        assertThat(flags.get(flagId2), notNullValue());

        db.removeValue(flagId2);
        assertFalse(db.getValue(flagId2).isPresent());
    }
}