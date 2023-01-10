package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.PageEntity;

public interface PageEntityRepository extends JpaRepository<PageEntity, Integer> {
}
