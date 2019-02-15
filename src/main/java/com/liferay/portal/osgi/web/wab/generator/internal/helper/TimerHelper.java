package com.liferay.portal.osgi.web.wab.generator.internal.helper;

import java.time.Duration;
import java.time.LocalDateTime;

public class TimerHelper {

    private final LocalDateTime globalStart;

    public TimerHelper() {
        globalStart = LocalDateTime.now();
    }

    public void time(final String name, final Runnable method) {
        System.out.println("Processing " + name);
        final LocalDateTime start = LocalDateTime.now();

        method.run();

        final LocalDateTime end = LocalDateTime.now();

        final Duration intermediary = Duration.between(start, end);
        final Duration total = Duration.between(globalStart, end);

        System.out.println(String.format("Took %s (total: %s)", intermediary, total));
    }
}
