package searchengine.config;

import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManagerFactory;

@Configuration
public class CommandLineRunnerImpl implements CommandLineRunner {

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }
    EntityManagerFactory entityManagerFactory;

    @Autowired
    public CommandLineRunnerImpl(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
        Session session = entityManagerFactory.createEntityManager().unwrap(Session.class);
    }

    @Override
    public void run(String... args) {


    }
}
