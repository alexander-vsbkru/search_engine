package searchengine.services;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import searchengine.model.PageEntity;
import searchengine.repositories.PageEntityRepository;
import searchengine.repositories.SiteEntityRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveTask;

public class Links  extends RecursiveTask<List<PageEntity>>
{
    private final Page page;
  //  SiteEntityRepository siteEntityRepository;
    //PageEntityRepository pageEntityRepository;

    public Links(Page page) {//, SiteEntityRepository siteEntityRepository, PageEntityRepository pageEntityRepository) {
     //   this.siteEntityRepository = siteEntityRepository;
      //  this.pageEntityRepository = pageEntityRepository;
        this.page = page;
    }

    @Override
    protected List<PageEntity> compute(){
        List<PageEntity> links = new ArrayList<>();

        List<Links> linksList = new ArrayList<>();
        try {
            Thread.sleep(2500);
            for (Page child : page.getChildren())
            {
                String path = child.getLink();
                String url = child.getSiteParent().getUrl() + path;
                Connection.Response response = Jsoup.connect(url).followRedirects(false).execute();
                int code = response.statusCode();
                String content = response.body();


                PageEntity newPage = new PageEntity();
                newPage.setCode(code);
                newPage.setContent(content);
                newPage.setPath(path);
                newPage.setSite(page.getSiteParent());
                links.add(newPage);
            //    pageEntityRepository.save(newPage);
             //   page.getSiteParent().setStatus_time(LocalDateTime.now());
               // siteEntityRepository.save(page.getSiteParent());
                Links task = new Links(child);//, siteEntityRepository, pageEntityRepository);

                task.fork();
                linksList.add(task);

            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }

        for (Links task : linksList) {
           links.addAll(task.join());
        }

       return links;
    }
}
