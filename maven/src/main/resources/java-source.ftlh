/*
 * The file is generated from ${input_path}.
 * DO NOT EDIT! YOUR CHANGES WILL BE OVERWRITTEN.
 */

package ${java_package};

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import cz.o2.proxima.repository.AttributeDescriptor;
import cz.o2.proxima.repository.AttributeFamilyDescriptor;
import cz.o2.proxima.repository.ConfigRepository;
import cz.o2.proxima.repository.EntityDescriptor;
import cz.o2.proxima.repository.Repository;
import cz.o2.proxima.storage.commitlog.CommitLogReader;
import cz.o2.proxima.storage.randomaccess.KeyValue;
import cz.o2.proxima.storage.randomaccess.RandomAccessReader;
import cz.o2.proxima.view.PartitionedView;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ${java_classname} implements Serializable {

  private static final String GENERATED_FROM = ${input_config};

  /**
   * Create instance of {@code ${java_classname}} for production usage.
   **/
  public static ${java_classname} of(Config cfg) {
    return new ${java_classname}(ConfigRepository.Builder.of(cfg.withFallback(ConfigFactory.parseString(GENERATED_FROM))));
  }

  /**
   * Create instance of {@code ${java_classname}} with specified {@code ConfigRepository.Builder}.
   **/
  public static ${java_classname} of(ConfigRepository.Builder builder) {
    return new ${java_classname}(builder);
  }

  /**
   * Create instance of {@code ${java_classname}} for testing purposes.
   **/
  public static ${java_classname} ofTest(Config cfg) {
    return new ${java_classname}(ConfigRepository.Builder.ofTest(cfg.withFallback(ConfigFactory.parseString(GENERATED_FROM))));
  }

<#list entities as entity>

  /**
   * Class wrapping access to attribute data of entity ${entity.name}.
   **/
  public class ${entity.classname}Wrapper implements Serializable {

  <#list entity.attributes as attribute>
    <#if !attribute.wildcard>
    private ${attribute.type} ${attribute.nameCamel} = null;
    @SuppressWarnings("unchecked")
    private final ${attribute.type} ${attribute.nameCamel}Default = (${attribute.type}) repo.findEntity(
        "${entity.name}").get().findAttribute("${attribute.name}").get().getValueSerializer().getDefault();
    </#if>
  </#list> <#-- entity.attributes as attribute -->

    private ${entity.classname}Wrapper() {

    }

    <#list entity.attributes as attribute>
    <#if !attribute.wildcard>
    public ${attribute.type} get${attribute.nameCamel}() {
      return ${attribute.nameCamel} == null ? ${attribute.nameCamel}Default : ${attribute.nameCamel};
    }
    </#if>
    </#list> <#-- entity.attributes as attribute -->

  }

  /**
   * Class wrapping views on entity ${entity.name}
   **/
  public class ${entity.classname} implements Serializable {

    private final EntityDescriptor descriptor = repo.findEntity("${entity.name}").get();

    public EntityDescriptor getDescriptor() {
      return descriptor;
    }

  <#list entity.attributes as attribute>
    <#if attribute.wildcard>
    @SuppressWarnings("unchecked")
    private final AttributeDescriptor<${attribute.type}> ${attribute.name}Descriptor =
      (AttributeDescriptor) descriptor.findAttribute("${attribute.name}.*").get();
    <#else>
    @SuppressWarnings("unchecked")
    private final AttributeDescriptor<${attribute.type}> ${attribute.name}Descriptor =
      (AttributeDescriptor) descriptor.findAttribute("${attribute.name}").get();
    </#if> <#-- attribute.wildcard -->

    public AttributeDescriptor<${attribute.type}> get${attribute.nameCamel}Descriptor() {
      return ${attribute.name}Descriptor;
    }
  </#list> <#-- entity.attributes as attribute -->

    public Optional<CommitLogReader> getCommitLog(AttributeDescriptor<?>... attributes) {
      Set<AttributeFamilyDescriptor> descriptors = new HashSet<>();
      for (AttributeDescriptor<?> attr : attributes) {
        Set<AttributeFamilyDescriptor> commitLogs = repo.getFamiliesForAttribute(attr)
            .stream()
            .filter(af -> af.getCommitLogReader().isPresent())
            .collect(Collectors.toSet());
        if (commitLogs.isEmpty()) {
          return Optional.empty();
        }
        descriptors.addAll(commitLogs);
      }
      // FIXME: we cannot join multiple commit logs together for now
      // so we have to throw exception here
      if (descriptors.size() > 1) {
        throw new UnsupportedOperationException("Unsupported: multiple commit logs for multiple attributes");
      }
      return descriptors.stream().findAny().get().getCommitLogReader();
    }

    public Optional<PartitionedView> getPartitionedView(AttributeDescriptor<?>... attributes) {

      Set<AttributeFamilyDescriptor> descriptors = new HashSet<>();
      for (AttributeDescriptor<?> attr : attributes) {
        Set<AttributeFamilyDescriptor> views = repo.getFamiliesForAttribute(attr)
            .stream()
            .filter(af -> af.getAccess().canCreatePartitionedView())
            .collect(Collectors.toSet());
        if (views.isEmpty()) {
          return Optional.empty();
        }
        descriptors.addAll(views);
      }
      // FIXME: we cannot join multiple commit logs together for now
      // so we have to throw exception here
      if (descriptors.size() > 1) {
        throw new UnsupportedOperationException("Unsupported: multiple commit logs for multiple attributes");
      }
      return descriptors.stream().findAny().get().getPartitionedView();
    }

    /**
     * Fetch specified attributes from the random access storage and return
     * the object with getters.
     **/
    @SuppressWarnings("unchecked")
    public ${entity.classname}Wrapper get(String key, AttributeDescriptor<?>... attributes) {

      ${entity.classname}Wrapper ret = new ${entity.classname}Wrapper();
      // FIXME: this is highly suboptimal for now
      for (AttributeDescriptor<?> desc : attributes) {
      <#list entity.attributes as attribute>
        if (desc == ${attribute.name}Descriptor) {
          RandomAccessReader reader = repo.getFamiliesForAttribute(desc)
              .stream()
              .filter(af -> af.getRandomAccessReader().isPresent())
              .map(af -> af.getRandomAccessReader().get())
              .findAny()
              .orElseThrow(() -> new IllegalArgumentException("Cannot fetch attribute " + desc));

        <#if !attribute.wildcard>
          Optional<KeyValue<${attribute.type}>> kv = (Optional) reader.get(key, desc);
          ret.${attribute.nameCamel} = kv.map(KeyValue::getValue).orElse(null);
        </#if>
          continue;
        }
      </#list>
      }
      return ret;

    }

    private ${entity.classname}() { }

  }


  </#list>

  private final Config cfg;
  private final Repository repo;

<#list entities as entity>
  private final ${entity.classname} ${entity.name};
</#list>


  private ${java_classname}(ConfigRepository.Builder builder) {
    this.repo = Objects.requireNonNull(builder).build();
    this.cfg = ((ConfigRepository) repo).getConfig();
<#list entities as entity>
    ${entity.name} = new ${entity.classname}();
</#list>
  }

<#list entities as entity>
  public ${entity.classname} get${entity.nameCamel}() {
    return ${entity.name};
  }
</#list>

  public Repository getRepo() {
    return repo;
  }

}

