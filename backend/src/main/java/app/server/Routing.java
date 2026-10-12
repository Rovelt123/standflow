package app.server;

import app.controllers.ApplicationController;
import app.controllers.TemplateController;
import app.controllers.UserController;
import app.controllers.MessageController;
import app.controllers.NewsletterController;
import app.controllers.VendorController;
import app.controllers.FileController;
import app.services.FileService;
import io.javalin.apibuilder.EndpointGroup;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Routing {

    public static EndpointGroup registerRoutes(FileService files) {
        return () -> {
            path("/api", () -> {
                TemplateController.registerRoutes().addEndpoints();
                UserController.registerRoutes().addEndpoints();
                ApplicationController.registerRoutes().addEndpoints();
                MessageController.registerRoutes().addEndpoints();
                FileController.registerRoutes(files).addEndpoints();
                NewsletterController.registerRoutes().addEndpoints();
                VendorController.registerRoutes().addEndpoints();
                get("/health", ctx -> ctx.result("Health OK"));
            });
        };
    }
}
