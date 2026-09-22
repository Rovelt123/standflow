package app;

import app.configs.HibernateConfig;
import app.server.Setup;
import app.utils.Utils;

public class Main {


    public static Setup setup;
    private static final int port = Integer.parseInt(Utils.getPropertyValue("PORT", "config.properties"));

    // ________________________________________________________

    public static void main(String[] args) {
        setup = new Setup(HibernateConfig.getEntityManagerFactory().createEntityManager(), port);

        setup.initialize();

        //setup.endSession();
    }
}
