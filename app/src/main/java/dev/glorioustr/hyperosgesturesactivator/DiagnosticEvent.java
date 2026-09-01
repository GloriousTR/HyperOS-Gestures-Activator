package dev.glorioustr.hyperosgesturesactivator;

final class DiagnosticEvent {
    static final String STATUS_INFO = "INFO";
    static final String STATUS_SUCCESS = "SUCCESS";
    static final String STATUS_FAILURE = "FAILURE";

    final long id;
    final long timestamp;
    final String status;
    final String category;
    final String operation;
    final String detail;
    final String processName;
    final String threadName;

    DiagnosticEvent(
            long id,
            long timestamp,
            String status,
            String category,
            String operation,
            String detail,
            String processName,
            String threadName) {
        this.id = id;
        this.timestamp = timestamp;
        this.status = status;
        this.category = category;
        this.operation = operation;
        this.detail = detail;
        this.processName = processName;
        this.threadName = threadName;
    }
}
