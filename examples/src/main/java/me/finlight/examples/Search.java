package me.finlight.examples;

import java.io.PrintStream;
import java.time.Instant;
import me.finlight.client.Article;
import me.finlight.client.ArticlePage;
import me.finlight.client.Company;
import me.finlight.client.FinlightApiException;
import me.finlight.client.FinlightClient;
import me.finlight.client.IncludeBlock;
import me.finlight.client.SearchParams;

/** One page of articles about NVIDIA, with companies and events. */
public final class Search {
  private Search() {}

  public static void main(String[] args) {
    try (FinlightClient client = Env.client()) {
      run(client, System.out);
    }
  }

  static void run(FinlightClient client, PrintStream out) {
    SearchParams params =
        SearchParams.builder()
            .query("ticker:NVDA")
            .languages("en")
            .include(IncludeBlock.COMPANIES, IncludeBlock.EVENTS)
            .limit(10)
            .build();
    try {
      ArticlePage page = client.articles().search(params);
      for (Article a : page.articles()) {
        Instant published = a.publishedAtTime(); // null when the source sent no readable time
        String companies =
            a.companiesPresent()
                ? String.join(", ", a.companies().value().stream().map(Company::name).toList())
                : "(companies not included in your plan)";
        out.printf("%s  %s  %s%n    %s%n", published, a.source().domain(), a.title(), companies);
      }
      out.println("more pages: " + page.pagination().hasMore());
    } catch (FinlightApiException e) {
      out.printf("API error %d %s (request %s)%n", e.status(), e.code(), e.requestId());
    }
  }
}
