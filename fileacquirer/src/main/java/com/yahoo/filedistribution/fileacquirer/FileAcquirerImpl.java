// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.filedistribution.fileacquirer;

import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.FileReference;
import com.yahoo.jrt.*;
import com.yahoo.log.LogLevel;

import java.util.logging.Logger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.io.File;

/**
 * Retrieves the path to a file or directory on the local file system
 * that has been transferred with the vespa file distribution
 * mechanism.
 *
 * Intended to be the only real implementation of FileAcquirer.
 *
 * @author Tony Vaagenes
 */
class FileAcquirerImpl implements FileAcquirer {
    static final class FileDistributionErrorCode {
        public static final int baseErrorCode = 0x10000;
        public static final int baseFileProviderErrorCode = baseErrorCode + 0x1000;

        public static final int fileReferenceDoesNotExists = baseFileProviderErrorCode;
        public static final int fileReferenceRemoved = fileReferenceDoesNotExists + 1;
    }

    private static final Logger log = Logger.getLogger(FileAcquirerImpl.class.getName());

    private final Supervisor supervisor = new Supervisor(new Transport());
    private final ConfigSubscriber configSubscriber;

    private class Connection implements ConfigSubscriber.SingleSubscriber<FiledistributorrpcConfig> {
        private final Lock targetLock = new ReentrantLock();
        private Target target;

        private volatile Spec spec;
        private long pauseTime = 0; //milliseconds

        private long nextLogTime = 0;
        private long logCount = 0;

        private void connect(Timer timer) throws InterruptedException {
            while (timer.isTimeLeft()) {
                pause();
                target = supervisor.connectSync(spec);
                if (target.isValid()) {
                    log.log(LogLevel.DEBUG, "Successfully connected to '" + spec + "', this = " + System.identityHashCode(this));
                    pauseTime = 0;
                    logCount = 0;
                    return;
                } else {
                    logWarning();
                }
            }
        }

        private void pause() throws InterruptedException {
            if (pauseTime > 0) {
                Thread.sleep(pauseTime);
                pauseTime = Math.min((long)(pauseTime*1.5), TimeUnit.MINUTES.toMillis(1));
            } else {
                pauseTime = 500;
            }
        }

        private void logWarning() {
            if (logCount == 0 || System.currentTimeMillis() > nextLogTime ) {
                log.warning("Could not connect to the config proxy '" + spec.toString() + "'" + " - " + this + "@" + System.identityHashCode(this));

                nextLogTime = System.currentTimeMillis() +
                        Math.min(TimeUnit.DAYS.toMillis(1),
                                TimeUnit.SECONDS.toMillis(30) * (++logCount));
                log.info("Next log time = " + nextLogTime + ", current = " + System.currentTimeMillis());
            }
        }

        @Override
        public void configure(FiledistributorrpcConfig filedistributorrpcConfig) {
            spec = new Spec(filedistributorrpcConfig.connectionspec());
        }

        public Target getTarget(Timer timer) throws InterruptedException {
            TimeUnit unit = TimeUnit.MILLISECONDS;

            targetLock.tryLock(timer.timeLeft(unit) , unit );
            try {
                if (target == null || !target.isValid())
                    connect(timer);
                return target;
            } finally {
                targetLock.unlock();
            }
        }
    }

    private final Connection connection = new Connection();

    private boolean temporaryError(int errorCode) {
        switch (errorCode) {
        case ErrorCode.ABORT:
        case ErrorCode.CONNECTION:
        case ErrorCode.GENERAL_ERROR:
        case ErrorCode.OVERLOAD:
        case ErrorCode.TIMEOUT:
            return true;
        default:
            return false;
        }
    }

    public FileAcquirerImpl(String configId) {
        configSubscriber = new ConfigSubscriber();
        configSubscriber.subscribe(connection, FiledistributorrpcConfig.class, configId);
    }

    public void shutdown() {
        configSubscriber.close();
        supervisor.transport().shutdown().join();
    }


    /**
     * Returns the path to a file or directory corresponding to the
     * given file reference.  File references are produced by the
     * config system.
     *
     * @throws TimeoutException if the file or directory could not be
     *     retrieved in time.
     * @throws FileReferenceDoesNotExistException if the file is no
     *     longer available (due to reloading of config).
     */
    public File waitFor(FileReference fileReference, long timeout, TimeUnit timeUnit)
            throws InterruptedException {
        Timer timer = new Timer(timeout, timeUnit);
        do {
            Target target = connection.getTarget(timer);
            if (target == null)
                break;

            Request request = new Request("waitFor");
            request.parameters().add(new StringValue(fileReference.value()));

            double rpcTimeout = Math.min(timer.timeLeft(TimeUnit.SECONDS), 60.0);
            log.log(LogLevel.DEBUG, "InvokeSync waitFor " + fileReference + " with " + rpcTimeout + " seconds timeout");
            target.invokeSync(request, rpcTimeout);

            if (request.checkReturnTypes("s")) {
                return new File(request.returnValues().get(0).asString());
            } else if (!request.isError()) {
                throw new RuntimeException("Invalid response: " + request.returnValues());
            } else if (temporaryError(request.errorCode())) {
                log.log(LogLevel.INFO, "Retrying waitFor for " + fileReference + ": " + request.errorCode() + " -- " + request.errorMessage());
                Thread.sleep(1000);
            } else {
                if (request.errorCode() == FileDistributionErrorCode.fileReferenceDoesNotExists)
                    throw new FileReferenceDoesNotExistException(fileReference.value());
                else if (request.errorCode() == FileDistributionErrorCode.fileReferenceRemoved)
                    throw new FileReferenceRemovedException(fileReference.value());
                else
                    throw new RuntimeException("Wait for " + fileReference + " failed:" + request.errorMessage() + " (" + request.errorCode() + ")");
            }
        } while ( timer.isTimeLeft() );
        throw new TimeoutException("Timed out waiting for " + fileReference + " after " + timeout + " " + timeUnit.name().toLowerCase());
    }

}
