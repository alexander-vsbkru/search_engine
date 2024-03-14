package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.IndexEntity;

import java.util.List;

public interface IndexEntityRepository extends JpaRepository<IndexEntity, Integer> {
    /** Поиск индексов по странице и лемме
     * @param pageId {int} принимает параметр id страницы
     * @param lemmaId {List<Integer>} принимает параметр список id лемм
     * @return {List<IndexEntity>} возвращает список индексов по заданным параметрам
     */
    @Query(value = "SELECT * from `index` where page_id = :pageId and lemma_id in (:lemmaId)", nativeQuery = true)
    List<IndexEntity> selectIndexIdByPageIdAndLemmaId(int pageId, List<Integer> lemmaId);

    /** Поиск индексов по странице
     * @param pageId {String} принимает параметр id страницы
     * @return {List<IndexEntity>} возвращает список индексов по заданным параметрам
     */
    @Query(value = "SELECT * from `index` where page_id in (:pageId)", nativeQuery = true)
    List<IndexEntity> selectIndexIdByPageId(String pageId);

    /** Поиск индексов по лемме
     * @param lemmaId {List<Integer>} принимает параметр список id лемм
     * @return {List<IndexEntity>} возвращает список индексов по заданным параметрам
     */
    @Query(value = "SELECT * from `index` where lemma_id in (:lemmaId)", nativeQuery = true)
    List<IndexEntity> selectIndexIdByLemmaId(List<Integer> lemmaId);
}

