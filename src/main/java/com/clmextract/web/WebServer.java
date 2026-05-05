package com.clmextract.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class WebServer {

    public static void start(String configPath, int port) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/static", Location.CLASSPATH);
        });

        app.get("/", ctx -> ctx.redirect("/index.html"));

        app.start(port);

        System.out.println("Web server started on port " + port);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
