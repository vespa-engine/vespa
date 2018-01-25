// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//  Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int64Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.log.LogLevel;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

public class RpcTester {

    private static final Logger log = Logger.getLogger(RpcTester.class.getName());

    private final Target target;

    private RpcTester(Target target) {
        this.target = target;
    }

    private void call(String fileReference, String filename, byte[] blob) {
        new FileReceiver(target).receive(new FileReference(fileReference), filename, blob);
    }

    public static void main(String[] args) {
        //String fileReference = args[0];
        String fileReference = "59f93f445438c9db7ccbf1629f583c2aa004a68b";
        String filename = "com.yahoo.vespatest.ExtraHitSearcher-1.0.0-deploy.jar";
        File file = new File(String.format("/tmp/%s/%s", fileReference, filename));
        byte[] blob = null;

        try {
            blob = IOUtils.readFileBytes(file);
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.log(LogLevel.INFO, "Read blob from " + file.getAbsolutePath());


        Supervisor supervisor = new Supervisor(new Transport());

        Spec spec = new Spec("tcp/localhost:19090");
        log.log(LogLevel.INFO, "Connecting to " + spec);
        Target target = supervisor.connect(spec);
        if (! target.isValid()) {
            log.log(LogLevel.INFO, "Could not connect");
            System.exit(1);
        } else {
            log.log(LogLevel.INFO, "Connected to " + spec);
        }

        new RpcTester(target).call(fileReference, filename, blob);
    }

    class FileReceiver {

        Target target;

        FileReceiver(Target target) {
            this.target = target;
        }

        void receive(FileReference reference, String filename, byte[] content) {

            log.log(LogLevel.INFO, "Preparing receive call for " + reference.value() + " and file " + filename);

            XXHash64 hasher = XXHashFactory.fastestInstance().hash64();
            Request fileBlob = new Request("filedistribution.receiveFile");

            log.log(LogLevel.INFO, "Calling " + fileBlob.methodName() + " with target " + target);

            fileBlob.parameters().add(new StringValue(reference.value()));
            fileBlob.parameters().add(new StringValue(filename));
            fileBlob.parameters().add(new DataValue(content));
            fileBlob.parameters().add(new Int64Value(hasher.hash(ByteBuffer.wrap(content), 0)));
            fileBlob.parameters().add(new Int32Value(0));
            fileBlob.parameters().add(new StringValue("OK"));
            log.log(LogLevel.INFO, "Doing invokeSync");
            target.invokeSync(fileBlob, 5);
            log.log(LogLevel.INFO, "Done with invokeSync");
        }
    }
}
