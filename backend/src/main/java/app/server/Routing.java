package app.server;

import app.controllers.ApplicationController;
import app.controllers.TemplateController;
import app.controllers.UserController;
import app.controllers.MessageController;
import app.controllers.NewsletterController;
import app.controllers.VendorController;
import io.javalin.apibuilder.EndpointGroup;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Routing {

    public static EndpointGroup registerRoutes() {
        return () -> {
            path("/api", () -> {
                TemplateController.registerRoutes().addEndpoints();
                UserController.registerRoutes().addEndpoints();
                ApplicationController.registerRoutes().addEndpoints();
                MessageController.registerRoutes().addEndpoints();
                NewsletterController.registerRoutes().addEndpoints();
                VendorController.registerRoutes().addEndpoints();
                get("/health", ctx -> ctx.result("Health OK"));
            });
        };
    }
}
