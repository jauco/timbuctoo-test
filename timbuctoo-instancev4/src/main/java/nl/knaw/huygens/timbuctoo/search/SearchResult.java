package nl.knaw.huygens.timbuctoo.search;

import nl.knaw.huygens.timbuctoo.search.description.facet.Facet;

import java.util.List;
import java.util.UUID;

public class SearchResult {

  // TODO add id used to store the result

  private List<EntityRef> refs;
  private List<String> fullTextSearchFields;
  private List<String> sortableFields;
  private List<Facet> facets;
  private UUID id;

  public SearchResult(List<EntityRef> refs,
                      List<String> fullTextSearchFields,
                      List<String> sortableFields,
                      List<Facet> facets) {
    this.refs = refs;
    this.fullTextSearchFields = fullTextSearchFields;
    this.sortableFields = sortableFields;
    this.facets = facets;
  }

  public SearchResult(List<EntityRef> refs, SearchDescription description, List<Facet> facets) {
    this(refs, description.getFullTextSearchFields(), description.getSortableFields(), facets);
  }

  public List<EntityRef> getRefs() {
    return refs;
  }

  public List<String> getFullTextSearchFields() {
    return fullTextSearchFields;
  }

  public List<String> getSortableFields() {
    return sortableFields;
  }

  public List<Facet> getFacets() {
    return facets;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }
}
