package com.rtsbuilding.rtsbuilding.server.pipeline.placement;

public final class PlaceResult {

    public static final int CODE_SUCCESS = 0;
    public static final int CODE_FAILED = 1;
    public static final int CODE_SKIPPED = 2;

    public final int code;
    public final String reason;

    private PlaceResult(int code, String reason) {
        this.code = code;
        this.reason = reason != null ? reason : "";
    }

    public static PlaceResult success() {
        return new PlaceResult(CODE_SUCCESS, "");
    }

    public static PlaceResult failed(String reason) {
        return new PlaceResult(CODE_FAILED, reason);
    }

    public static PlaceResult skipped() {
        return new PlaceResult(CODE_SKIPPED, "");
    }

    public boolean isSuccess() {
        return code == CODE_SUCCESS;
    }
}
