package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.IndexEntity;
import searchengine.model.PageEntity;

import java.util.List;

public interface IndexEntityRepository extends JpaRepository<IndexEntity, Integer> {
    @Query(value = "SELECT * from `index` where page_id = :pageId and lemma_id in (:lemmaId)", nativeQuery = true)
    List<IndexEntity> selectIndexIdByPageIdAndLemmaId(int pageId, List<Integer> lemmaId);

    @Query(value = "SELECT * from `index` where page_id in (:pageId)", nativeQuery = true)
    List<IndexEntity> selectIndexIdByPageId(String pageId);

    @Query(value = "SELECT * from `index` where lemma_id in (:lemmaId)", nativeQuery = true)
    List<IndexEntity> selectIndexIdByLemmaId(List<Integer> lemmaId);

 //   @Query(value = "SELECT page_id from `index` where lemma_id = :lemmaId", nativeQuery = true)
 //   List<PageEntity> selectPageIdByLemmaId(List<Integer> lemmaId);

}
