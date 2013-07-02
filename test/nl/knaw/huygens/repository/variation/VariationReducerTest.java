package nl.knaw.huygens.repository.variation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import nl.knaw.huygens.repository.VariationHelper;
import nl.knaw.huygens.repository.config.DocTypeRegistry;
import nl.knaw.huygens.repository.model.Document;
import nl.knaw.huygens.repository.model.Reference;
import nl.knaw.huygens.repository.storage.mongo.MongoChanges;
import nl.knaw.huygens.repository.storage.mongo.MongoDiff;
import nl.knaw.huygens.repository.variation.model.GeneralTestDoc;
import nl.knaw.huygens.repository.variation.model.TestConcreteDoc;
import nl.knaw.huygens.repository.variation.model.TestInheritsFromTestBaseDoc;
import nl.knaw.huygens.repository.variation.model.projecta.ProjectAGeneralTestDoc;
import nl.knaw.huygens.repository.variation.model.projectb.ProjectBGeneralTestDoc;
import nl.knaw.huygens.repository.variation.model.projectb.TestDoc;

import org.junit.Before;
import org.junit.Test;
import org.mongojack.internal.stream.JacksonDBObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.mongodb.DBObject;

public class VariationReducerTest {

  private static final String TEST_ID = "id0000000001";
  private VariationReducer reducer;
  private ObjectMapper m;

  @Before
  public void setUp() {
    DocTypeRegistry registry = mock(DocTypeRegistry.class);
    Set<Class<? extends Document>> types = Sets.newHashSet();
    types.add(TestConcreteDoc.class);
    types.add(TestInheritsFromTestBaseDoc.class);
    types.add(GeneralTestDoc.class);
    types.add(ProjectAGeneralTestDoc.class);
    types.add(ProjectBGeneralTestDoc.class);
    doReturn(types).when(registry).getDocumentTypes();

    reducer = new VariationReducer(registry);
    m = new ObjectMapper();
  }

