package com.liferay.portal.osgi.web.wab.generator.internal.helper;

import java.time.Duration;
import java.time.LocalDateTime;

public enum TimerHelper {

    timer;

    private final LocalDateTime globalStart = LocalDateTime.now();

    public void time(final String name) {
        this.time(name, () -> {});
    }

    public <E extends Exception> void time(final String name, final ThrowingRunnable<E> throwingRunnable) {
        System.out.println("Processing " + name);
        final LocalDateTime start = LocalDateTime.now();

        try {
            throwingRunnable.run();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        final LocalDateTime end = LocalDateTime.now();

        final Duration intermediary = Duration.between(start, end);
        final Duration total = Duration.between(globalStart, end);

        System.out.println(String.format("Took %s (total: %s)", intermediary, total));
    }

    @FunctionalInterface
    public interface ThrowingRunnable<E extends Exception> {
        void run() throws E;
    }
}
