package com.mmlinaric.remotemanager.app;

/** Application entry point. Startup work belongs to {@link ApplicationBootstrap}. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        new ApplicationBootstrap().launch();
    }
}
