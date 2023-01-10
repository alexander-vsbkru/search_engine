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
      /*  Transaction tx = null;
        try {
            tx = session.beginTransaction();
            String hql = "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                    "WHERE table_name = 'page' and index_name = 'index_path'";
  //          Query query = session.createQuery(hql);
    //        List results = query.getResultList();
   //         if (results.isEmpty()) {
                hql = "create index if not exists index_path on page(path(255))";
                Query query = session.createQuery(hql);
         //   }
            tx.commit();
        }
        catch (HibernateException hex) {
            hex.printStackTrace();
        }
        finally {
            session.close();
        }*/
    }

    @Override
    public void run(String... args) throws Exception {


    }
}
