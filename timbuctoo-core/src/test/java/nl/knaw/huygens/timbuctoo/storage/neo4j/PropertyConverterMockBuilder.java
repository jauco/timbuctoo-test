package nl.knaw.huygens.timbuctoo.storage.neo4j;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PropertyConverterMockBuilder {
  private String name;
  private FieldType type;

  private PropertyConverterMockBuilder() {

  }

  public static PropertyConverterMockBuilder newPropertyConverter() {
    return new PropertyConverterMockBuilder();
  }

  public PropertyConverterMockBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public PropertyConverterMockBuilder withType(FieldType type) {
    this.type = type;
    return this;
  }

  public PropertyConverter build() {
    PropertyConverter fieldConverter = mock(PropertyConverter.class);

    when(fieldConverter.getName()).thenReturn(name);
    when(fieldConverter.getFieldType()).thenReturn(type);

    return fieldConverter;
  }

}