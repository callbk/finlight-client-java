package me.finlight.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

/**
 * The lazy result of {@code articles().searchAll(...)} (R-PAGE-1). Iterate it once, either over
 * articles ({@link #iterator()}, {@link #stream()}) or over pages ({@link #pages()}). Pages are
 * fetched while you iterate; errors surface from {@code hasNext()}/{@code next()}.
 *
 * <p>The request body was frozen when {@code searchAll} was called; later pages only add the
 * cursor. The walk stops at the last page or after {@code maxPages} pages. Then {@link
 * #truncated()} is true and {@link #nextCursor()} continues the search.
 */
public final class SearchAllResult implements Iterable<Article> {
  private final Function<ObjectNode, ArticlePage> fetch;
  private final ObjectNode frozen;
  private final int maxPages;
  private @Nullable String cursor;
  private @Nullable String nextCursor;
  private boolean started;
  private boolean done;
  private boolean truncated;
  private int pagesFetched;

  SearchAllResult(Function<ObjectNode, ArticlePage> fetch, ObjectNode body, int maxPages) {
    this.fetch = fetch;
    this.frozen = body.deepCopy();
    this.cursor = body.hasNonNull("cursor") ? body.get("cursor").asText() : null;
    this.frozen.remove("cursor");
    this.maxPages = maxPages;
  }

  /** Iterates the articles of every page. Can be called once. */
  @Override
  public Iterator<Article> iterator() {
    Iterator<ArticlePage> pages = pageIterator();
    return new Iterator<>() {
      private Iterator<Article> current = Collections.emptyIterator();

      @Override
      public boolean hasNext() {
        while (!current.hasNext()) {
          if (!pages.hasNext()) {
            return false;
          }
          current = pages.next().articles().iterator();
        }
        return true;
      }

      @Override
      public Article next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return current.next();
      }
    };
  }

  /** The pages instead of the articles. Can be used once, instead of {@link #iterator()}. */
  public Iterable<ArticlePage> pages() {
    Iterator<ArticlePage> it = pageIterator();
    return () -> it;
  }

  /** The articles as a sequential {@link Stream}. Can be used once. */
  public Stream<Article> stream() {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED | Spliterator.NONNULL),
        false);
  }

  /** The cursor to continue after the last fetched page, or null when the search is complete. */
  public synchronized @Nullable String nextCursor() {
    return nextCursor;
  }

  /** True when the walk stopped at {@code maxPages} while more pages exist. */
  public synchronized boolean truncated() {
    return truncated;
  }

  /** Number of pages fetched so far. */
  public synchronized int pagesFetched() {
    return pagesFetched;
  }

  private synchronized Iterator<ArticlePage> pageIterator() {
    if (started) {
      throw new FinlightValidationException(
          null, "a searchAll result can be iterated once; call searchAll again for a new search");
    }
    started = true;
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        synchronized (SearchAllResult.this) {
          return !done;
        }
      }

      @Override
      public ArticlePage next() {
        synchronized (SearchAllResult.this) {
          if (done) {
            throw new NoSuchElementException();
          }
          return fetchNext();
        }
      }
    };
  }

  private ArticlePage fetchNext() {
    ObjectNode body = frozen.deepCopy();
    if (cursor != null) {
      body.put("cursor", cursor);
    }
    ArticlePage page;
    try {
      page = fetch.apply(body);
    } catch (RuntimeException e) {
      done = true;
      throw e;
    }
    pagesFetched++;
    String next = page.pagination().nextCursor();
    if (!page.pagination().hasMore() || next == null) {
      done = true;
      nextCursor = null;
    } else if (pagesFetched >= maxPages) {
      done = true;
      truncated = true;
      nextCursor = next;
      Log.warn(
          "searchAll stopped after maxPages="
              + maxPages
              + " pages; more results exist. Continue with nextCursor().");
    } else {
      cursor = next;
      nextCursor = next;
    }
    return page;
  }
}
