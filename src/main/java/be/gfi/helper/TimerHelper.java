package be.gfi.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public enum TimerHelper {

    timer;

    private static final Logger LOG = LoggerFactory.getLogger(TimerHelper.class);

    private static final LocalDateTime GLOBAL_START = LocalDateTime.now();

    private static final Map<String, LocalDateTime> durations = new HashMap<>();

    public <E extends Exception> void time(final String name, final ThrowingRunnable<E> throwingRunnable) {
        final LocalDateTime start = LocalDateTime.now();

        try {
            throwingRunnable.run();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        final LocalDateTime end = LocalDateTime.now();

        final Duration intermediary = Duration.between(start, end);
        final Duration total = getTotal(end);

        printProcessed(name, intermediary, total);
    }

    public void start(final String name) {
        durations.put(name, LocalDateTime.now());
    }

    public void end(final String name) {
        final LocalDateTime start = durations.get(name);
        final LocalDateTime end = LocalDateTime.now();
        final Duration intermediary = Duration.between(start, end);
        final Duration total = getTotal(end);

        printProcessed(name, intermediary, total);
    }

    private Duration getTotal(final LocalDateTime end) {
        return Duration.between(GLOBAL_START, end);
    }

    private void printProcessed(final String name, final Duration intermediary, final Duration total) {
        LOG.info("Processed '{}' in [{}] (took {} on {})", name, getLocation(), intermediary, total);
    }

    public void printTotal() {
        final LocalDateTime now = LocalDateTime.now();
        final Duration total = getTotal(now);

        LOG.info("Total: {} minutes {} seconds", total.toMinutes(), total.getSeconds() % 60);
    }

    private String getLocation() {
        final StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        final StackTraceElement element = stackTraceElements[4];
        final String[] split = element.getClassName().split("\\.");
        final String className = split[split.length - 1];

        return String.format("%s.%s(...) - line: %s", className, element.getMethodName(), element.getLineNumber());
    }

    @FunctionalInterface
    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }
}
