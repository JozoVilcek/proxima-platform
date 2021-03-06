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
package cz.o2.proxima.storage.http;

import cz.o2.proxima.repository.Context;
import cz.o2.proxima.repository.EntityDescriptor;
import cz.o2.proxima.storage.AbstractStorage;
import cz.o2.proxima.storage.AttributeWriterBase;
import cz.o2.proxima.storage.DataAccessor;
import cz.o2.proxima.storage.commitlog.CommitLogReader;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * Accessor for HTTP(s) and websocket URLs.
 */
public class HttpAccessor extends AbstractStorage implements DataAccessor {

  final Map<String, Object> cfg;

  public HttpAccessor(
      EntityDescriptor entityDesc, URI uri, Map<String, Object> cfg) {

    super(entityDesc, uri);
    this.cfg = cfg;
  }

  @Override
  public Optional<AttributeWriterBase> getWriter(Context context) {
    if (getUri().getScheme().startsWith("http")) {
      return Optional.of(new HttpWriter(getEntityDescriptor(), getUri(), cfg));
    }
    return Optional.empty();
  }

  @Override
  public Optional<CommitLogReader> getCommitLogReader(Context context) {
    if (getUri().getScheme().startsWith("ws")) {
      return Optional.of(new WebsocketReader(getEntityDescriptor(), getUri(), cfg));
    }
    return Optional.empty();
  }



}
