package app.server;

import app.controllers.TemplateController;
import io.javalin.apibuilder.EndpointGroup;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Routing {

    public static EndpointGroup registerRoutes() {
        return () -> {
            path("/api", () -> {
                TemplateController.registerRoutes().addEndpoints();

                get("/health", ctx -> ctx.result("Health OK"));
            });
        };
    }
}
