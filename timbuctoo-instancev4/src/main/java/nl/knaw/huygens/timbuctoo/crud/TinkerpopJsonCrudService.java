package nl.knaw.huygens.timbuctoo.crud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javaslang.control.Try;
import nl.knaw.huygens.timbuctoo.logging.Logmarkers;
import nl.knaw.huygens.timbuctoo.model.properties.TimbuctooProperty;
import nl.knaw.huygens.timbuctoo.model.vre.Collection;
import nl.knaw.huygens.timbuctoo.model.vre.Vre;
import nl.knaw.huygens.timbuctoo.model.vre.Vres;
import nl.knaw.huygens.timbuctoo.security.AuthenticationUnavailableException;
import nl.knaw.huygens.timbuctoo.security.JsonBasedUserStore;
import nl.knaw.huygens.timbuctoo.security.User;
import nl.knaw.huygens.timbuctoo.server.GraphWrapper;
import nl.knaw.huygens.timbuctoo.util.Tuple;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Clock;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static nl.knaw.huygens.timbuctoo.crud.VertexDuplicator.duplicateVertex;
import static nl.knaw.huygens.timbuctoo.logging.Logmarkers.databaseInvariant;
import static nl.knaw.huygens.timbuctoo.model.properties.converters.Converters.arrayToEncodedArray;
import static nl.knaw.huygens.timbuctoo.util.JsonBuilder.jsn;
import static nl.knaw.huygens.timbuctoo.util.JsonBuilder.jsnA;
import static nl.knaw.huygens.timbuctoo.util.JsonBuilder.jsnO;
import static nl.knaw.huygens.timbuctoo.util.StreamIterator.stream;
import static nl.knaw.huygens.timbuctoo.util.Tuple.tuple;

