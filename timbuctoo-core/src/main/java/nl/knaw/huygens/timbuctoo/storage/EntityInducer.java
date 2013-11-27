package nl.knaw.huygens.timbuctoo.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static nl.knaw.huygens.timbuctoo.config.TypeRegistry.isDomainEntity;
import static nl.knaw.huygens.timbuctoo.config.TypeRegistry.isSystemEntity;

import java.io.IOException;
import java.util.Map;

import nl.knaw.huygens.timbuctoo.config.BusinessRules;
import nl.knaw.huygens.timbuctoo.config.TypeRegistry;
import nl.knaw.huygens.timbuctoo.model.DomainEntity;
import nl.knaw.huygens.timbuctoo.model.Entity;
import nl.knaw.huygens.timbuctoo.model.SystemEntity;
import nl.knaw.huygens.timbuctoo.storage.mongo.MongoObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

public class EntityInducer {

  private static final Logger LOG = LoggerFactory.getLogger(EntityInducer.class);

  protected final TypeRegistry typeRegistry;
  protected final ObjectMapper jsonMapper;
  protected final FieldMapper fieldMapper;
  protected final MongoObjectMapper propertyMapper;

  public EntityInducer(TypeRegistry registry) {
    typeRegistry = registry;
    jsonMapper = new ObjectMapper();
    fieldMapper = new FieldMapper();
    propertyMapper = new MongoObjectMapper(fieldMapper);
  }

  // --- public API ----------------------------------------------------

  /**
   * Converts an entity to a JsonTree.
   */
  @SuppressWarnings("unchecked")
  public <T extends Entity> JsonNode induceNewEntity(Class<? super T> type, T entity) throws IOException {
    checkArgument(type != null && (isSystemEntity(type) || isDomainEntity(type)));
    checkArgument(entity != null);

    if (isSystemEntity(type)) {
      return induceNewSystemEntity((Class<SystemEntity>) type, (SystemEntity) entity);
    } else {
      return induceNewDomainEntity((Class<DomainEntity>) type, (DomainEntity) entity);
    }
  }

  /**
   * Converts an entity to a Json tree and combines it with an existing Json tree.
   */
  @SuppressWarnings("unchecked")
  public <T extends Entity> JsonNode induceOldEntity(Class<T> type, T entity, JsonNode node) throws IOException {
    checkArgument(entity != null);
    checkArgument(node != null);

    if (isSystemEntity(type)) {
      return induceOldSystemEntity((Class<SystemEntity>) type, (SystemEntity) entity, node);
    } else {
      return induceOldDomainEntity(type, entity, node);
    }
  }

  // -------------------------------------------------------------------

  private <T extends SystemEntity> JsonNode induceNewSystemEntity(Class<? super T> type, T entity) {
    checkArgument(BusinessRules.allowSystemEntityAdd(type));

    Map<String, Object> map = Maps.newTreeMap();
    Class<? super T> viewType = type;
    while (Entity.class.isAssignableFrom(viewType)) {
      propertyMapper.addObject(type, viewType, entity, map);
      viewType = viewType.getSuperclass();
    }
    return jsonMapper.valueToTree(map);
  }

  private <T extends DomainEntity> JsonNode induceNewDomainEntity(final Class<? super T> type, T entity) {
    checkArgument(BusinessRules.allowDomainEntityAdd(type));

    Map<String, Object> map = Maps.newTreeMap();
    Class<? super T> viewType = type;
    while (Entity.class.isAssignableFrom(viewType)) {
      propertyMapper.addObject(type, viewType, entity, map);
      viewType = viewType.getSuperclass();
    }
    return jsonMapper.valueToTree(map);
  }

  private <T extends SystemEntity> JsonNode induceOldSystemEntity(final Class<? super T> type, T entity, JsonNode existingItem) {
    Map<String, Object> map = Maps.newTreeMap();
    return jsonMapper.valueToTree(map);
  }

  private <T extends Entity> JsonNode induceOldDomainEntity(Class<T> type, T entity, JsonNode existingItem) {
    LOG.info("Enter induceOldDomainEntity");
    checkArgument(isDomainEntity(type));
    checkArgument(existingItem != null);

    Map<String, Object> map = Maps.newHashMap();
    return jsonMapper.valueToTree(map);
  }

}
