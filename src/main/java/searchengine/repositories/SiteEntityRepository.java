package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.SiteEntity;

import java.util.List;

public interface SiteEntityRepository extends JpaRepository<SiteEntity, Integer> {
    @Query(value = "SELECT * from site where url = :url", nativeQuery = true)
    List<SiteEntity> selectSiteIdByUrl(String url);

    @Query(value = "SELECT * from site where status = :status", nativeQuery = true)
    List<SiteEntity> selectSiteIdByStatus(String status);

}
