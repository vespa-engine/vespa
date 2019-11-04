// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTask;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.DeadlineExceededException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.InternalFailure;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.StateRestApiException;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.errors.UnknownMasterException;

public abstract class Request<Result> extends RemoteClusterControllerTask {

    public enum MasterState {
        MUST_BE_MASTER,
        NEED_NOT_BE_MASTER
    }

    // TODO a lot of this logic could be replaced with a CompleteableFuture

    private Exception failure = null;
    protected boolean resultSet = false;
    protected Result result = null;
    private final MasterState masterState;


    public Request(MasterState state) {
        this.masterState = state;
    }

    public Result getResult() throws StateRestApiException, OtherMasterIndexException {
        if (failure != null) {
            if (failure instanceof OtherMasterIndexException) {
                throw (OtherMasterIndexException) failure;
            } else {
                throw (StateRestApiException) failure;
            }
        }
        if (!isCompleted()) {
            throw new InternalFailure("Attempt to fetch result before it has been calculated");
        }
        if (!resultSet) {
            throw new InternalFailure("Expected result to be set at this point.");
        }
        return result;
    }

    @Override
    public final void doRemoteFleetControllerTask(Context context) {
        try{
            if (masterState == MasterState.MUST_BE_MASTER && !context.masterInfo.isMaster()) {
                Integer masterIndex = context.masterInfo.getMaster();
                if (masterIndex == null) throw new UnknownMasterException();
                throw new OtherMasterIndexException(masterIndex);
            }
            result = calculateResult(context);
            resultSet = true;
        } catch (OtherMasterIndexException | StateRestApiException e) {
            failure = e;
        } catch (Exception e) {
            failure = new InternalFailure("Caught unexpected exception");
            failure.initCause(e);
        }
    }

    private static String failureStringWithPossibleMessage(String prefix, String message) {
        if (message != null && !message.isEmpty()) {
            return String.format("%s: %s", prefix, message);
        }
        return prefix;
    }

    @Override
    public void handleFailure(Failure failure) {
        if (failure.getCondition() == FailureCondition.LEADERSHIP_LOST) {
            this.failure = new UnknownMasterException(failureStringWithPossibleMessage(
                    "Leadership lost before request could complete", failure.getMessage()));
        } else if (failure.getCondition() == FailureCondition.DEADLINE_EXCEEDED) {
            this.failure = new DeadlineExceededException(failureStringWithPossibleMessage(
                    "Task exceeded its version wait deadline", failure.getMessage()));
        }
    }

    @Override
    public boolean isFailed() {
        return (failure != null);
    }

    public abstract Result calculateResult(Context context) throws StateRestApiException, OtherMasterIndexException;

}
