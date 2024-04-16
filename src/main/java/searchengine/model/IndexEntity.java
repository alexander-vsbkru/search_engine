package searchengine.model;

import javax.persistence.*;

@Entity
@Table(name = "`index`")
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @ManyToOne(cascade = CascadeType.MERGE)
    private PageEntity page;
    @ManyToOne(cascade = CascadeType.MERGE)
    private LemmaEntity lemma;
    @Column(name = "`rank`", nullable = false)
    private float rank;

    public int getId() {
        return id;
    }

    public PageEntity getPage() {
        return page;
    }

    public void setPage(PageEntity page) {
        this.page = page;
    }

    public LemmaEntity getLemma() {
        return lemma;
    }

    public void setLemma(LemmaEntity lemma) {
        this.lemma = lemma;
    }

    public float getRank() {
        return rank;
    }

    public void setRank(float rank) {
        this.rank = rank;
    }
}
