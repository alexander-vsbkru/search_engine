package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.PageEntity;

import java.util.List;

public interface PageEntityRepository extends JpaRepository<PageEntity, Integer> {
    @Query(value = "SELECT * from page where site_id = :siteId and path = :path", nativeQuery = true)
    List<PageEntity> selectPageIdBySiteIdAndPath(String siteId, String path);

    @Query(value = "SELECT * from page where site_id = :siteId", nativeQuery = true)
    List<PageEntity> selectPageIdBySiteId(String siteId);
}
