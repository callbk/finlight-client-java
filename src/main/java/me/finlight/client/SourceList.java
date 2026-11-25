package me.finlight.client;

import java.util.AbstractList;
import java.util.List;

/** Result of {@code sources().list()}: the sources plus skipped entries. */
public final class SourceList extends AbstractList<Source> {
  private final List<Source> items;
  private final List<SkippedEntry> skipped;

  SourceList(List<Source> items, List<SkippedEntry> skipped) {
    this.items = List.copyOf(items);
    this.skipped = List.copyOf(skipped);
  }

  @Override
  public Source get(int index) {
    return items.get(index);
  }

  @Override
  public int size() {
    return items.size();
  }

  /** Entries that could not be read (logged as a warning). */
  public List<SkippedEntry> skipped() {
    return skipped;
  }
}
