package nl.knaw.huygens.timbuctoo.storage.graph.tinkerpop;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import nl.knaw.huygens.timbuctoo.storage.graph.SystemRelationType;
import org.junit.Before;
import org.junit.Test;

import static nl.knaw.huygens.timbuctoo.model.Entity.DB_ID_PROP_NAME;
import static nl.knaw.huygens.timbuctoo.model.Entity.DB_REV_PROP_NAME;
import static nl.knaw.huygens.timbuctoo.storage.graph.tinkerpop.EdgeMockBuilder.anEdge;
import static nl.knaw.huygens.timbuctoo.storage.graph.tinkerpop.ElementFields.IS_LATEST;
import static nl.knaw.huygens.timbuctoo.storage.graph.tinkerpop.VertexMockBuilder.aVertex;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VertexDuplicatorTest {
  private static final String VERSION_OF_EDGE_LABEL = SystemRelationType.VERSION_OF.name();
  private static final String OUTGOING_EDGE_LABEL = "outgoing";
  private static final String INCOMING_EDGE_LABEL = "incomming";
  private static final int REV = 1;
  private static final String ID = "id";
  private Graph dbMock;
  private Vertex vertexToDuplicate;
  private Vertex duplicate;
  private VertexDuplicator instance;
  private Vertex otherVertex;
  private Vertex sourceIncomingVersionOfEdge;
  private Edge incomingEdge;
  private Edge outgoingEdge;
  private Edge versionOfOutgoingEdge;
  private Edge versionOfIncomingEdge;

  @Before
  public void setup() {
    sourceIncomingVersionOfEdge = aVertex().build();
    otherVertex = aVertex().build();
    vertexToDuplicate = setupVertexToDuplicate();
    duplicate = aVertex().build();
    dbMock = mock(Graph.class);
    when(dbMock.addVertex(null)).thenReturn(duplicate);

    instance = new VertexDuplicator(dbMock);
  }

  private Vertex setupVertexToDuplicate() {
    incomingEdge = anEdge().withLabel(INCOMING_EDGE_LABEL).withSource(otherVertex).withTarget(vertexToDuplicate).build();
    outgoingEdge = anEdge().withLabel(OUTGOING_EDGE_LABEL).withSource(vertexToDuplicate).withTarget(otherVertex).build();
    versionOfOutgoingEdge = anEdge().withLabel(VERSION_OF_EDGE_LABEL).withSource(otherVertex).withTarget(vertexToDuplicate).build();
    versionOfIncomingEdge = anEdge().withLabel(VERSION_OF_EDGE_LABEL).withSource(sourceIncomingVersionOfEdge).withTarget(otherVertex).build();

    return aVertex() //
      .withId(ID) //
      .withRev(REV) //
      .withIncomingEdgeWithLabel(INCOMING_EDGE_LABEL, incomingEdge) //
      .withIncomingEdgeWithLabel(VERSION_OF_EDGE_LABEL, versionOfIncomingEdge) //
      .withOutgoingEdgeWithLabel(VERSION_OF_EDGE_LABEL, versionOfOutgoingEdge)//
      .withOutgoingEdgeWithLabel(OUTGOING_EDGE_LABEL, outgoingEdge)//
      .build();
  }

  @Test
  public void duplicateCopiesAllThePropertiesOfTheNode() {
    // action
    instance.duplicate(vertexToDuplicate);

    // verify
    verifyIdIsSet(duplicate);
    verifyRevIsSet(duplicate);

  }

  private void verifyRevIsSet(Vertex duplicate) {
    verify(duplicate).setProperty(DB_REV_PROP_NAME, REV);
  }

  private void verifyIdIsSet(Vertex duplicate) {
    verify(duplicate).setProperty(DB_ID_PROP_NAME, ID);
  }

  @Test
  public void duplicateCopiesAllTheEdgesOfTheNodeExceptIsVersionOf() {
    // action
    instance.duplicate(vertexToDuplicate);

    // verify
    verify(duplicate).addEdge(OUTGOING_EDGE_LABEL, otherVertex);
    verify(otherVertex).addEdge(INCOMING_EDGE_LABEL, duplicate);
    verify(duplicate, times(0)).addEdge(VERSION_OF_EDGE_LABEL, otherVertex);
    verify(sourceIncomingVersionOfEdge, times(0)).addEdge(VERSION_OF_EDGE_LABEL, duplicate);
  }

  @Test
  public void duplicateRemovesTheEdgesOfTheDuplicatedVertexExceptIsVersionOf() {
    // action
    instance.duplicate(vertexToDuplicate);

    // verify
    verify(incomingEdge).remove();
    verify(outgoingEdge).remove();
    verify(versionOfIncomingEdge, never()).remove();
    verify(versionOfOutgoingEdge, never()).remove();
  }

  @Test
  public void duplicateCreatesAVersionOfEdgeBetweenTheDuplicateAndTheOriginal() {
    // action
    instance.duplicate(vertexToDuplicate);

    // verify
    verify(vertexToDuplicate).addEdge(VERSION_OF_EDGE_LABEL, duplicate);
  }


  @Test
  public void duplicateReturnsTheDuplicateVertex() {
    // action
    Vertex actualDuplicate = instance.duplicate(vertexToDuplicate);

    // verify 
    assertThat(actualDuplicate, is(sameInstance(duplicate)));
  }

  @Test
  public void duplicateAddsIsLatestPropertyAndSetsItToFalseOnTheVertextToDuplicate(){
    // action
    instance.duplicate(vertexToDuplicate);

    // verify
    verify(duplicate).setProperty(IS_LATEST, true);
    verify(vertexToDuplicate).setProperty(IS_LATEST, false);
  }


}
