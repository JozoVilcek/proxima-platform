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
package cz.o2.proxima.tools.groovy;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import cz.o2.proxima.repository.AttributeDescriptor;
import cz.o2.proxima.repository.ConfigRepository;
import cz.o2.proxima.repository.EntityDescriptor;
import cz.o2.proxima.repository.Repository;
import cz.o2.proxima.storage.StreamElement;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Test suite for {@link GroovyEnv}.
 */
public class GroovyEnvTest {

  final Config cfg = ConfigFactory.load("test-reference.conf").resolve();
  final Repository repo = ConfigRepository.of(cfg);
  final EntityDescriptor gateway = repo.findEntity("gateway")
      .orElseThrow(() -> new IllegalStateException("Missing entity gateway"));
  @SuppressWarnings("unchecked")
  final AttributeDescriptor<byte[]> armed = (AttributeDescriptor) gateway
      .findAttribute("armed")
      .orElseThrow(() -> new IllegalStateException("Missing attribute armed"));
  Configuration conf;

  GroovyClassLoader loader;

  @Before
  public void setUp() {
    Console.create(cfg, repo);
    conf = new Configuration(Configuration.VERSION_2_3_23);
    conf.setDefaultEncoding("utf-8");
    conf.setClassForTemplateLoading(getClass(), "/");
    conf.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    conf.setLogTemplateExceptions(false);

    loader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader());
    Thread.currentThread().setContextClassLoader(loader);
  }

  @SuppressWarnings("unchecked")
  Script compile(String script) throws Exception {
    String source = GroovyEnv.getSource(conf, repo)
        + "\n"
        + "env = cz.o2.proxima.tools.groovy.Console.get().getEnv()"
        + "\n"
        + script;
    Class<Script> parsed = loader.parseClass(source);
    return parsed.newInstance();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testStreamFromOldestCollect() throws Exception {
    Script compiled = compile("env.gateway.armed.streamFromOldest().collect()");
    repo.getWriter(armed)
        .orElseThrow(() -> new IllegalStateException("Missing writer"))
        .write(StreamElement.update(gateway, armed, "uuid",
            "key", armed.getName(), System.currentTimeMillis(), new byte[] { }),
            (succ, exc) -> { });
    List<StreamElement> result = (List) compiled.run();
    assertEquals(1, result.size());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testUnionFromOldestCollect() throws Exception {
    Script compiled = compile("env.unionStreamFromOldest(env.gateway.armed).collect()");
    repo.getWriter(armed)
        .orElseThrow(() -> new IllegalStateException("Missing writer"))
        .write(StreamElement.update(gateway, armed, "uuid",
            "key", armed.getName(), System.currentTimeMillis(), new byte[] { }),
            (succ, exc) -> { });
    List<StreamElement> result = (List) compiled.run();
    assertEquals(1, result.size());
  }

}
