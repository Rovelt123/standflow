package app.configs;

import app.utils.Utils;
import jakarta.persistence.EntityManagerFactory;

import java.util.Properties;

public final class HibernateConfig {

    private static volatile EntityManagerFactory emf;

    private HibernateConfig() {}

    public static EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            synchronized (HibernateConfig.class) {
                if (emf == null) {
                    emf = HibernateEmfBuilder.build(buildProps());
                }
            }
        }
        return emf;
    }

    private static Properties buildProps() {
        Properties props = HibernateBaseProperties.createBase();

        // Teaching-friendly default - change to update in production
        props.put("hibernate.hbm2ddl.auto", "update");

        if (Boolean.parseBoolean(Utils.getPropertyValue("DEPLOYED", "config.properties"))) {
            setDeployedProperties(props);
        } else {
            setDevProperties(props);
        }
        return props;
    }

    private static void setDeployedProperties(Properties props) {
        String dbName = Utils.getPropertyValue("DB_NAME", "config.properties");
        props.setProperty("hibernate.connection.url", Utils.getPropertyValue("CONNECTION_STR", "config.properties") + dbName);
        props.setProperty("hibernate.connection.username", Utils.getPropertyValue("DB_USERNAME", "config.properties"));
        props.setProperty("hibernate.connection.password", Utils.getPropertyValue("DB_PASSWORD", "config.properties"));
    }

    private static void setDevProperties(Properties props) {
        String dbName = Utils.getPropertyValue("DB_NAME", "config.properties");
        String username = Utils.getPropertyValue("DB_USERNAME", "config.properties");
        String password = Utils.getPropertyValue("DB_PASSWORD", "config.properties");

        props.put("hibernate.connection.url", "jdbc:postgresql://localhost:5432/" + dbName);
        props.put("hibernate.connection.username", username);
        props.put("hibernate.connection.password", password);
    }
}
