package com.github.jsafarik.ocp.monitoring.util;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Utils {

    public static void waitFor(Callable<Boolean> action, int seconds) throws TimeoutException {
        waitFor(action, seconds, 10);
    }

    public static void waitFor(Callable<Boolean> action, int seconds, int sleep) throws TimeoutException {
        long time = System.currentTimeMillis() + (seconds * 1000L);
        while (System.currentTimeMillis() < time) {
            try {
                if (action.call()) {
                    return;
                }
            } catch (Exception ex) {
                log.error("Supplied action threw exception");
            }

            try {
                Thread.sleep(sleep * 1000L);
            } catch (InterruptedException e) {
                log.error("Thread sleep interrupted: " + e.getMessage());
            }
        }

        throw new TimeoutException("Timed out waiting");
    }
}
