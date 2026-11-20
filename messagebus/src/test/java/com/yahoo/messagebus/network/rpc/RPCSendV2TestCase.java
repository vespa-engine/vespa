// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;
import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.jrt.Values;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Inspector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RPCSendV2TestCase {

    @Test
    void a_reply_that_could_not_be_encoded_is_returned_with_its_error() throws Exception {
        Slobrok slobrok = new Slobrok();
        TestServer server = new TestServer("test", new RoutingTableSpec(SimpleProtocol.NAME), slobrok, null);
        try {
            // When the protocol fails to encode a reply, the payload is null and the reply has an error.
            SimpleReply reply = new SimpleReply("not encoded");
            reply.addError(new Error(ErrorCode.ENCODE_ERROR, "Protocol failed to encode reply."));
            Values returned = new Values();
            new RPCSendV2(server.net).createResponse(returned, reply, new Version(8), null);

            byte[] slimeBytes = new Compressor().decompress(returned.get(5).asData(),
                                                            CompressionType.valueOf(returned.get(3).asInt8()),
                                                            returned.get(4).asInt32());
            Inspector root = BinaryFormat.decode(slimeBytes).get();
            assertEquals(0, root.field("msg").asData().length);
            assertEquals(ErrorCode.ENCODE_ERROR, root.field("errors").entry(0).field("code").asLong());
        } finally {
            server.destroy();
            slobrok.stop();
        }
    }

}
