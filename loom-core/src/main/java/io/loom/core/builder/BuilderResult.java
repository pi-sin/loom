package io.loom.core.builder;

public record BuilderResult<T>(
    T value,
    Throwable error,
    boolean timedOut
) {
    public static <T> BuilderResult<T> success(T value) {
        return new BuilderResult<>(value, null, false);
    }

    public static <T> BuilderResult<T> failure(Throwable error) {
        return new BuilderResult<>(null, error, false);
    }

    public static <T> BuilderResult<T> timeout() {
        return new BuilderResult<>(null, null, true);
    }

    public boolean isSuccess() {
        return value != null && error == null && !timedOut;
    }

    public boolean isFailure() {
        return error != null;
    }
}
