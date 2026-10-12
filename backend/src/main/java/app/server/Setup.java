package app.server;

import app.enums.Role;
import app.exceptions.ApiException;
import app.services.MessageService;
import app.security.AccessService;
import app.controllers.FileController;
import app.daos.FileDAO;
import app.services.FileService;
import app.services.FileValidator;
import app.services.LocalFileStorage;
import java.time.Clock;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import jakarta.persistence.EntityManager;
import lombok.Getter;


@Getter
public class Setup {

    public static EntityManager em;
    private final Javalin app;
    private final AccessService accessService;
    private final MessageService messageService;
    private final FileService fileService;

    // ________________________________________________________

    public Setup(EntityManager em, int port) {
        this.em = em;
        this.accessService = new AccessService();
        this.messageService = new MessageService();
        this.fileService = new FileService(new FileDAO(em), LocalFileStorage.fromEnvironment(),
                new FileValidator(), Clock.systemUTC());

        this.app = Javalin.create(config -> {
                    configuration(config);
                    config.events.serverStopping(fileService::close);
                })
                .beforeMatched(accessService::accessHandler)
                .start(port);
    }

    // ________________________________________________________

    public static void configuration(JavalinConfig config) {
        config.showJavalinBanner = false;
        FileController.configureMultipart(config);
        config.bundledPlugins.enableRouteOverview("/routes", Role.ANYONE);



        config.bundledPlugins.enableCors(cors ->
                cors.addRule(rule -> {
                    rule.anyHost();
                })
        );
        config.bundledPlugins.enableHttpAllowedMethodsOnRoutes();
        //TODO: Add more configs maybe???
    }

    // ________________________________________________________

    public void initialize(){

        app.exception(ApiException.class, (e, ctx) -> {
            System.out.println(e.getMessage());
            System.out.println(e.getStatus());
            ctx.result(e.getMessage()).status(e.getStatus());
        });

        // Gives me error message
        app.exception(Exception.class, (e, ctx) -> ctx.status(500).result(e.getMessage()));


        app.unsafeConfig().router.apiBuilder(Routing.registerRoutes(fileService));
        fileService.startCleanup(em.getEntityManagerFactory());
    }

    // ________________________________________________________

    // Should it ever end? It has been added to end the session!
    public void endSession() {
        if (app != null) app.stop();
        fileService.close();
        em.close();
    }
}
