package app.configs;

import jakarta.persistence.EntityManagerFactory;

import java.util.Properties;

public class TestHibernateConfig {

    public static EntityManagerFactory getTestEmf() {
        Properties props = new Properties();

        props.put("hibernate.connection.driver_class", "org.h2.Driver");
        props.put("hibernate.connection.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        props.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        props.put("hibernate.hbm2ddl.auto", "create-drop");
        props.put("hibernate.show_sql", "false");

        return HibernateEmfBuilder.build(props);
    }
}