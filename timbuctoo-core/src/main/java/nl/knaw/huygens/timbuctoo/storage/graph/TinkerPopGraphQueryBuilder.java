package nl.knaw.huygens.timbuctoo.storage.graph;

import static nl.knaw.huygens.timbuctoo.storage.graph.tinkerpop.ElementFields.ELEMENT_TYPES;

import java.util.Map.Entry;

import nl.knaw.huygens.timbuctoo.config.TypeNames;
import nl.knaw.huygens.timbuctoo.model.Entity;
import nl.knaw.huygens.timbuctoo.storage.graph.tinkerpop.IsOfTypePredicate;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.GraphQuery;

class TinkerPopGraphQueryBuilder extends AbstractGraphQueryBuilder {
  private static final IsOfTypePredicate IS_OF_TYPE = new IsOfTypePredicate();
  private Graph db;

  public TinkerPopGraphQueryBuilder(Class<? extends Entity> type, PropertyBusinessRules businessRules, Graph db) {
    super(type, businessRules);
    this.db = db;
  }

  public GraphQuery build() {
    GraphQuery query = db.query();

    for (Entry<String, Object> entry : hasProperties.entrySet()) {
      query.has(getPropertyName(entry.getKey()), entry.getValue());
    }

    if (type != null) {
      query.has(ELEMENT_TYPES, IS_OF_TYPE, TypeNames.getInternalName(type));
    }

    return query;
  }

}