package nl.knaw.huygens.timbuctoo.search.description.sort;

import nl.knaw.huygens.timbuctoo.search.description.Property;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;

public class SortFieldDescription {
  private final String name;
  private final GraphTraversal<Object, Object> traversal;
  private final Comparable<?> defaultValue;
  private GraphTraversal<Object, Object> backUpTraversal;

  private SortFieldDescription(String name, GraphTraversal<Object, Object> traversal,
                       GraphTraversal<Object, Object> backUpTraversal,
                       Comparable<?> defaultValue) {
    this.name = name;
    this.traversal = traversal;
    this.backUpTraversal = backUpTraversal;

    this.defaultValue = defaultValue;
  }

  public static SortFieldDescriptionNameBuilder newSortFieldDescription() {
    return new BuilderSortFieldDescription();
  }

  public String getName() {
    return name;
  }

  @SuppressWarnings("unchecked")
  public GraphTraversal<Object, Object> getTraversal() {
    if (backUpTraversal == null) {
      return __.coalesce(traversal, __.map(x -> defaultValue));
    }
    return __.coalesce(traversal, backUpTraversal, __.map(x -> defaultValue));
  }

  public interface SortFieldDescriptionNameBuilder {
    SortFieldDescriptionDefaultValueBuilder withName(String name);
  }

  public interface SortFieldDescriptionDefaultValueBuilder {
    SortFieldDescriptionPropertyBuilder withDefaultValue(Comparable<?> value);
  }

  public interface SortFieldDescriptionPropertyBuilder {
    SortFieldDescriptionBackupPropertyBuilder withProperty(Property.PropertyBuilder property);
  }

  public interface SortFieldDescriptionBackupPropertyBuilder extends SortFieldDescriptionBuilder {
    SortFieldDescriptionBuilder withBackupProperty(Property.PropertyBuilder property);
  }

  public interface SortFieldDescriptionBuilder {
    SortFieldDescription build();
  }

  private static class BuilderSortFieldDescription implements SortFieldDescriptionNameBuilder,
    SortFieldDescriptionBuilder, SortFieldDescriptionPropertyBuilder, SortFieldDescriptionDefaultValueBuilder,
    SortFieldDescriptionBackupPropertyBuilder {
    private String name;
    private Comparable<?> value;
    private Property property;
    private Property otherProperty;

    private BuilderSortFieldDescription() {
    }

    public SortFieldDescriptionDefaultValueBuilder withName(String name) {
      this.name = name;
      return this;
    }

    @Override
    public SortFieldDescriptionPropertyBuilder withDefaultValue(Comparable<?> value) {
      this.value = value;
      return this;
    }

    @Override
    public SortFieldDescriptionBackupPropertyBuilder withProperty(Property.PropertyBuilder property) {
      this.property = property.build();
      return this;
    }

    @Override
    public SortFieldDescriptionBuilder withBackupProperty(Property.PropertyBuilder property) {
      this.otherProperty = property.build();
      return this;
    }

    public SortFieldDescription build() {
      if (otherProperty == null) {
        return new SortFieldDescription(name, property.getTraversal(), null, value);
      }
      return new SortFieldDescription(name, property.getTraversal(), otherProperty.getTraversal(), value);
    }

  }

}
