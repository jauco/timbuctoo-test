package nl.knaw.huygens.timbuctoo.storage.neo4j;

import java.lang.reflect.Field;

import nl.knaw.huygens.timbuctoo.model.Entity;

import org.neo4j.graphdb.Node;

public interface FieldWrapper {

  public abstract void setContainingType(Class<? extends Entity> containingType);

  public abstract void setField(Field field);

  public abstract void addValueToNode(Node node, Entity entity) throws IllegalArgumentException, IllegalAccessException;

  /**
   * Extracts the value from the node and converts it so it can be added to the entity.
   * @param entity the entity to add the values to
   * @param node the node to retrieve the values from
   * @throws IllegalAccessException 
   * @throws IllegalArgumentException when the fieldType is different than the propertyType  
   */
  public abstract void addValueToEntity(Entity entity, Node node) throws IllegalArgumentException, IllegalAccessException;

  public abstract void setFieldType(FieldType fieldType);

  public abstract void setName(String fieldName);

}