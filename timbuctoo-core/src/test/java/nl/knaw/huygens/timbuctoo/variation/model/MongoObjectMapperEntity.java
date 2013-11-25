package nl.knaw.huygens.timbuctoo.variation.model;

import java.util.List;

import nl.knaw.huygens.timbuctoo.annotations.IDPrefix;
import nl.knaw.huygens.timbuctoo.facet.IndexAnnotation;
import nl.knaw.huygens.timbuctoo.model.SystemEntity;
import nl.knaw.huygens.timbuctoo.model.util.Datable;
import nl.knaw.huygens.timbuctoo.model.util.PersonName;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@IDPrefix("TSTD")
public class MongoObjectMapperEntity extends SystemEntity {

  private List<String> primitiveTestCollection;
  // Will not serialize if non-null
  private List<? extends SystemEntity> nonPrimitiveTestCollection;
  private String name;
  private String testValue1;
  private String testValue2;
  @JsonProperty("propAnnotated")
  private String annotatedProperty;
  private String propWithAnnotatedAccessors;
  private Class<?> type;
  private Datable date;
  private PersonName personName;

  @Override
  @JsonIgnore
  @IndexAnnotation(fieldName = "desc")
  public String getDisplayName() {
    return name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTestValue1() {
    return testValue1;
  }

  public void setTestValue1(String testValue1) {
    this.testValue1 = testValue1;
  }

  public String getTestValue2() {
    return testValue2;
  }

  public void setTestValue2(String testValue2) {
    this.testValue2 = testValue2;
  }

  public String getAnnotatedProperty() {
    return annotatedProperty;
  }

  public void setAnnotatedProperty(String annotatedProperty) {
    this.annotatedProperty = annotatedProperty;
  }

  @JsonProperty("pwaa")
  public String getPropWithAnnotatedAccessors() {
    return propWithAnnotatedAccessors;
  }

  @JsonProperty("pwaa")
  public void setPropWithAnnotatedAccessors(String propWithAnnotatedAccessors) {
    this.propWithAnnotatedAccessors = propWithAnnotatedAccessors;
  }

  public List<String> getPrimitiveTestCollection() {
    return primitiveTestCollection;
  }

  public void setPrimitiveTestCollection(List<String> primitiveTestCollection) {
    this.primitiveTestCollection = primitiveTestCollection;
  }

  public List<? extends SystemEntity> getNonPrimitiveTestCollection() {
    return nonPrimitiveTestCollection;
  }

  public void setNonPrimitiveTestCollection(List<? extends SystemEntity> nonPrimitiveTestCollection) {
    this.nonPrimitiveTestCollection = nonPrimitiveTestCollection;
  }

  public Class<?> getType() {
    return type;
  }

  public void setType(Class<?> type) {
    this.type = type;
  }

  public Datable getDate() {
    return date;
  }

  public void setDate(Datable date) {
    this.date = date;
  }

  public PersonName getPersonName() {
    return personName;
  }

  public void setPersonName(PersonName personName) {
    this.personName = personName;
  }

}
