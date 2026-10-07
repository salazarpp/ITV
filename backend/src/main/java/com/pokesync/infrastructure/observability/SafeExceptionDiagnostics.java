package com.pokesync.infrastructure.observability;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class SafeExceptionDiagnostics {
    private SafeExceptionDiagnostics() {
    }

    /** Reports cause types and application locations without exception messages or data values. */
    public static String describe(Throwable failure) {
        StringBuilder result = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int causes = 0;
        while (failure != null && causes++ < 8 && visited.add(failure)) {
            if (!result.isEmpty()) result.append(" <- ");
            result.append(failure.getClass().getName());
            int frames = 0;
            for (StackTraceElement frame : failure.getStackTrace()) {
                if (frame.getClassName().startsWith("com.pokesync.") && frames++ < 8) {
                    result.append(" at ").append(frame.getClassName()).append('.').append(frame.getMethodName())
                            .append(':').append(frame.getLineNumber());
                }
            }
            failure = failure.getCause();
        }
        return result.toString();
    }
}
