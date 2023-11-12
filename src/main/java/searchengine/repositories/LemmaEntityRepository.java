package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.LemmaEntity;

import java.util.List;

public interface LemmaEntityRepository extends JpaRepository<LemmaEntity, Integer> {
    @Query(value = "SELECT id, site_id, lemma, sum(frequency)  as frequency from lemma where site_id in (:siteId)" +
            " and lemma in (:lemma) group by lemma order by frequency", nativeQuery = true)
    List<LemmaEntity> selectLemmaIdBySiteIdAndLemma(List<Integer> siteId, List<String> lemma);

    void deleteByFrequency(int frequency);

    @Query(value = "SELECT * from lemma where site_id = :siteId", nativeQuery = true)
    List<LemmaEntity> selectLemmaIdBySiteId(String siteId);

    @Query(value = "SELECT id from lemma where lemma = :lemma", nativeQuery = true)
    List<Integer> selectLemmaIdByLemma(String lemma);
}
