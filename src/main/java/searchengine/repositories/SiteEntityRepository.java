package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.SiteEntity;

import java.util.List;

public interface SiteEntityRepository extends JpaRepository<SiteEntity, Integer> {

    /** Поиск сайтов по url
     * @param url {String} принимает параметр url сайта
     * @return {List<SiteEntity> возвращает список сайтов по заданному url}
     */
    @Query(value = "SELECT * from site where url = :url", nativeQuery = true)
    List<SiteEntity> selectSiteIdByUrl(String url);
}
