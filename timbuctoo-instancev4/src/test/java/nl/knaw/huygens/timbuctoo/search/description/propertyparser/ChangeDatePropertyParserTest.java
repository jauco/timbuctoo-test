package nl.knaw.huygens.timbuctoo.search.description.propertyparser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.knaw.huygens.timbuctoo.model.Change;
import nl.knaw.huygens.timbuctoo.search.description.PropertyParser;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class ChangeDatePropertyParserTest extends AbstractPropertyParserTest {

  private ChangeDatePropertyParser instance;

  @Before
  public void setUp() throws Exception {
    instance = new ChangeDatePropertyParser();
  }

  @Test
  public void parseReturnsAFormattedYearMonthAndDateIfTheInputIsValid() throws JsonProcessingException {
    long timeStampOnJan20th2016 = 1453290593000L;
    Change change = new Change(timeStampOnJan20th2016, "user", "vre");
    String changeString = new ObjectMapper().writeValueAsString(change);

    String result = instance.parse(changeString);

    assertThat(result, is(equalTo("20160120")));
  }

  @Test
  public void parseReturnsNullIfTheInputCannotBeParsed() {
    String output = instance.parse("notASerializedChange");

    assertThat(output, is(nullValue()));
  }

  @Test
  public void parseToRawReturnsTheTimestamp() throws JsonProcessingException {
    long timeStampOnJan20th2016 = 1453290593000L;
    Change change = new Change(timeStampOnJan20th2016, "user", "vre");
    String changeString = new ObjectMapper().writeValueAsString(change);

    Object result = instance.parseToRaw(changeString);

    assertThat(result, is(timeStampOnJan20th2016));
  }

  @Test
  public void parseToRawReturnsTheDefaultValueIfTheInputCannotBeParsed() {
    Object output = instance.parseToRaw("notASerializedChange");

    assertThat(output, is(instance.getDefaultValue()));
  }

  @Override
  protected PropertyParser getInstance() {
    return instance;
  }
}
