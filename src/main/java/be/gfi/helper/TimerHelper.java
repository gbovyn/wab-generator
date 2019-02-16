package be.gfi.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;

public enum TimerHelper {

    timer;

    private static final Logger LOG = LoggerFactory.getLogger(TimerHelper.class);

    private static final LocalDateTime GLOBAL_START = LocalDateTime.now();

    public void time(final String name) {
        this.time(name, () -> {});
    }

    public <E extends Exception> void time(final String name, final ThrowingRunnable<E> throwingRunnable) {
        LOG.info("Processing {}", name);
        final LocalDateTime start = LocalDateTime.now();

        try {
            throwingRunnable.run();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        final LocalDateTime end = LocalDateTime.now();

        final Duration intermediary = Duration.between(start, end);
        final Duration total = Duration.between(GLOBAL_START, end);

        LOG.info("Took {} (total: {})", intermediary, total);
    }

    public void printTotal() {
        final LocalDateTime now = LocalDateTime.now();
        final Duration total = Duration.between(GLOBAL_START, now);

        LOG.info("Total: {} minutes {} seconds", total.toMinutes(), total.getSeconds() % 60);
    }

    @FunctionalInterface
    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }
}
