package app.server;

import app.enums.Role;
import app.exceptions.ApiException;
import app.services.MessageService;
import app.security.AccessService;
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

    // ________________________________________________________

    public Setup(EntityManager em, int port) {
        this.em = em;
        this.accessService = new AccessService();
        this.messageService = new MessageService();

        this.app = Javalin.create(Setup::configuration)
                .beforeMatched(accessService::accessHandler)
                .start(port);
    }

    // ________________________________________________________

    public static void configuration(JavalinConfig config) {
        config.showJavalinBanner = false;
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


        app.unsafeConfig().router.apiBuilder(Routing.registerRoutes());
    }

    // ________________________________________________________

    // Should it ever end? It has been added to end the session!
    public void endSession() {
        em.close();
        if (app != null) app.stop();
    }
}
