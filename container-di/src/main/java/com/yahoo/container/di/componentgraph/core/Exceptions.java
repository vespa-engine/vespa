package com.yahoo.container.di.componentgraph.core;

import java.util.Arrays;

class Exceptions {
    static <E extends Throwable> E removeStackTrace(E exception) {
        if (preserveStackTrace()) {
            return exception;
        } else {
            exception.setStackTrace(new StackTraceElement[0]);
            return exception;
        }
    }

    static boolean preserveStackTrace() {
        String preserve = System.getProperty("jdisc.container.preserveStackTrace");
        return (preserve != null && !preserve.isEmpty());
    }

    static Throwable cutStackTraceAtConstructor(Throwable throwable, StackTraceElement marker) {
        if (throwable != null && !preserveStackTrace()) {
            StackTraceElement[] stackTrace = throwable.getStackTrace();
            int upTo = stackTrace.length - 1;

            // take until ComponentNode is reached
            while (upTo >= 0 && !stackTrace[upTo].getClassName().equals(ComponentNode.class.getName())) {
                upTo--;
            }

            // then drop until <init> is reached
            while (upTo >= 0 && !stackTrace[upTo].getMethodName().equals("<init>")) {
                upTo--;
            }
            if (upTo < 0) {
                throwable.setStackTrace(new StackTraceElement[0]);
            } else {
                throwable.setStackTrace(Arrays.copyOfRange(stackTrace, 0, upTo));
            }

            cutStackTraceAtConstructor(throwable.getCause(), marker);
        }
        return throwable;
    }

}
