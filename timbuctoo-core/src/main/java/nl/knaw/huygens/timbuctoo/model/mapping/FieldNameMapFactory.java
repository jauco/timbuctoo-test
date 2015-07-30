package nl.knaw.huygens.timbuctoo.model.mapping;

import com.fasterxml.jackson.annotation.JsonProperty;
import nl.knaw.huygens.timbuctoo.facet.IndexAnnotation;
import nl.knaw.huygens.timbuctoo.model.DerivedProperty;
import nl.knaw.huygens.timbuctoo.model.DomainEntity;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static nl.knaw.huygens.timbuctoo.storage.graph.MethodHelper.getGetterName;
import static nl.knaw.huygens.timbuctoo.storage.graph.MethodHelper.getMethodByName;

public class FieldNameMapFactory {
  public <T extends DomainEntity> FieldNameMap create(Representation from, Representation to, Class<T> type) throws MappingException {
    FieldNameMap fieldNameMap = new FieldNameMap();

    Class<?> typeToAddFieldsFor = type;

    for (; isDomainEntity(typeToAddFieldsFor); typeToAddFieldsFor = typeToAddFieldsFor.getSuperclass()) {
      addFields(from, to, typeToAddFieldsFor, fieldNameMap);
    }

    try {
      for (DerivedProperty derivedProperty : type.newInstance().getDerivedProperties()) {
        String key = from.getFieldName(type, derivedProperty);
        String value = to.getFieldName(type, derivedProperty);

        addField(fieldNameMap, key, value);
      }
    } catch (InstantiationException | IllegalAccessException e) {
      throw new MappingException(type, e);
    }


    return fieldNameMap;
  }

  private boolean isDomainEntity(Class<?> typeToAddFieldsFor) {
    return DomainEntity.class.isAssignableFrom(typeToAddFieldsFor);
  }

  private void addFields(Representation from, Representation to, Class<?> type, FieldNameMap fieldNameMap) {
    for (Field field : type.getDeclaredFields()) {
      String key = from.getFieldName(type, field);
      String value = to.getFieldName(type, field);
      addField(fieldNameMap, key, value);
    }
  }

  private void addField(FieldNameMap fieldNameMap, String key, String value) {
    if (key != null && value != null) {
      fieldNameMap.put(key, value);
    }
  }

  public enum Representation {
    CLIENT {
      @Override
      protected String getFieldName(Class<?> type, Field field) {
        if (field.isAnnotationPresent(JsonProperty.class)) {
          return field.getAnnotation(JsonProperty.class).value();
        }

        Method method = getMethodByName(type, getGetterName(field));
        if (isAnnotationPresentOnMethod(method, JsonProperty.class)) {
          return method.getAnnotation(JsonProperty.class).value();
        }

        return field.getName();
      }

      @Override
      public String getFieldName(Class<?> type, DerivedProperty derivedProperty) {
        return derivedProperty.getPropertyName();
      }
    },
    //    DATABASE, TODO implement when needed
    //    POJO,TODO implement when needed
    INDEX {
      @Override
      protected String getFieldName(Class<?> type, Field field) {
        Method method = getMethodByName(type, getGetterName(field));

        return getFieldName(method);
      }

      @Override
      public String getFieldName(Class<?> type, DerivedProperty derivedProperty) {
        Method method = getMethodByName(type, derivedProperty.getAccessor());

        return getFieldName(method);
      }

      private String getFieldName(Method method) {
        if (isAnnotationPresentOnMethod(method, IndexAnnotation.class)) {
          return method.getAnnotation(IndexAnnotation.class).fieldName();
        }

        return null;
      }
    };

    protected abstract String getFieldName(Class<?> type, Field field);

    protected abstract String getFieldName(Class<?> type, DerivedProperty derivedProperty);

    protected boolean isAnnotationPresentOnMethod(Method method, Class<? extends Annotation> annotation) {
      return method != null && method.isAnnotationPresent(annotation);
    }
  }
}