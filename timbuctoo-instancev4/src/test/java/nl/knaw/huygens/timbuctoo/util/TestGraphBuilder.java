package nl.knaw.huygens.timbuctoo.util;

import org.apache.tinkerpop.gremlin.neo4j.structure.Neo4jGraph;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.neo4j.tinkerpop.api.impl.Neo4jGraphAPIImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TestGraphBuilder {

  private final List<VertexBuilder> vertexBuilders = new ArrayList<>();
  private final Map<String, VertexBuilder> identifiableVertexBuilders = new HashMap<>();

  private TestGraphBuilder() {
  }

  public static TestGraphBuilder newGraph() {
    return new TestGraphBuilder();
  }

  public Graph build() {
    GraphDatabaseService graphDatabaseService = new TestGraphDatabaseFactory().newImpermanentDatabase();
    Neo4jGraphAPIImpl neo4jGraphApi = new Neo4jGraphAPIImpl(graphDatabaseService);
    Neo4jGraph neo4jGraph = Neo4jGraph.open(neo4jGraphApi);
    Map<String, Vertex> identifiableVertices = new HashMap<>();
    //Create all identifiable vertices
    identifiableVertexBuilders.forEach(
      (key, builder) -> identifiableVertices.put(key, builder.build(neo4jGraph.addVertex()))
    );
    //now we can add the links
    identifiableVertexBuilders.forEach(
      (key, builder) -> builder.setRelations(identifiableVertices.get(key), identifiableVertices)
    );
    //finally add all the non-identifiable vertices
    vertexBuilders.forEach(
      (builder) -> {
        Vertex vertex = neo4jGraph.addVertex();
        builder.build(vertex);
        builder.setRelations(vertex, identifiableVertices);
      }
    );
    return neo4jGraph;
  }

  public TestGraphBuilder withVertex(String id, Consumer<VertexBuilder> vertexBuilderConfig) {
    if (identifiableVertexBuilders.containsKey(id)) {
      throw new RuntimeException("Key " + id + " is used twice");
    }
    VertexBuilder vb = new VertexBuilder();
    vertexBuilderConfig.accept(vb);
    identifiableVertexBuilders.put(id, vb);
    return this;
  }

  public TestGraphBuilder withVertex(Consumer<VertexBuilder> vertexBuilderConfig) {
    VertexBuilder vb = new VertexBuilder();
    vertexBuilderConfig.accept(vb);
    vertexBuilders.add(vb);
    return this;
  }
}