  @Test
  public void testReduce() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"blub\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"blub\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]}], \"!defaultVRE\":\"blub\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    ProjectAGeneralTestDoc val = reducer.reduce(t, ProjectAGeneralTestDoc.class);
    ProjectAGeneralTestDoc testVal = new ProjectAGeneralTestDoc();
    testVal.name = "a";
    testVal.setId(TEST_ID);
    testVal.generalTestDocValue = "a";
    testVal.projectAGeneralTestDocValue = "test";
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", null));
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test
  public void testReduceCommonDataOnly() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"blub\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    TestConcreteDoc val = reducer.reduce(t, TestConcreteDoc.class);
    TestConcreteDoc testVal = new TestConcreteDoc();
    testVal.name = "a";
    testVal.setId(TEST_ID);
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", null));
    testVal.setCurrentVariation("projecta");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test
  public void testReduceCommonDataMultipleRoles() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"projectb\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projectb\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]},{\"v\":\"b\", \"a\":[\"projectb\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},"
        + "\"projectb-projectbgeneraltestdoc\":{\"projectBGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    TestConcreteDoc val = reducer.reduce(t, TestConcreteDoc.class);
    TestConcreteDoc testVal = new TestConcreteDoc();
    testVal.name = "b";
    testVal.setId(TEST_ID);
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().add(new Reference(ProjectBGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.setCurrentVariation("projectb");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));

  }

  @Test
  public void testReduceRolDataAndCommonData() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"blub\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    GeneralTestDoc val = reducer.reduce(t, GeneralTestDoc.class);
    GeneralTestDoc testVal = new GeneralTestDoc();
    testVal.name = "a";
    testVal.setId(TEST_ID);
    testVal.generalTestDocValue = "a";
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", null));
    testVal.setCurrentVariation("projecta");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test
  public void testReduceRoleDataAndCommonDataMultipleRoles() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"projectb\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projectb\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]},{\"v\":\"b\", \"a\":[\"projectb\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},"
        + "\"projectb-projectbgeneraltestdoc\":{\"projectBGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    GeneralTestDoc val = reducer.reduce(t, GeneralTestDoc.class);
    GeneralTestDoc testVal = new GeneralTestDoc();
    testVal.name = "a";
    testVal.setId(TEST_ID);
    testVal.generalTestDocValue = "a";
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().add(new Reference(ProjectBGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.setCurrentVariation("projecta");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test
  public void testReduceMissingRole() throws IOException {
    String x = "{\"testinheritsfromtestbasedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"blub\"]}],\"!defaultVRE\":\"blub\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    TestDoc val = reducer.reduce(t, TestDoc.class);
    TestDoc testVal = new TestDoc();
    testVal.setId(TEST_ID);
    testVal.getVariations().add(new Reference(TestInheritsFromTestBaseDoc.class, TEST_ID, null));
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  //Tests with explicitly requested variation.
  @Test
  public void testReduceCommonDataOnlyWithRequestedVariation() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"projectb\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\", \"projectb\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},"
        + "\"projectb-projectbgeneraltestdoc\":{\"projectBGeneralTestDocValue\":\"blubtest\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    TestConcreteDoc val = reducer.reduce(t, TestConcreteDoc.class, "projectb");
    TestConcreteDoc testVal = new TestConcreteDoc();
    testVal.name = "b";
    testVal.setId("id0000000001");
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().add(new Reference(ProjectBGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projectb", "projecta", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projectb", "projecta", null));
    testVal.setCurrentVariation("projectb");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test
  public void testReduceCommonDataMultipleRolesWithRequestedVariation() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"projectb\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projectb\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]},{\"v\":\"b\", \"a\":[\"projectb\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},"
        + "\"projectb-projectbgeneraltestdoc\":{\"projectBGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    TestConcreteDoc val = reducer.reduce(t, TestConcreteDoc.class, "projecta");
    TestConcreteDoc testVal = new TestConcreteDoc();
    testVal.name = "a";
    testVal.setId(TEST_ID);
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().add(new Reference(ProjectBGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.setCurrentVariation("projecta");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test
  public void testReduceRolDataAndCommonDataWithRequestedVariation() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"projectb\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]},{\"v\":\"b\", \"a\":[\"projectb\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},"
        + "\"projectb-projectbgeneraltestdoc\":{\"projectBGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    GeneralTestDoc val = reducer.reduce(t, GeneralTestDoc.class, "projectb");
    GeneralTestDoc testVal = new GeneralTestDoc();
    testVal.name = "b";
    testVal.generalTestDocValue = "b";
    testVal.setId(TEST_ID);
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().add(new Reference(ProjectBGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.setCurrentVariation("projectb");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test
  public void testReduceProjectSpecificRoleWithRequestedVariation() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"blub\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    ProjectAGeneralTestDoc val = reducer.reduce(t, ProjectAGeneralTestDoc.class, "projecta");
    ProjectAGeneralTestDoc testVal = new ProjectAGeneralTestDoc();
    testVal.name = "a";
    testVal.setId(TEST_ID);
    testVal.generalTestDocValue = "a";
    testVal.projectAGeneralTestDocValue = "test";
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", null));
    testVal.setCurrentVariation(null);
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  // Reduce revision
  @Test
  public void testReduceRevision() throws JsonProcessingException, IOException {
    String jsonString = "{\"versions\":[{\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"projectATestDocValue\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"a\":[\"projecta\"], \"v\":\"testDocValue\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"testconcretedoc\":{\"name\":[{\"a\":[\"projecta\"],\"v\":\"test\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"_id\":\"TCD000000001\",\"^rev\":0,\"^lastChange\":null,\"^creation\":null,\"^pid\":null,\"^deleted\":false}],\"_id\":\"TCD000000001\",\"_id\":\"id0000000001\"}";
    DBObject node = generateDBObject(jsonString);
    ProjectAGeneralTestDoc actual = reducer.reduceRevision(ProjectAGeneralTestDoc.class, node);

    ProjectAGeneralTestDoc expected = new ProjectAGeneralTestDoc();
    expected.setId("TCD000000001");
    expected.name = "test";
    expected.generalTestDocValue = "testDocValue";
    expected.projectAGeneralTestDocValue = "projectATestDocValue";
    expected.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, expected.getId(), null));
    expected.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, expected.getId(), "projecta", null));
    expected.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, expected.getId(), "projecta", null));

    assertEquals(null, MongoDiff.diffDocuments(expected, actual));
  }

  @Test
  public void testReduceRevisionSuperClass() throws JsonProcessingException, IOException {
    String jsonString = "{\"versions\":[{\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"projectATestDocValue\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"a\":[\"projecta\"], \"v\":\"testDocValue\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"testconcretedoc\":{\"name\":[{\"a\":[\"projecta\"],\"v\":\"test\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"_id\":\"TCD000000001\",\"^rev\":0,\"^lastChange\":null,\"^creation\":null,\"^pid\":null,\"^deleted\":false}],\"_id\":\"TCD000000001\",\"_id\":\"id0000000001\"}";
    DBObject node = generateDBObject(jsonString);
    TestConcreteDoc actual = reducer.reduceRevision(TestConcreteDoc.class, node);

    TestConcreteDoc expected = new TestConcreteDoc();
    expected.setId("TCD000000001");
    expected.name = "test";
    expected.setCurrentVariation("projecta");
    expected.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, expected.getId(), null));
    expected.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, expected.getId(), "projecta", null));
    expected.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, expected.getId(), "projecta", null));

    assertEquals(null, MongoDiff.diffDocuments(expected, actual));
  }

  @Test
  public void testReduceRevisionNull() throws IOException {
    ProjectAGeneralTestDoc actual = reducer.reduceRevision(ProjectAGeneralTestDoc.class, null);

    assertNull(actual);
  }

  @Test
  public void testReduceMultipleRevisionsOneRevision() throws JsonProcessingException, IOException {
    String jsonString = "{\"versions\":[{\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"projectATestDocValue\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"a\":[\"projecta\"], \"v\":\"testDocValue\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"testconcretedoc\":{\"name\":[{\"a\":[\"projecta\"],\"v\":\"test\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"_id\":\"TCD000000001\",\"^rev\":0,\"^lastChange\":null,\"^creation\":null,\"^pid\":null,\"^deleted\":false}],\"_id\":\"TCD000000001\",\"_id\":\"id0000000001\"}";
    DBObject node = generateDBObject(jsonString);
    MongoChanges<ProjectAGeneralTestDoc> actual = reducer.reduceMultipleRevisions(ProjectAGeneralTestDoc.class, node);

    assertEquals("TCD000000001", actual.getId());
    assertEquals(1, actual.getRevisions().size());
  }

  @Test
  public void testReduceMultipleRevisionsMultipleRevisions() throws JsonProcessingException, IOException {
    String jsonString = "{\"versions\":[{\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"projectATestDocValue\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"a\":[\"projecta\"], \"v\":\"testDocValue\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"testconcretedoc\":{\"name\":[{\"a\":[\"projecta\"],\"v\":\"test\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"_id\":\"TCD000000001\",\"^rev\":0,\"^lastChange\":null,\"^creation\":null,\"^pid\":null,\"^deleted\":false},"
        + "{\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"projectATestDocValue\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"a\":[\"projecta\"], \"v\":\"testDocValue1\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"testconcretedoc\":{\"name\":[{\"a\":[\"projecta\"],\"v\":\"test\"}],\"!defaultVRE\":\"projecta\"},"
        + "\"_id\":\"TCD000000001\",\"^rev\":0,\"^lastChange\":null,\"^creation\":null,\"^pid\":null,\"^deleted\":false}],\"_id\":\"TCD000000001\",\"_id\":\"id0000000001\"}";
    DBObject node = generateDBObject(jsonString);
    MongoChanges<ProjectAGeneralTestDoc> actual = reducer.reduceMultipleRevisions(ProjectAGeneralTestDoc.class, node);

    assertEquals("TCD000000001", actual.getId());
    assertEquals(2, actual.getRevisions().size());
  }

  @Test
  public void testReduceRevisionsNull() throws IOException {
    MongoChanges<ProjectAGeneralTestDoc> actual = reducer.reduceMultipleRevisions(ProjectAGeneralTestDoc.class, null);

    assertNull(actual);
  }

  //Tests with missing variation.

  @Test
  public void testReduceCommonDataOnlyWithMissingVariation() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"blub\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    TestConcreteDoc val = reducer.reduce(t, TestConcreteDoc.class, "blah");
    TestConcreteDoc testVal = new TestConcreteDoc();
    testVal.name = "a";
    testVal.setId(TEST_ID);
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", null));
    testVal.setCurrentVariation("projecta");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test
  public void testReduceCommonDataMultipleRolesWithMissingVariation() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"projectb\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projectb\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]},{\"v\":\"b\", \"a\":[\"projectb\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},"
        + "\"projectb-projectbgeneraltestdoc\":{\"projectBGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    TestConcreteDoc val = reducer.reduce(t, TestConcreteDoc.class, "blah");
    TestConcreteDoc testVal = new TestConcreteDoc();
    testVal.name = "b";
    testVal.setId(TEST_ID);
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().add(new Reference(ProjectBGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", "projectb", null));
    testVal.setCurrentVariation("projectb");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test
  public void testReduceRolDataAndCommonDataWithMissingVariation() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"blub\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]},{\"v\":\"b\", \"a\":[\"blub\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    GeneralTestDoc val = reducer.reduce(t, GeneralTestDoc.class, "blah");
    GeneralTestDoc testVal = new GeneralTestDoc();
    testVal.name = "a";
    testVal.setId(TEST_ID);
    testVal.generalTestDocValue = "a";
    testVal.getVariations().add(new Reference(ProjectAGeneralTestDoc.class, testVal.getId(), null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(TestConcreteDoc.class, testVal.getId(), "projecta", null));
    testVal.getVariations().addAll(VariationHelper.createVariationsForType(GeneralTestDoc.class, testVal.getId(), "projecta", null));
    testVal.setCurrentVariation("projecta");
    assertEquals(null, MongoDiff.diffDocuments(val, testVal));
  }

  @Test(expected = VariationException.class)
  public void testReduceProjectSpecificRoleWithWrongVaration() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"blub\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);
    reducer.reduce(t, ProjectAGeneralTestDoc.class, "blub");
  }

  @Test(expected = VariationException.class)
  public void testReduceVariationNonObject() throws IOException {
    String x = "{\"projectb-testdoc\": \"flups\",\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);

    reducer.reduce(t, TestDoc.class); // This will throw
  }

  @Test(expected = VariationException.class)
  public void testReduceMalformedCommonItem() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\": 42},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);

    reducer.reduce(t, TestConcreteDoc.class); // This will throw
  }

  @Test(expected = VariationException.class)
  public void testReduceMalformedCommonValueArrayItem() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[42]},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);

    reducer.reduce(t, TestConcreteDoc.class); // This will throw
  }

  @Test(expected = VariationException.class)
  public void testReduceMalformedCommonValueArrayItemAgreed() throws IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":42}]},\"_id\":\"id0000000001\"}";
    JsonNode t = m.readTree(x);

    reducer.reduce(t, TestConcreteDoc.class); // This will throw
  }

  @Test(expected = VariationException.class)
  public void testReduceUnknownType() throws JsonProcessingException, IOException {
    String x = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"blub\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]},{\"v\":\"b\", \"a\":[\"blub\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"unknownType\" : {\"prop\":\"value\"},\"_id\":\"id0000000001\"}";

    JsonNode t = m.readTree(x);
    reducer.reduce(t, TestConcreteDoc.class);

  }

  @Test
  public void testGetAllForDBObjectRootClass() throws JsonProcessingException, IOException {
    String jsonString = "{\"testconcretedoc\":{\"name\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    DBObject object = generateDBObject(jsonString);

    List<TestConcreteDoc> variations = reducer.getAllForDBObject(object, TestConcreteDoc.class);
    assertEquals(3, variations.size());
  }

  @Test
  public void testGetAllForDBObjectSubClass() throws JsonProcessingException, IOException {
    String jsonString = "{\"testconcretedoc\":{\"name\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    DBObject object = generateDBObject(jsonString);

    List<GeneralTestDoc> variations = reducer.getAllForDBObject(object, GeneralTestDoc.class);
    assertEquals(3, variations.size());
  }

  @Test
  public void testGetAllForDBObjectRootClassWithMultipleProjects() throws JsonProcessingException, IOException {
    String jsonString = "{\"testconcretedoc\":{\"name\":[{\"v\":\"b\", \"a\":[\"projectb\"]}, {\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]},{\"v\":\"b\", \"a\":[\"projectb\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"projectb-projectbgeneraltestdoc\":{\"projectBGeneralTestDocValue\":\"testb\"},\"_id\":\"id0000000001\"}";
    DBObject object = generateDBObject(jsonString);

    List<TestConcreteDoc> variations = reducer.getAllForDBObject(object, TestConcreteDoc.class);
    assertEquals(4, variations.size());
  }

  @Test
  public void testGetAllForDBObjectNonExistingClass() throws JsonProcessingException, IOException {
    String jsonString = "{\"testconcretedoc\":{\"name\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"generaltestdoc\":{\"generalTestDocValue\":[{\"v\":\"a\", \"a\":[\"projecta\"]}],\"!defaultVRE\":\"projecta\"},"
        + "\"projecta-projectageneraltestdoc\":{\"projectAGeneralTestDocValue\":\"test\"},\"_id\":\"id0000000001\"}";
    DBObject object = generateDBObject(jsonString);

    List<ProjectBGeneralTestDoc> variations = reducer.getAllForDBObject(object, ProjectBGeneralTestDoc.class);
    assertEquals(3, variations.size());
  }

  private DBObject generateDBObject(String jsonString) throws JsonProcessingException, IOException {
    return new JacksonDBObject<JsonNode>(m.readTree(jsonString), JsonNode.class);
  }

}
