package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.config.FileReference;
import com.yahoo.io.IOUtils;
import com.yahoo.jrt.DataValue;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int64Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Target;
import net.jpountz.xxhash.XXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class FileServer {
    private static final Logger log = Logger.getLogger(FileServer.class.getName());
    private final String rootDir;
    private final ExecutorService executor;

    private String getPath(FileReference ref) {
        return rootDir + "/" + ref.value();
    }

    public FileServer(String rootDir) {
        this(rootDir, Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

    }

    public FileServer(String rootDir, ExecutorService executor) {
        this.rootDir = rootDir;
        this.executor = executor;
    }
    public boolean hasFile(String fileName) {
        return hasFile(new FileReference(fileName));
    }
    public boolean hasFile(FileReference ref) {
        return new File(getPath(ref)).exists();
    }
    public boolean startFileServing(String fileName, Target target) {
        File file = new File(getPath(new FileReference(fileName)));

        if (file.exists()) {
            executor.execute(() -> serveFile(fileName, target));
        }
        return false;
    }

    private void serveFile(String fileName, Target target) {
        Request fileBlob = new Request("filedistribution.receiveFile");
        File file = new File(getPath(new FileReference(fileName)));
        fileBlob.parameters().add(new StringValue(fileName));
        fileBlob.parameters().add(new StringValue(fileName));
        byte [] blob = new byte [0];
        boolean success = false;
        String errorDescription = "OK";
        try {
            blob = IOUtils.readFileBytes(file);
            success = true;
        } catch (IOException e) {
            errorDescription = "Failed reading file '" + file.getAbsolutePath() + "'";
            log.warning(errorDescription + "for sending to '" + target.toString() + "'.");
        }
        XXHash64 hasher = XXHashFactory.fastestInstance().hash64();
        fileBlob.parameters().add(new DataValue(blob));
        fileBlob.parameters().add(new Int64Value(hasher.hash(ByteBuffer.wrap(blob), 0)));
        fileBlob.parameters().add(new Int32Value(success ? 0 : 1));
        fileBlob.parameters().add(new StringValue(success ? "OK" : errorDescription));
        target.invokeSync(fileBlob, 600);
    }
}
