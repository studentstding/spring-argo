package pl.app.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import pl.app.model.Person;

import java.util.List;

@Repository
public class PersonRepository {

    @PersistenceContext
    private EntityManager em;

    public List<Person> findAll() {
        return em.createQuery("SELECT p FROM Person p ORDER BY p.id", Person.class)
                .getResultList();
    }

}
