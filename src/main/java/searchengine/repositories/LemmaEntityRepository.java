package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import searchengine.model.LemmaEntity;

import java.util.List;

public interface LemmaEntityRepository extends JpaRepository<LemmaEntity, Integer> {

    /** Поиск лемм по site_id и леммам, упорядоченных по частоте нахождения лемм
     * @param siteId {list<Integer>} принимает параметр список id сайтов
     * @param lemma {List<String>} принимает параметр список лемм
     * @return {List<LemmaEntity>} возвращает список лемм по заданным параметрам
     */
    @Query(value = "SELECT id, site_id, lemma, sum(frequency)  as frequency from lemma where site_id in (:siteId)" +
            " and lemma in (:lemma) group by lemma order by frequency", nativeQuery = true)
    List<LemmaEntity> selectLemmaIdBySiteIdAndLemmaOrderByFreq(List<Integer> siteId, List<String> lemma);

    /** Выбор лемм по site_id
     * @param siteId {String} принимает параметр id сайта
     * @return {List<LemmaEntity>} возвращает список лемм по заданным параметрам
     */
    @Query(value = "SELECT * from lemma where site_id = :siteId", nativeQuery = true)
    List<LemmaEntity> selectLemmaIdBySiteId(String siteId);

    /** Поиск lemma_id по лемме
     * @param lemma {String} принимает параметр лемма
     * @return {List<Integer>} возвращает список id лемм по заданным параметрам
     */
    @Query(value = "SELECT id from lemma where lemma = :lemma", nativeQuery = true)
    List<Integer> selectLemmaIdByLemma(String lemma);

    /** Поиск лемм по частоте
     * @param frequency {int} принимает параметр частота леммы
     */

    @Query(value = "select id from lemma where frequency = :frequency", nativeQuery = true)
    List<Integer> selectByFrequency(int frequency);

}
