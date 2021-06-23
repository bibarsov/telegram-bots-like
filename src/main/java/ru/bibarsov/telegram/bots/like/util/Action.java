package ru.bibarsov.telegram.bots.like.util;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public interface Action {

    Action IDENTITY = () -> {

    };

    static Action identity() {
        return IDENTITY;
    }

    void execute() throws Exception;

    static void executeQuietly(Action action) {
        try {
            action.execute();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable ignored) {
        }
    }
}
