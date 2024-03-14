package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.PageEntity;

import java.util.List;

public interface PageEntityRepository extends JpaRepository<PageEntity, Integer> {

    /** Выбор страниц по site_id и пути к страницы
     * @param siteId {String} принимает параметр id сайта
     * @param path {String} принимает параметр путь к страницы
     * @return {List<PageEntity>} возвращает список страниц по заданным параметрам
     */
    @Query(value = "SELECT * from page where site_id = :siteId and path = :path", nativeQuery = true)
    List<PageEntity> selectPageIdBySiteIdAndPath(String siteId, String path);

    /** Выбор страниц по site_id
     * @param siteId {String} принимает параметр id сайта
     * @return {List<PageEntity>} возвращает список страниц по заданному параметру
     */
    @Query(value = "SELECT * from page where site_id = :siteId", nativeQuery = true)
    List<PageEntity> selectPageIdBySiteId(String siteId);
}
