package me.finlight.examples;

import java.io.PrintStream;
import me.finlight.client.Article;
import me.finlight.client.CompanyList;
import me.finlight.client.CompanyRecord;
import me.finlight.client.CompanySearchParams;
import me.finlight.client.FinlightClient;
import me.finlight.client.IncludeBlock;
import me.finlight.client.LookupOptions;
import me.finlight.client.NotFoundException;
import me.finlight.client.SourceList;

/** Article, company and source lookups. */
public final class Lookup {
  private Lookup() {}

  public static void main(String[] args) {
    String link = args.length > 0 ? args[0] : "https://www.reuters.com/markets/";
    try (FinlightClient client = Env.client()) {
      run(client, System.out, link);
    }
  }

  static void run(FinlightClient client, PrintStream out, String link) {
    try {
      Article byLink =
          client.articles().getByLink(link, LookupOptions.include(IncludeBlock.CONTENT));
      if (byLink.contentPresent()) {
        String text = byLink.content().value();
        out.println(
            "content: " + (text == null ? "none for this article" : text.length() + " chars"));
      } else {
        out.println("content is not in your plan");
      }
      Article byId = client.articles().get(byLink.id());
      out.println("by id: " + byId.title());
    } catch (NotFoundException e) {
      // Articles indexed before ids existed are found by link only.
      out.println("not found (request " + e.requestId() + ")");
    }

    CompanyList companies = client.companies().search(CompanySearchParams.of("NVDA", "XNAS"));
    for (CompanyRecord c : companies) {
      out.println("company " + c.companyId() + " " + c.name());
    }
    if (!companies.isEmpty()) {
      CompanyRecord record = client.companies().get(companies.get(0).companyId());
      out.println(
          "etag "
              + record.cacheInfo().etag()
              + ", cache-control "
              + record.cacheInfo().cacheControl());
    }

    SourceList sources = client.sources().list();
    out.println(sources.size() + " sources");
  }
}
