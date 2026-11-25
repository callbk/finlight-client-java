package me.finlight.client.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import me.finlight.client.AdmittedInfo;
import me.finlight.client.Article;
import me.finlight.client.ArticlePage;
import me.finlight.client.CompanyGetOptions;
import me.finlight.client.CompanyList;
import me.finlight.client.CompanyRecord;
import me.finlight.client.CompanySearchParams;
import me.finlight.client.FinlightClient;
import me.finlight.client.FinlightConfig;
import me.finlight.client.FinlightException;
import me.finlight.client.FinlightStream;
import me.finlight.client.IncludeBlock;
import me.finlight.client.LookupOptions;
import me.finlight.client.NotFoundException;
import me.finlight.client.RawStreamParams;
import me.finlight.client.SearchParams;
import me.finlight.client.SourceList;
import me.finlight.client.StreamEvent;
import me.finlight.client.StreamListener;
import me.finlight.client.StreamParams;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Small live checks against dev only (R-TEST-2). Needs FINLIGHT_API_KEY, FINLIGHT_BASE_URL and
 * FINLIGHT_WSS_URL; skipped when one is missing, and refuses production hosts. Run with {@code
 * ./gradlew liveTest} and the owner's okay. A handful of requests, two short streams.
 */
@Timeout(120)
class LiveDevTest {
  private static FinlightClient client;

  @BeforeAll
  static void setUp() {
    String key = System.getenv("FINLIGHT_API_KEY");
    String base = System.getenv("FINLIGHT_BASE_URL");
    String wss = System.getenv("FINLIGHT_WSS_URL");
    Assumptions.assumeTrue(
        key != null
            && !key.isBlank()
            && base != null
            && !base.isBlank()
            && wss != null
            && !wss.isBlank(),
        "live tests need FINLIGHT_API_KEY, FINLIGHT_BASE_URL and FINLIGHT_WSS_URL");
    for (String url : List.of(base, wss)) {
      String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
      if (!host.contains(".dev.")) {
        throw new IllegalStateException("live tests run against dev only, not " + host);
      }
    }
    client = new FinlightClient(FinlightConfig.builder(key).baseUrl(base).wssUrl(wss).build());
  }

  @AfterAll
  static void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void searchAndLookups() {
    ArticlePage page =
        client
            .articles()
            .search(
                SearchParams.builder()
                    .query("ticker:NVDA")
                    .languages("en")
                    .include(IncludeBlock.COMPANIES, IncludeBlock.EVENTS)
                    .limit(3)
                    .build());
    System.out.println(
        "search: " + page.articles().size() + " articles, hasMore " + page.pagination().hasMore());
    assertTrue(page.skipped().isEmpty(), "every article decodes");
    assertFalse(page.articles().isEmpty());
    Article first = page.articles().get(0);
    assertTrue(first.companiesPresent() || !first.companiesPresent());
    Article byLink =
        client.articles().getByLink(first.link(), LookupOptions.include(IncludeBlock.CONTENT));
    assertEquals(first.id(), byLink.id());
    Article byId = client.articles().get(first.id());
    assertEquals(first.link(), byId.link());
    try {
      client.articles().get("does-not-exist-000000000");
      throw new AssertionError("expected a 404");
    } catch (NotFoundException e) {
      assertEquals(404, e.status());
      assertNotNull(e.requestId());
    }
    SourceList sources = client.sources().list();
    System.out.println("sources: " + sources.size() + ", skipped " + sources.skipped().size());
    assertFalse(sources.isEmpty());
    CompanyList companies = client.companies().search(CompanySearchParams.of("NVDA", "XNAS"));
    System.out.println("companies: " + companies.size());
    if (!companies.isEmpty()) {
      CompanyRecord record = client.companies().get(companies.get(0).companyId());
      assertNotNull(record.cacheInfo());
      CompanyRecord again =
          client
              .companies()
              .get(record.companyId(), CompanyGetOptions.ifNoneMatch(record.cacheInfo().etag()));
      System.out.println("company revalidated, not modified " + (again == null));
    }
  }

  @Test
  void streamsAdmit() throws Exception {
    for (boolean raw : new boolean[] {false, true}) {
      CountDownLatch admitted = new CountDownLatch(1);
      List<FinlightException> errors = new CopyOnWriteArrayList<>();
      List<StreamEvent> events = new CopyOnWriteArrayList<>();
      StreamListener listener =
          new StreamListener() {
            @Override
            public void onEvent(StreamEvent event) {
              events.add(event);
            }

            @Override
            public void onAdmitted(AdmittedInfo info) {
              admitted.countDown();
            }

            @Override
            public void onError(FinlightException error) {
              errors.add(error);
            }

            @Override
            public void onClosed(@Nullable FinlightException terminal) {
              if (terminal != null) {
                errors.add(terminal);
              }
            }
          };
      FinlightStream stream =
          raw
              ? client.rawStream(RawStreamParams.builder().language("en").build(), listener)
              : client.stream(
                  StreamParams.builder().query("ticker:NVDA").language("en").build(), listener);
      assertTrue(
          admitted.await(30, TimeUnit.SECONDS),
          (raw ? "raw" : "enriched") + " stream admitted; errors " + errors);
      Thread.sleep(raw ? 1000 : 5000);
      stream.close();
      assertTrue(stream.awaitTermination(Duration.ofSeconds(10)));
      assertNull(stream.terminalError());
      System.out.println(
          (raw ? "raw" : "enriched")
              + " stream: admitted, "
              + events.size()
              + " events, errors "
              + errors);
      assertTrue(errors.isEmpty(), "no stream errors: " + errors);
    }
  }
}
