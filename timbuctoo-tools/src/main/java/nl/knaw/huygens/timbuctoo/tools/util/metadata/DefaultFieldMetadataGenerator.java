package nl.knaw.huygens.timbuctoo.tools.util.metadata;

import java.lang.reflect.Field;
import java.util.Map;

import nl.knaw.huygens.timbuctoo.storage.FieldMapper;

import com.google.common.collect.Maps;

public class DefaultFieldMetadataGenerator extends FieldMetaDataGenerator {

  public DefaultFieldMetadataGenerator(TypeNameGenerator typeNameGenerator, FieldMapper fieldMapper) {
    super(typeNameGenerator, fieldMapper);
  }

  @Override
  protected Object constructValue(Field field) {
    Map<String, Object> valueMap = Maps.newHashMap();
    valueMap.put(TYPE_FIELD, typeNameGenerator.getTypeName(field));

    return valueMap;
  }

}
