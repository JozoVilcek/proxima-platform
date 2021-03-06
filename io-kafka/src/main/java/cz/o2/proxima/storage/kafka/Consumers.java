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
package cz.o2.proxima.storage.kafka;

import com.google.common.base.MoreObjects;
import cz.o2.proxima.functional.BiConsumer;
import cz.o2.proxima.functional.Factory;
import cz.o2.proxima.storage.StreamElement;
import cz.o2.proxima.storage.commitlog.BulkLogObserver;
import cz.o2.proxima.storage.commitlog.LogObserverBase;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * Placeholder class for consumers.
 */
@Slf4j
class Consumers {

  private abstract static class ConsumerBase implements ElementConsumer {

    final Map<Integer, Long> committed = Collections.synchronizedMap(new HashMap<>());
    final Map<Integer, Long> processing = Collections.synchronizedMap(new HashMap<>());

    @Override
    public void onCompleted() {
      observer().onCompleted();
    }

    @Override
    public void onCancelled() {
      observer().onCancelled();
    }

    @Override
    public boolean onError(Throwable err) {
      return observer().onError(err);
    }

    @Override
    public void onAssign(
        KafkaConsumer<String, byte[]> consumer,
        List<TopicOffset> offsets) {

      committed.clear();
      committed.putAll(offsets.stream().collect(Collectors.toMap(
          o -> o.getPartition().getId(),
          TopicOffset::getOffset)));
    }

    abstract LogObserverBase observer();

  }

  static final class OnlineConsumer extends ConsumerBase {

    private final KafkaLogObserver observer;
    private final OffsetCommitter<TopicPartition> committer;
    private final Factory<Map<TopicPartition, OffsetAndMetadata>> prepareCommit;

    OnlineConsumer(
        KafkaLogObserver observer,
        OffsetCommitter<TopicPartition> committer,
        Factory<Map<TopicPartition, OffsetAndMetadata>> prepareCommit) {

      this.observer = observer;
      this.committer = committer;
      this.prepareCommit = prepareCommit;
    }

    @Override
    public boolean consumeWithConfirm(
        @Nullable StreamElement element,
        TopicPartition tp, long offset,
        Consumer<Throwable> errorHandler) {

      processing.put(tp.partition(), offset);
      if (element != null) {
        return observer.onNext(element, (succ, exc) -> {
          if (succ) {
            committed.compute(
                tp.partition(),
                (k, v) -> v == null || v <= offset ? offset + 1 : v);
            committer.confirm(tp, offset);
          } else {
            errorHandler.accept(exc);
          }
        }, tp::partition);
      }
      committed.compute(
          tp.partition(),
          (k, v) -> v == null || v <= offset ? offset + 1 : v);
      committer.confirm(tp, offset);
      return true;
    }

    @Override
    public List<TopicOffset> getCurrentOffsets() {
      return TopicOffset.fromMap(processing);
    }

    @Override
    public List<TopicOffset> getCommittedOffsets() {
      return TopicOffset.fromMap(committed);
    }

    @Override
    public Map<TopicPartition, OffsetAndMetadata> prepareOffsetsForCommit() {
      return prepareCommit.apply();
    }

    @Override
    LogObserverBase observer() {
      return observer;
    }

    @Override
    public void onAssign(
        KafkaConsumer<String, byte[]> consumer,
        List<TopicOffset> offsets) {

      super.onAssign(consumer, offsets);
      observer.onRepartition(offsets.stream()
          .map(TopicOffset::getPartition)
          .collect(Collectors.toList()));
    }

  }

  static final class BulkConsumer extends ConsumerBase {

    private final String topic;
    private final BulkLogObserver observer;
    private final BiConsumer<TopicPartition, Long> commit;
    private final Factory<Map<TopicPartition, OffsetAndMetadata>> prepareCommit;

    BulkConsumer(
        String topic,
        BulkLogObserver observer,
        BiConsumer<TopicPartition, Long> commit,
        Factory<Map<TopicPartition, OffsetAndMetadata>> prepareCommit) {

      this.topic = topic;
      this.observer = observer;
      this.commit = commit;
      this.prepareCommit = prepareCommit;
    }

    @Override
    public boolean consumeWithConfirm(
        @Nullable StreamElement element,
        TopicPartition tp, long offset,
        Consumer<Throwable> errorHandler) {

      processing.put(tp.partition(), offset);
      if (element != null) {
        return observer.onNext(
            element, tp::partition,
            bulkCommitter(tp, offset, errorHandler));
      }
      return true;
    }

    private BulkLogObserver.OffsetCommitter bulkCommitter(
        TopicPartition tp, long offset, Consumer<Throwable> errorHandler) {

      return (succ, err) -> {
        if (succ) {
          committed.compute(
              tp.partition(),
              (k, v) -> Math.max(MoreObjects.firstNonNull(v, 0L), offset + 1));
          commit.accept(tp, offset);
        } else {
          errorHandler.accept(err);
        }
      };
    }

    @Override
    public List<TopicOffset> getCurrentOffsets() {
      return TopicOffset.fromMap(processing);
    }

    @Override
    public List<TopicOffset> getCommittedOffsets() {
      return TopicOffset.fromMap(committed);
    }

    @Override
    LogObserverBase observer() {
      return observer;
    }

    @Override
    public Map<TopicPartition, OffsetAndMetadata> prepareOffsetsForCommit() {
      return prepareCommit.apply();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onAssign(
        KafkaConsumer<String, byte[]> consumer,
        List<TopicOffset> offsets) {

      super.onAssign(consumer, offsets);
      observer.onRestart((List) offsets);

      Utils.seekToOffsets(topic, (List) offsets, consumer);
    }

  }


  private Consumers() {
    // nop
  }

}
