/**
 * Copyright 2017-2018 O2 Czech Republic, a.s.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.o2.proxima.view;

import cz.o2.proxima.functional.BiConsumer;
import cz.o2.proxima.functional.Consumer;
import cz.o2.proxima.repository.AttributeDescriptor;
import cz.o2.proxima.repository.EntityDescriptor;
import cz.o2.proxima.storage.CommitCallback;
import cz.o2.proxima.storage.OnlineAttributeWriter;
import cz.o2.proxima.storage.Partition;
import cz.o2.proxima.storage.StreamElement;
import cz.o2.proxima.storage.commitlog.BulkLogObserver;
import cz.o2.proxima.storage.commitlog.CommitLogReader;
import cz.o2.proxima.storage.commitlog.ObserveHandle;
import cz.o2.proxima.storage.commitlog.Offset;
import cz.o2.proxima.storage.commitlog.Position;
import cz.o2.proxima.storage.randomaccess.KeyValue;
import cz.o2.proxima.storage.randomaccess.RandomOffset;
import cz.o2.proxima.storage.randomaccess.RawOffset;
import cz.o2.proxima.util.Pair;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * A transformation view from {@link PartitionedView} to {@link PartitionedCachedView}.
 */
@Slf4j
public class LocalCachedPartitionedView implements PartitionedCachedView {

  private final CommitLogReader reader;
  private final EntityDescriptor entity;

  /**
   * Writer to persist data to.
   */
  private final OnlineAttributeWriter writer;

  /**
   * Cache for data in memory.
   */
  private final TimeBoundedVersionedCache cache;

  /**
   * Handle of the observation thread (if any running).
   */
  private final AtomicReference<ObserveHandle> handle = new AtomicReference<>();

  private BiConsumer<StreamElement, Pair<Long, Object>> updateCallback = (e, old) -> { };

  public LocalCachedPartitionedView(
      EntityDescriptor entity, CommitLogReader reader, OnlineAttributeWriter writer) {

    this(entity, reader, writer, 60_000L);
  }

  public LocalCachedPartitionedView(
      EntityDescriptor entity, CommitLogReader reader, OnlineAttributeWriter writer,
      long keepCachedDuration) {

    this.cache = new TimeBoundedVersionedCache(keepCachedDuration);
    this.reader = reader;
    this.entity = entity;
    this.writer = writer;
  }

  @SuppressWarnings("unchecked")
  protected void onCache(StreamElement ingest, boolean overwrite) {

    final Optional<Object> parsed = ingest.isDelete()
        ? Optional.empty()
        : ingest.getParsed();
    if (ingest.isDelete() || parsed.isPresent()) {
      final String attrName;
      if (ingest.isDeleteWildcard()) {
        attrName = ingest.getAttributeDescriptor().toAttributePrefix();
      } else {
        attrName = ingest.getAttribute();
      }
      Pair<Long, Object> oldVal = cache.get(
          ingest.getKey(), attrName, Long.MAX_VALUE);
      boolean updated = cache.put(
          ingest.getKey(), attrName, ingest.getStamp(),
          overwrite, ingest.isDelete() ? null : parsed.get());
      if (updated) {
        updateCallback.accept(ingest, oldVal);
      }
    }
  }


  @Override
  public void assign(
      Collection<Partition> partitions,
      BiConsumer<StreamElement, Pair<Long, Object>> updateCallback) {

    close();
    this.updateCallback = Objects.requireNonNull(updateCallback);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicLong prefetchedCount = new AtomicLong();

    BulkLogObserver prefetchObserver = new BulkLogObserver() {

      @Override
      public boolean onNext(
          StreamElement ingest,
          Partition partition,
          OffsetCommitter committer) {

        try {
          prefetchedCount.incrementAndGet();
          onCache(ingest, false);
          committer.confirm();
          return true;
        } catch (Throwable ex) {
          committer.fail(ex);
          return false;
        }
      }

      @Override
      public boolean onError(Throwable error) {
        log.error("Failed to prefetch data", error);
        assign(partitions);
        return false;
      }

      @Override
      public void onCompleted() {
        latch.countDown();
      }

    };
    BulkLogObserver observer = new BulkLogObserver() {

      @Override
      public boolean onNext(
          StreamElement ingest,
          Partition partition,
          OffsetCommitter confirm) {

        try {
          onCache(ingest, false);
          confirm.confirm();
          return true;
        } catch (Throwable err) {
          confirm.fail(err);
          return false;
        }
      }

      @Override
      public boolean onError(Throwable error) {
        log.error("Error in caching data. Restarting consumption", error);
        assign(partitions);
        return false;
      }

    };
    try {
      // prefetch the data
      log.info(
          "Starting prefetching old topic data for partitions {} with preUpdate {}",
          partitions.stream().map(Partition::getId).collect(Collectors.toList()),
          updateCallback);
      ObserveHandle h = reader.observeBulkPartitions(
          partitions, Position.OLDEST, true, prefetchObserver);
      latch.await();
      log.info(
          "Finished prefetching of data after {} records. Starting consumption "
              + "of updates.",
          prefetchedCount.get());
      List<Offset> offsets = h.getCommittedOffsets();
      // continue the processing
      handle.set(reader.observeBulkOffsets(offsets, observer));
      handle.get().waitUntilReady();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(ex);
    }
  }

