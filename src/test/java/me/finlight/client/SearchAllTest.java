package me.finlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** R-PAGE-1: maxPages, the warning, the cursor to continue, pages and stream views. */
class SearchAllTest {
  private static List<JsonNode> pages() {
    JsonNode f = Kit.load("fixtures/pagination/three-pages.json");
    return List.of(f.at("/steps/0/response"), f.at("/steps/1/response"), f.at("/steps/2/response"));
  }

  @Test
  void stopsAtMaxPagesWithAWarningAndACursor() throws Exception {
    Logger jul = Logger.getLogger("finlight");
    StringBuilder warnings = new StringBuilder();
    Handler h =
        new Handler() {
          @Override
          public void publish(LogRecord r) {
            warnings.append(r.getMessage()).append('\n');
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    jul.addHandler(h);
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock(), b -> b.maxPages(2));
      pages().forEach(mock::enqueue);
      SearchAllResult all = client.articles().searchAll(SearchParams.builder().limit(2).build());
      assertEquals(4, all.stream().count());
      assertEquals(2, mock.requests.size());
      assertTrue(all.truncated());
      assertEquals(pages().get(1).at("/body/pagination/nextCursor").asText(), all.nextCursor());
      assertTrue(warnings.toString().contains("maxPages=2"), warnings.toString());
      assertThrows(FinlightValidationException.class, all::stream);
      client.close();
    } finally {
      jul.removeHandler(h);
    }
  }

  @Test
  void pagesViewAndOptionOverride() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      pages().forEach(mock::enqueue);
      SearchAllResult all =
          client
              .articles()
              .searchAll(SearchParams.builder().limit(2).build(), SearchAllOptions.maxPages(1));
      int n = 0;
      for (ArticlePage page : all.pages()) {
        n += page.articles().size();
      }
      assertEquals(2, n);
      assertEquals(1, all.pagesFetched());
      assertTrue(all.truncated());
      client.close();
    }
  }

  @Test
  void aStartCursorIsUsedOnPageOne() throws Exception {
    try (HttpMock mock = new HttpMock()) {
      FinlightClient client = Support.client(mock, new FakeClock());
      mock.enqueue(pages().get(2));
      SearchAllResult all =
          client.articles().searchAll(SearchParams.builder().cursor("abc").build());
      assertEquals(1, all.stream().count());
      assertEquals("{\"cursor\":\"abc\"}", mock.requests.get(0).bodyText());
      client.close();
    }
  }
}
