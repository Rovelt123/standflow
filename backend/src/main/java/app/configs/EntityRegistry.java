package app.configs;


import app.entities.*;
import org.hibernate.cfg.Configuration;

final class EntityRegistry {

    private EntityRegistry() {}

    static void registerEntities(Configuration configuration) {
        //configuration.addAnnotatedClass(User.class);
    }
}