  @Override
  public Collection<Partition> getAssigned() {
    if (handle.get() != null) {
      return handle.get().getCommittedOffsets()
          .stream().map(Offset::getPartition)
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  @Override
  public RandomOffset fetchOffset(Listing type, String key) {
    return new RawOffset(key);
  }

  @Override
  public <T> Optional<KeyValue<T>> get(
      String key,
      String attribute,
      AttributeDescriptor<T> desc,
      long stamp) {

    long deleteStamp = Long.MIN_VALUE;
    if (desc.isWildcard()) {
      // check there is not wildcard delete
      Pair<Long, Object> wildcard = cache.get(key, desc.toAttributePrefix(), stamp);
      if (wildcard != null && wildcard.getSecond() == null) {
        // this is delete
        // move the required stamp after the delete
        deleteStamp = wildcard.getFirst();
      }
    }
    final long filterStamp = deleteStamp;
    return Optional.ofNullable(cache.get(key, attribute, stamp))
        .filter(e -> e.getFirst() >= filterStamp)
        .flatMap(e -> Optional.ofNullable(toKv(key, attribute, e)));
  }

  @Override
  public void scanWildcardAll(
      String key, RandomOffset offset, long stamp,
      int limit, Consumer<KeyValue<?>> consumer) {

    String off = offset == null ? "" : ((RawOffset) offset).getOffset();
    scanWildcardPrefix(key, "", off, stamp, limit, consumer);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> void scanWildcard(
      String key,
      AttributeDescriptor<T> wildcard,
      RandomOffset offset,
      long stamp,
      int limit,
      Consumer<KeyValue<T>> consumer) {

    String off = offset == null
        ? wildcard.toAttributePrefix()
        : ((RawOffset) offset).getOffset();
    scanWildcardPrefix(
        key, wildcard.toAttributePrefix(), off, stamp, limit, (Consumer) consumer);
  }

  @SuppressWarnings("unchecked")
  private void scanWildcardPrefix(
      String key,
      String prefix,
      String offset,
      long stamp,
      int limit,
      Consumer<KeyValue<?>> consumer) {

    AtomicInteger missing = new AtomicInteger(limit);
    cache.scan(key, prefix, offset, stamp,
        attr -> {
          AttributeDescriptor<?> desc = entity.findAttribute(attr)
              .orElseThrow(() -> new IllegalStateException(
                  "Missing attribute " + attr + " in " + entity));
          if (desc.isWildcard()) {
            return desc.toAttributePrefix();
          }
          return null;
        },
        (attr, e) -> {
          KeyValue<Object> kv = toKv(key, attr, e);
          if (kv != null) {
            if (missing.decrementAndGet() != 0) {
              consumer.accept(kv);
            } else {
              return false;
            }
          }
          return true;
        });
  }

  @Override
  public void listEntities(
      RandomOffset offset,
      int limit,
      Consumer<Pair<RandomOffset, String>> consumer) {

    throw new UnsupportedOperationException(
        "Don't use this view for listing entities");
  }

  @Override
  public void close() {
    Optional.ofNullable(handle.getAndSet(null)).ifPresent(ObserveHandle::cancel);
    cache.clear();
  }

  @SuppressWarnings("unchecked")
  private @Nullable <T> KeyValue<T> toKv(
      String key, String attribute, @Nullable Pair<Long, Object> p) {

    Optional<AttributeDescriptor<Object>> attrDesc = entity.findAttribute(
        attribute, true);
    if (attrDesc.isPresent()) {
      return toKv(key, attribute, attrDesc.get(), p);
    }
    log.warn("Missing attribute {} in entity {}", attribute, entity);
    return null;
  }

  @SuppressWarnings("unchecked")
  private @Nullable <T> KeyValue<T> toKv(
      String key, String attribute,
      AttributeDescriptor<?> attr, @Nullable Pair<Long, Object> p) {

    if (p == null || p.getSecond() == null) {
      return null;
    }
    return (KeyValue) KeyValue.of(
        entity, (AttributeDescriptor) attr, key, attribute,
        new RawOffset(attribute), (T) p.getSecond(), null,
        p.getFirst());
  }

  @Override
  public EntityDescriptor getEntityDescriptor() {
    return entity;
  }

  @Override
  public void write(StreamElement data, CommitCallback statusCallback) {
    try {
      cache(data);
      writer.write(data, statusCallback);
    } catch (Exception ex) {
      statusCallback.commit(false, ex);
    }
  }

  @Override
  public URI getUri() {
    return reader.getUri();
  }

  @Override
  public void cache(StreamElement element) {
    onCache(element, true);
  }

}
