package nl.knaw.huygens.timbuctoo.storage.graph.tinkerpop;

import static com.tinkerpop.blueprints.Direction.IN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import nl.knaw.huygens.timbuctoo.storage.graph.SystemRelationType;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;

public class VertexMockBuilder {
  private Map<String, List<Edge>> incomingEdges;

  private VertexMockBuilder() {
    incomingEdges = Maps.newHashMap();
  }

  public static VertexMockBuilder aVertex() {
    return new VertexMockBuilder();
  }

  public VertexMockBuilder withIncomingEdgeWithLabel(Edge edge, String label) {
    addIncomingEdge(edge, label);
    return this;
  }

  public VertexMockBuilder withIncomingEdgeWithLabel(SystemRelationType label) {
    addIncomingEdge(mock(Edge.class), label.name());
    return this;
  }

  private void addIncomingEdge(Edge edge, String label) {
    List<Edge> edges = incomingEdges.get(label);
    if (edges == null) {
      edges = Lists.newArrayList();
      incomingEdges.put(label, edges);
    }

    edges.add(edge);

  }

  public Vertex build() {
    Vertex vertex = mock(Vertex.class);

    for (Entry<String, List<Edge>> entry : incomingEdges.entrySet()) {
      when(vertex.getEdges(IN, entry.getKey())).thenReturn(entry.getValue());
    }

    return vertex;
  }

}