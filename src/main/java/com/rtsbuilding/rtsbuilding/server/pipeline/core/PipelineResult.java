package com.rtsbuilding.rtsbuilding.server.pipeline.core;

public abstract class PipelineResult {

    public static Success success() {
        return new Success();
    }

    public static Failure failure(String message) {
        return new Failure(message, null);
    }

    public static Failure failure(String message, Throwable cause) {
        return new Failure(message, cause);
    }

    public static Skip skip(String reason) {
        return new Skip(reason);
    }

    public static final class Success extends PipelineResult {
    }

    public static final class Failure extends PipelineResult {

        private final String message;
        private final Throwable cause;

        public Failure(String message, Throwable cause) {
            this.message = message;
            this.cause = cause;
        }

        public String message() {
            return message;
        }

        public Throwable cause() {
            return cause;
        }
    }

    public static final class Skip extends PipelineResult {

        private final String reason;

        public Skip(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
