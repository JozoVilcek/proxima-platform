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
package cz.o2.proxima.tools.io;

import cz.o2.proxima.annotations.Experimental;
import cz.o2.proxima.functional.UnaryFunction;
import cz.o2.proxima.repository.Repository;
import cz.o2.proxima.storage.OnlineAttributeWriter;
import cz.o2.proxima.storage.StreamElement;
import cz.seznam.euphoria.core.client.io.DataSink;
import cz.seznam.euphoria.core.client.io.Writer;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * Attribute sink using direct write to commit log.
 */
@Slf4j
@Experimental("Does not follow correct exactly-once semantics for now.")
public class DirectAttributeSink implements DataSink<StreamElement> {

  public static DataSink<StreamElement> of(Repository repo) {
    return new DirectAttributeSink(repo);
  }

  public static DataSink<StreamElement> of(
      Repository repo, UnaryFunction<StreamElement, StreamElement> transformFn) {

    return new DirectAttributeSink(repo, transformFn);
  }

  private final Repository repo;
  private final UnaryFunction<StreamElement, StreamElement> transformFn;

  private DirectAttributeSink(Repository repo) {
    this(repo, UnaryFunction.identity());
  }

  private DirectAttributeSink(
      Repository repo,
      UnaryFunction<StreamElement, StreamElement> transformFn) {

    this.repo = repo;
    this.transformFn = transformFn;
  }

  @Override
  public Writer<StreamElement> openWriter(int partitionId) {
    return new Writer<StreamElement>() {

      @Override
      public void write(StreamElement elem) throws IOException {
        OnlineAttributeWriter writer = repo.getWriter(elem.getAttributeDescriptor())
            .orElseThrow(() -> new IllegalStateException(
                "Missing writer for " + elem.getAttributeDescriptor()));
        StreamElement toWrite = transformFn.apply(elem);
        AtomicInteger retries = new AtomicInteger();
        AtomicReference<Runnable> write = new AtomicReference<>();
        write.set(() -> writer.write(toWrite, (succ, exc) -> {
          if (!succ) {
            if (retries.incrementAndGet() >= 3) {
              throw new IllegalStateException(exc);
            }
            log.warn(
                "Failed to write {}, retries {}, retrying.",
                toWrite, retries.get(), exc);
            write.get().run();
          }
        }));
        write.get().run();
      }

      @Override
      public void commit() throws IOException {
        // nop
      }

      @Override
      public void close() throws IOException {
        repo.close();
      }

    };
  }

  @Override
  public void commit() throws IOException {
    // nop
  }

  @Override
  public void rollback() throws IOException {
    // nop
  }

}
