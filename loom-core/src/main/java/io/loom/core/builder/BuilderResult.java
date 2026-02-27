package io.loom.core.builder;

public record BuilderResult<T>(
    T value,
    Throwable error,
    boolean timedOut,
    boolean success
) {
    public static <T> BuilderResult<T> success(T value) {
        return new BuilderResult<>(value, null, false, true);
    }

    public static <T> BuilderResult<T> failure(Throwable error) {
        return new BuilderResult<>(null, error, false, false);
    }

    public static <T> BuilderResult<T> timeout() {
        return new BuilderResult<>(null, null, true, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return error != null;
    }
}
