package me.finlight.examples;

import java.io.PrintStream;
import me.finlight.client.FinlightClient;
import me.finlight.client.SearchAllOptions;
import me.finlight.client.SearchAllResult;
import me.finlight.client.SearchParams;

/** Walks up to 3 pages. Every page costs quota, so the walk is capped. */
public final class SearchAll {
  private SearchAll() {}

  public static void main(String[] args) {
    try (FinlightClient client = Env.client()) {
      run(client, System.out);
    }
  }

  static void run(FinlightClient client, PrintStream out) {
    SearchParams params =
        SearchParams.builder().query("ticker:NVDA").languages("en").limit(20).build();
    SearchAllResult result = client.articles().searchAll(params, SearchAllOptions.maxPages(3));
    long count = result.stream().count(); // pages are fetched while the stream runs
    out.println(count + " articles from " + result.pagesFetched() + " pages");
    if (result.truncated()) {
      out.println(
          "stopped at maxPages; continue with params.withCursor(\"" + result.nextCursor() + "\")");
    }
  }
}