public class TinkerpopJsonCrudService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(TinkerpopJsonCrudService.class);


  private final GraphWrapper graphwrapper;
  private final Vres mappings;
  private final HandleAdder handleAdder;
  private final UrlGenerator urlFor;
  private final UrlGenerator absoluteUrlFor;
  private final Clock clock;
  private final JsonNodeFactory nodeFactory;
  private final JsonBasedUserStore userStore;

  public TinkerpopJsonCrudService(GraphWrapper graphwrapper, Vres mappings,
                                  HandleAdder handleAdder, JsonBasedUserStore userStore, UrlGenerator urlFor,
                                  UrlGenerator absoluteUrlFor, Clock clock) {
    this.graphwrapper = graphwrapper;
    this.mappings = mappings;
    this.handleAdder = handleAdder;
    this.urlFor = urlFor;
    this.absoluteUrlFor = absoluteUrlFor;
    nodeFactory = JsonNodeFactory.instance;
    this.userStore = userStore;

    this.clock = clock;
  }

  public UUID create(String collectionName, ObjectNode input, String userId)
    throws InvalidCollectionException, IOException {

    Collection collection = mappings.get(collectionName);
    if (collection == null) {
      throw new InvalidCollectionException(collectionName);
    }
    if (collection.isRelationCollection()) {
      return createRelation(collection, input, userId);
    } else {
      return createEntity(collection, input, userId);
    }
  }

  private UUID createRelation(Collection collection, ObjectNode input, String userId) throws IOException {
    String entityTypeName = collection.getEntityTypeName();
    String abstractName = collection.getAbstractType();

    JsonNode accepted = input.get("accepted");
    JsonNode source = input.get("^sourceId");
    JsonNode target = input.get("^targetId");
    JsonNode type = input.get("^typeId");

    if (accepted == null || !accepted.isBoolean()) {
      throw new IOException("Accepted must be a boolean");
    }

    UUID id = UUID.randomUUID();

    Graph graph = graphwrapper.getGraph();
    GraphTraversalSource traversal = graph.traversal();
    try {
      Vertex sourceV = getEntity(traversal, UUID.fromString(source.asText("")), null).next();
      try {
        Vertex targetV = getEntity(traversal, UUID.fromString(target.asText("")), null).next();
        try {
          Vertex typeV = getEntity(traversal, UUID.fromString(type.asText("")), null).next();
          String label = getProp(typeV, "relationtype_regularName", String.class)
            .orElseThrow(() -> new IOException("Requested relation has no regular name"));
          try {
            sourceV.addEdge(label, targetV,
              "wwrelation_accepted", accepted.asBoolean(),
              "types", jsnA(jsn(entityTypeName), jsn(abstractName)).toString(),
              "typeId", type.asText(),
              "tim_id", id.toString(),
              "isLatest", true
            );
            //Make sure this is the last line of the method. We don't want to commit half our changes
            //this also means checking each function that we call to see if they don't call commit()
            graph.tx().commit();
            return id;
          } catch (IllegalArgumentException e) {
            throw new RuntimeException("The relation could not be created");
          }
        } catch (IllegalArgumentException | NoSuchElementException e) {
          throw new IOException("^typeId must contain a UUID pointing to an existing relationType");
        }
      } catch (IllegalArgumentException | NoSuchElementException e) {
        throw new IOException("^targetId must contain a UUID pointing to an existing entity");
      }
    } catch (IllegalArgumentException | NoSuchElementException e) {
      throw new IOException("^sourceId must contain a UUID pointing to an existing entity");
    }
  }

  private UUID createEntity(Collection collection, ObjectNode input, String userId) throws IOException {
    String collectionName = collection.getCollectionName();
    Map<String, TimbuctooProperty> mapping = collection.getProperties();

    UUID id = UUID.randomUUID();

    Graph graph = graphwrapper.getGraph();
    GraphTraversalSource traversalSource = graph.traversal();
    GraphTraversal<Vertex, Vertex> traversalWithVertex = traversalSource.addV();

    try {
      stream(input.fieldNames())
        .filter(fieldName -> !Objects.equals(fieldName, "@type"))
        .map(fieldName -> Try.of((Try.CheckedSupplier<GraphTraversal<?, ?>>) () -> {
          if (mapping.containsKey(fieldName)) {
            try {
              return mapping.get(fieldName).set(input.get(fieldName));
            } catch (IOException e) {
              throw new IOException(fieldName + " could not be saved. " + e.getMessage(), e);
            }
          } else {
            throw new IOException(String.format("Items of %s have no property %s", collectionName, fieldName));
          }
        }))
        .forEach(x -> x
          .onSuccess(v->traversalWithVertex.union(v))
          .onFailure(e -> {
            throw new RuntimeException(e);
          })
        );
    } catch (RuntimeException e) {
      throw new IOException(e.getCause());
    }

    Vertex vertex = traversalWithVertex.next();

    vertex.property("tim_id", id.toString());
    vertex.property("rev", 1);
    vertex.property("types", String.format(
      "[\"%s\", \"%s\"]",
      collection.getEntityTypeName(),
      collection.getAbstractType()
    ));
    setCreated(vertex, userId);

    duplicateVertex(graph, vertex);
    //Make sure this is the last line of the method. We don't want to commit if an exception happens halfway
    //the return statement below should return a variable directly without any additional logic
    graph.tx().commit();

    //but out of process commands that require our changes need to come after a commit of course :)
    handleAdder.add(new HandleAdderParameters(id, 1, urlFor.apply(collectionName, id, 1)));
    return id;
  }

  public JsonNode get(String collectionName, UUID id) throws InvalidCollectionException, NotFoundException {
    return get(collectionName, id, null);
  }

  public JsonNode get(String collectionName, UUID id, Integer rev)
    throws InvalidCollectionException, NotFoundException {

    final Collection collection = mappings.get(collectionName);
    if (collection == null) {
      throw new InvalidCollectionException(collectionName);
    }
    final Map<String, TimbuctooProperty> mapping = collection.getProperties();
    final String entityTypeName = collection.getEntityTypeName();
    final GraphTraversalSource traversalSource = graphwrapper.getGraph().traversal();

    final ObjectNode result = JsonNodeFactory.instance.objectNode();

    result.set("@type", nodeFactory.textNode(entityTypeName));
    result.set("_id", nodeFactory.textNode(id.toString()));

    GraphTraversal<Vertex, Vertex> entityT = getEntity(traversalSource, id, rev);
    if (!entityT.asAdmin().clone().hasNext()) {
      throw new NotFoundException();
    }

    GraphTraversal[] propertyGetters = mapping
      .entrySet().stream()
      //append error handling and result handling to the traversal
      .map(prop -> prop.getValue().get().get().sideEffect(x ->
        x.get()
          .onSuccess( node -> result.set(prop.getKey(), node))
          .onFailure( e -> {
            if (e.getCause() instanceof IOException) {
              LOG.error(
                databaseInvariant,
                "Property '" + prop.getKey() + "' is not encoded correctly",
                e.getCause()
              );
            } else {
              LOG.error("Something went wrong while reading the property '" + prop.getKey() + "'.", e.getCause());
            }
          })
      ))
      .toArray(GraphTraversal[]::new);

    entityT.asAdmin().clone().union(propertyGetters).forEachRemaining(x -> {
      //Force side effects to happen
    });
    Vertex entity = entityT.asAdmin().clone().next();

    String entityTypesStr = getProp(entity, "types", String.class).orElse("[]");
    if (!entityTypesStr.contains("\"" + collection.getEntityTypeName() + "\"")) {
      throw new NotFoundException();
    }

    Tuple<ObjectNode, Long> relations = getRelations(entity, traversalSource, collection);
    result.set("@relationCount", nodeFactory.numberNode(relations.getRight()));
    result.set("@relations", relations.getLeft());

    result.set(
      "^rev", nodeFactory.numberNode(
        getProp(entity, "rev", Integer.class)
          .orElse(rev == null ? -1 : rev)
      )
    );
    getModification(entity, "modified").ifPresent(val -> result.set("^modified", val));
    getModification(entity, "created").ifPresent(val -> result.set("^created", val));

    result.set("@variationRefs", getVariationRefs(entity, id, entityTypeName));

    result.set("^deleted", nodeFactory.booleanNode(getProp(entity, "deleted", Boolean.class).orElse(false)));

    getProp(entity, "pid", String.class)
      .ifPresent(pid -> result.set("^pid", nodeFactory.textNode(pid)));

    return result;
  }

  private Tuple<ObjectNode, Long> getRelations(Vertex entity, GraphTraversalSource traversalSource,
                                               Collection collection) {
    final ObjectMapper mapper = new ObjectMapper();

    final long[] relationCount = new long[1];

    GraphTraversal<Vertex, ObjectNode> realRelations = getRealRelations(entity, traversalSource, collection);
    GraphTraversal<Vertex, ObjectNode> derivedRelations = getDerivedRelations(entity, traversalSource, collection);

    Map<String, List<ObjectNode>> relations = concat(stream(realRelations), stream(derivedRelations))
      .filter(x->x != null)
      .peek(x -> relationCount[0]++)
      .collect(groupingBy(jsn -> jsn.get("relationType").asText()));

    return new Tuple<>(mapper.valueToTree(relations), relationCount[0]);
  }

  private GraphTraversal<Vertex, ObjectNode> getDerivedRelations(Vertex entity, GraphTraversalSource traversalSource,
                                                                 Collection collection) {
    return traversalSource.withSack("").V(entity.id())
      .union(
        collection.getDerivedRelations().entrySet().stream().map(entry -> {
          return __.sack((left,right) -> entry.getKey()).union(entry.getValue().get()).as("targetVertex");
        }).toArray(GraphTraversal[]::new)
      )
      .sack().as("sack")
      .select("targetVertex", "sack")
      .map((Function<Traverser<Map<String, Object>>, ObjectNode>) r -> {
        try {
          Vertex vertex = (Vertex) r.get().get("targetVertex");
          String relationType = (String) r.get().get("sack");
          String targetType = getEntityType(collection, vertex);
          if (targetType == null) {
            //this means that the vertex is of another VRE
            throw new IOException(
              "The derived relation " + relationType + " points to vertex " + vertex + " which is not part of this vre"
            );
          }
          String targetCollection = targetType + "s";
          String uuid = getProp(vertex, "tim_id", String.class).orElse("");
          String displayName = getDisplayname(traversalSource, vertex, collection.getVre().getCollection(targetType))
            .orElse("<No displayname found>");

          return jsnO(
            tuple("id", jsn(uuid)),
            tuple("path", jsn(urlFor.apply(targetCollection, UUID.fromString(uuid), null).toString())),
            tuple("type", jsn(relationType)),
            tuple("relationType", jsn(relationType)),
            tuple("accepted", jsn(true)),
            tuple("relationId", jsn("derived")),
            tuple("rev", jsn(1)),
            tuple("displayName", jsn(displayName))
          );
        } catch (Exception e) {
          LOG.error(Logmarkers.databaseInvariant, "Error while generating derived-relation data", e);
          return null;
        }
      });
  }

  private GraphTraversal<Vertex, ObjectNode> getRealRelations(Vertex entity, GraphTraversalSource traversalSource,
                                                              Collection collection) {
    final Vre vre = collection.getVre();

    Object[] relationTypes = traversalSource.V().has("relationtype_regularName").id().toList().toArray();

    return traversalSource.V(entity.id())
        .union(
          __.outE().as("edge")
            .label().as("label")
            .select("edge"),
          __.inE()
            .as("edge")
            .label().as("edgeLabel")
            .V(relationTypes)
            .has("relationtype_regularName", __.where(P.eq("edgeLabel")))
            .properties("relationtype_inverseName").value()
            .as("label")
            .select("edge")
        )
        .where(
          //FIXME move to strategy
          __.has("isLatest", true)
            .not(__.has("deleted", true))
            .not(__.hasLabel("VERSION_OF"))
            .has("types", new P<>(
              (val, def) -> {
                return Try.of(() -> arrayToEncodedArray.tinkerpopToJava(val, String[].class))
                  .map(vre::getOwnType)
                  .map(ownType -> ownType != null)
                  .onFailure(e -> LOG.error(databaseInvariant, "Error reading 'types' of edge", e))
                  .getOrElse(false);
              }, //if the types array is a failure then pretend the relation does not exist
              "")
            )
        )
        .otherV().as("vertex")
        .select("edge", "vertex", "label")
        .map(r -> {
          try {
            Map<String, Object> val = r.get();
            Edge edge = (Edge) val.get("edge");
            Vertex vertex = (Vertex) val.get("vertex");
            String label = (String) val.get("label");

            String targetEntityType = getEntityType(collection, vertex);
            if (targetEntityType == null) {
              //this means that the edge is of this VRE, but the Vertex it points to is of another VRE
              throw new IOException(
                String.format("Edge %s that is of this vre points to vertex %s that is not of this vre", edge, vertex)
              );
            }

            String displayName = getDisplayname(traversalSource, vertex, vre.getCollection(targetEntityType))
              .orElse("<No displayname found>");
            String targetCollection = targetEntityType + "s";
            String uuid = getProp(vertex, "tim_id", String.class).orElse("");

            return jsnO(
              tuple("id", jsn(uuid)),
              tuple("path", jsn(urlFor.apply(targetCollection, UUID.fromString(uuid), null).toString())),
              tuple("relationType", jsn(label)),
              tuple("type", jsn(targetEntityType)),
              tuple("accepted", jsn(getProp(edge, "accepted", Boolean.class).orElse(true))),
              tuple("relationId", getProp(edge, "tim_id", String.class).map(x -> (JsonNode) jsn(x)).orElse(jsn())),
              tuple("rev", jsn(getProp(edge, "rev", Integer.class).orElse(1))),
              tuple("displayName", jsn(displayName))
            );
          } catch (Exception e) {
            LOG.error(databaseInvariant, "Something went wrong while formatting the entity", e);
            return null;
          }
        });
  }

  private Optional<String> getDisplayname(GraphTraversalSource traversalSource, Vertex vertex,
                                          Collection targetCollection) {
    TimbuctooProperty displayNameProperty = targetCollection.getDisplayName();
    if (displayNameProperty != null) {
      GraphTraversal<Vertex, Try<JsonNode>> displayNameGetter = traversalSource.V(vertex.id()).union(
        targetCollection.getDisplayName().get().get()
      );
      if (displayNameGetter.hasNext()) {
        Try<JsonNode> traversalResult = displayNameGetter.next();
        if (!traversalResult.isSuccess()) {
          LOG.error(databaseInvariant, "Retrieving displayname failed", traversalResult.getCause());
        } else {
          if (traversalResult.get() == null) {
            LOG.error(databaseInvariant, "Displayname was null");
          } else {
            if (!traversalResult.get().isTextual()) {
              LOG.error(databaseInvariant, "Displayname was not a string");
            } else {
              return Optional.of(traversalResult.get().asText());
            }
          }
        }
      } else {
        LOG.error(databaseInvariant, "Displayname traversal resulted in no results: " + displayNameGetter);
      }
    } else {
      LOG.warn("No displayname configured for " + targetCollection.getEntityTypeName());
    }
    return Optional.empty();
  }

  /* returns the entitytype for the current collection's vre or else the type of the current collection */
  private String getEntityType(Collection collection, Element vertex) throws IOException {
    final Vre vre = collection.getVre();
    String encodedEntityTypes = getProp(vertex, "types", String.class)
      .orElse("[\"" + collection.getEntityTypeName() +  "\"]");
    String[] entityTypes = arrayToEncodedArray.tinkerpopToJava(encodedEntityTypes, String[].class);
    return vre.getOwnType(entityTypes);
  }

  private ArrayNode getVariationRefs(Vertex entity, UUID id, String entityTypeName) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      ArrayNode variationRefs = nodeFactory.arrayNode();
      JsonNode types = mapper.readTree((String) entity.value("types"));
      if (!types.isArray()) {
        throw new IOException("types should be a JSON encoded Array");
      }
      for (int i = 0; i < types.size(); i++) {
        ObjectNode ref = nodeFactory.objectNode();
        ref.set("id", nodeFactory.textNode(id.toString()));
        ref.set("type", nodeFactory.textNode(types.get(i).asText()));
        variationRefs.add(ref);
      }
      return variationRefs;
    } catch (Exception e) {
      //When something goes wrong we log the error and return a functional representation
      LOG.error(databaseInvariant, "Error while generating variation refs", e);
      return jsnA(
        jsnO(
          "id", jsn(id.toString()),
          "type", jsn(entityTypeName)
        )
      );
    }
  }

  private Optional<ObjectNode> getModification(Vertex entity, String propertyName) {
    ObjectMapper mapper = new ObjectMapper();
    return getProp(entity, propertyName, String.class)
      .flatMap(content -> {
        try {
          return Optional.of(mapper.readTree(content));
        } catch (IOException e) {
          return Optional.empty();
        }
      })
      .flatMap(parsed -> parsed instanceof ObjectNode ? Optional.of((ObjectNode) parsed) : Optional.empty())
      .map(modifiedObj -> {
        try {
          userStore.userForId(modifiedObj.get("userId").asText(""))
                   .map(User::getDisplayName)
                   .ifPresent(userName -> modifiedObj.set("username", nodeFactory.textNode(userName)));
        } catch (AuthenticationUnavailableException e) {
          LOG.error(Logmarkers.serviceUnavailable, "could not get user for modifiedObj", e);
          modifiedObj.set("username", nodeFactory.nullNode());
        }
        return modifiedObj;
      });
  }

  //copies: 1
  public <V> Optional<V> getProp(final Element vertex, final String key, Class<? extends V> clazz) {
    try {
      Iterator<? extends Property<Object>> revProp = vertex.properties(key);
      if (revProp.hasNext()) {
        return Optional.of(clazz.cast(revProp.next().value()));
      } else {
        return Optional.empty();
      }
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  private GraphTraversal<Vertex, Vertex> getEntity(GraphTraversalSource source, UUID id, Integer rev) {
    if (rev == null) {
      return source
        .V()
        .has("tim_id", id.toString())
        .not(__.has("deleted", true))
        .has("isLatest", true);
    }
    return source
      .V()
      .has("tim_id", id.toString())
      .has("rev", rev)
      .not(__.has("deleted", true))
      .has("isLatest", false);
  }

  private void setCreated(Vertex vertex, String userId) {
    String value = String.format("{\"timeStamp\":%s,\"userId\":%s}",
      clock.millis(),
      nodeFactory.textNode(userId)
    );
    vertex.property("created", value);
    vertex.property("modified", value);
  }

  private void setModified(Vertex vertex, String userId) {
    String value = String.format("{\"timeStamp\":%s,\"userId\":%s}",
      clock.millis(),
      nodeFactory.textNode(userId)
    );
    vertex.property("modified", value);
  }

  public void replace(String collectionName, UUID id, ObjectNode data, String userId)
    throws InvalidCollectionException, IOException, NotFoundException, AlreadyUpdatedException {

    final Collection collection = mappings.get(collectionName);
    if (collection == null) {
      throw new InvalidCollectionException(collectionName);
    }
    if (collection.isRelationCollection()) {
      replaceRelation(collection, id, data, userId);
    } else {
      replaceEntity(collection, id, data, userId);
    }
  }

  private void replaceRelation(Collection collection, UUID id, ObjectNode data, String userId) throws IOException {
    String acceptedPropName = collection.getEntityTypeName() + "_accepted";

    JsonNode accepted = data.get("accepted");
    if (accepted == null || !accepted.isBoolean()) {
      throw new IOException("accepted must be a boolean");
    }
    JsonNode rev = data.get("^rev");
    if (rev == null || !rev.isNumber()) {
      throw new IOException("^rev must be a number");
    }

    Graph graph = graphwrapper.getGraph();
    Edge edge = graph.traversal().E()
      .has("tim_id", id.toString())
      .has("isLatest", true)
      .has("rev", rev.intValue())
      .next();

    edge.property(acceptedPropName, accepted.booleanValue());

    //Make sure this is the last line of the method. We don't want to commit half our changes
    //this also means checking each function that we call to see if they don't call commit()
    graph.tx().commit();
  }

  private void replaceEntity(Collection collection, UUID id, ObjectNode data, String userId)
    throws NotFoundException, IOException, AlreadyUpdatedException {

    final Graph graph = graphwrapper.getGraph();
    final GraphTraversalSource traversalSource = graph.traversal();

    GraphTraversal<Vertex, Vertex> entityTraversal = getEntity(traversalSource, id, null);

    if (!entityTraversal.hasNext()) {
      throw new NotFoundException();
    }
    Vertex entity = entityTraversal.next();
    int curRev = getProp(entity, "rev", Integer.class).orElse(1);
    if (data.get("^rev") == null) {
      throw new IOException("data object should have a ^rev property indicating the revision this update was based on");
    }
    if (curRev != data.get("^rev").asInt()) {
      throw new AlreadyUpdatedException();
    }

    int newRev = curRev + 1;
    entity.property("rev", newRev);

    String entityTypesStr = getProp(entity, "types", String.class).orElse("[]");
    if (!entityTypesStr.contains("\"" + collection.getEntityTypeName() + "\"")) {
      try {
        ArrayNode entityTypes = arrayToEncodedArray.tinkerpopToJson(entityTypesStr);
        entityTypes.add(collection.getEntityTypeName());
        entity.property("types", entityTypes.toString());
      } catch (IOException e) {
        LOG.error(Logmarkers.databaseInvariant, "property 'types' was not parseable: " + entityTypesStr);
      }
    }

    final Map<String, TimbuctooProperty> collectionProperties = collection.getProperties();
    final GraphTraversal[] setters = new GraphTraversal[collectionProperties.size()];

    final List<String> dataFields = stream(data.fieldNames())
      .filter(x -> !Objects.equals(x, "@type"))
      .filter(x -> !Objects.equals(x, "_id"))
      .filter(x -> !Objects.equals(x, "^rev"))
      .collect(toList());

    final List<String> omittedProperties = collectionProperties
      .keySet().stream()
      .filter(key -> !dataFields.contains(key))
      .collect(toList());

    for (int i = 0; i < dataFields.size(); i++) {
      String name = dataFields.get(i);
      if (!collectionProperties.containsKey(name)) {
        throw new IOException(name + " is not a valid property");
      }
      try {
        setters[i] = collectionProperties.get(name).set(data.get(name));
      } catch (IOException e) {
        throw new IOException(name + " could not be saved. " + e.getMessage(), e);
      }
    }

    for (int i = 0; i < omittedProperties.size(); i++) {
      String name = omittedProperties.get(i);
      setters[dataFields.size() + i] = collectionProperties.get(name).set(null);
    }

    traversalSource
      .V(entity.id())
      .union(setters)
      .forEachRemaining(x-> {
        //trigger side effects
      });

    setModified(entity, userId);
    duplicateVertex(graph, entity);

    //Make sure this is at the last line of the method. We don't want to commit half our changes
    //this also means checking each function that we call to see if they don't call commit()
    graph.tx().commit();

    //but out of process commands that require our changes need to come after a commit of course :)
    handleAdder.add(new HandleAdderParameters(id, newRev, urlFor.apply(collection.getCollectionName(), id, newRev)));
  }

  public ArrayNode autoComplete(String collectionName, Optional<String> tokenParam, Optional<String> type)
    throws InvalidCollectionException {

    LOG.info(collectionName + " " + tokenParam + " " + type);
    final Collection collection = mappings.get(collectionName);
    if (collection == null) {
      throw new InvalidCollectionException(collectionName);
    }
    final Graph graph = graphwrapper.getGraph();
    final GraphTraversalSource traversalSource = graph.traversal();

    GraphTraversal<Vertex, Vertex> typeFilter;
    if (type.isPresent()) {
      typeFilter = __.has("keyword_type", type.get());
    } else {
      typeFilter = __.identity();
    }

    List<ObjectNode> results;
    if (tokenParam.isPresent()) {
      String token = tokenParam.get();
      if (token.startsWith("*")) {
        token = token.substring(1);
      }
      if (token.endsWith("*")) {
        token = token.substring(0, token.length() - 1);
      }
      final String searchToken = token.toLowerCase();

      results = traversalSource.V()
        .as("vertex")
        .where(typeFilter)
        .union(collection.getDisplayName().get().get())
        .filter(x -> x.get().isSuccess())
        .map(x -> x.get().get().asText())
        .as("displayName")
        .filter(x -> x.get().toLowerCase().contains(searchToken))
        .select("vertex", "displayName")
        .map(x -> {
          Vertex vertex = (Vertex) x.get().get("vertex");
          String dn = (String) x.get().get("displayName");
          Optional<String> id = getProp(vertex, "tim_id", String.class);
          Integer rev = getProp(vertex, "rev", Integer.class).orElse(1);
          if (id.isPresent()) {
            try {
              UUID uuid = UUID.fromString(id.get());
              return jsnO(
                "key", jsn(absoluteUrlFor.apply(collection.getCollectionName(), uuid, rev).toString()),
                "value", jsn(dn)
              );
            } catch (IllegalArgumentException e) {
              LOG.error(Logmarkers.databaseInvariant, "Tim_id " + id + "is not a valid UUID");
              return null;
            }
          } else {
            LOG.error(Logmarkers.databaseInvariant, "No Tim_id found on vertex with id " + vertex.id());
            return null;
          }
        })
        .filter(x -> x != null)
        .limit(10L)
        .toList();
    } else {
      results = traversalSource.V()
        .as("vertex")
        .where(typeFilter)
        .union(collection.getDisplayName().get().get())
        .filter(x -> x.get().isSuccess())
        .map(x -> x.get().get().asText())
        .as("displayName")
        .select("vertex", "displayName")
        .map(x -> {
          Vertex vertex = (Vertex) x.get().get("vertex");
          String dn = (String) x.get().get("displayName");
          Optional<String> id = getProp(vertex, "tim_id", String.class);
          Integer rev = getProp(vertex, "rev", Integer.class).orElse(1);
          if (id.isPresent()) {
            try {
              UUID uuid = UUID.fromString(id.get());
              return jsnO(
                "key", jsn(absoluteUrlFor.apply(collection.getCollectionName(), uuid, rev).toString()),
                "value", jsn(dn)
              );
            } catch (IllegalArgumentException e) {
              LOG.error(Logmarkers.databaseInvariant, "Tim_id " + id + "is not a valid UUID");
              return null;
            }
          } else {
            LOG.error(Logmarkers.databaseInvariant, "No Tim_id found on vertex with id " + vertex.id());
            return null;
          }
        })
        .filter(x -> x != null)
        .limit(1000L) //no query means you get a lot
        .toList();
    }


    ArrayNode resultNode = jsnA();
    for (ObjectNode n : results) {
      resultNode.add(n);
    }

    return resultNode;
  }

  public void delete(String collectionName, UUID id, String userId)
    throws InvalidCollectionException, NotFoundException {

    final Collection collection = mappings.get(collectionName);
    if (collection == null) {
      throw new InvalidCollectionException(collectionName);
    }
    final Graph graph = graphwrapper.getGraph();
    final GraphTraversalSource traversalSource = graph.traversal();
    GraphTraversal<Vertex, Vertex> entityTraversal = getEntity(traversalSource, id, null);

    if (!entityTraversal.hasNext()) {
      throw new NotFoundException();
    }

    Vertex entity = entityTraversal.next();
    String entityTypesStr = getProp(entity, "types", String.class).orElse("[]");
    if (entityTypesStr.contains("\"" + collection.getEntityTypeName() + "\"")) {
      try {
        ArrayNode entityTypes = arrayToEncodedArray.tinkerpopToJson(entityTypesStr);
        if (entityTypes.size() == 1) {
          entity.property("deleted", true);
        } else {
          for (int i = entityTypes.size() - 1; i >= 0; i--) {
            JsonNode val = entityTypes.get(i);
            if (val != null && val.asText("").equals(collection.getEntityTypeName())) {
              entityTypes.remove(i);
            }
          }
          entity.property("types", entityTypes.toString());
        }
      } catch (IOException e) {
        LOG.error(Logmarkers.databaseInvariant, "property 'types' was not parseable: " + entityTypesStr);
      }
    } else {
      throw new NotFoundException();
    }
    int newRev = getProp(entity, "rev", Integer.class).orElse(1) + 1;
    entity.property("rev", newRev);

    entity.edges(Direction.BOTH).forEachRemaining(edge -> {
      try {
        String entityType = getEntityType(collection, edge);
        if (entityType != null) {
          edge.property(entityType + "_accepted", false);
        }
      } catch (IOException e) {
        LOG.error(Logmarkers.databaseInvariant, "property 'types' was not parseable");
      }
    });

    setModified(entity, userId);
    duplicateVertex(graph, entity);
    handleAdder.add(new HandleAdderParameters(id, newRev, urlFor.apply(collectionName, id, newRev)));

    //Make sure this is the last line of the method. We don't want to commit half our changes
    //this also means checking each function that we call to see if they don't call commit()
    graph.tx().commit();
  }
}
