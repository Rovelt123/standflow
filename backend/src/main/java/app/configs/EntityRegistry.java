package app.configs;


import app.entities.*;
import org.hibernate.cfg.Configuration;

final class EntityRegistry {

    private EntityRegistry() {}

    static void registerEntities(Configuration configuration) {
        configuration.addAnnotatedClass(User.class);
        configuration.addAnnotatedClass(Application.class);
        configuration.addAnnotatedClass(Message.class);
        configuration.addAnnotatedClass(Template.class);
        configuration.addAnnotatedClass(UnsubscribeToken.class);
    }
}
