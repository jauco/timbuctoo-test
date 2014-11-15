package nl.knaw.huygens.timbuctoo.security;

import static nl.knaw.huygens.timbuctoo.security.SecurityInformationMatcher.securityInformationWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import nl.knaw.huygens.security.client.model.SecurityInformation;
import nl.knaw.huygens.timbuctoo.model.Login;

import org.junit.Test;

public class LoginConverterTest {
  @Test
  public void toSecurityInformationCreatesAPrincipalAndAddsItToANewSecurityInformation() {
    // setup
    String userPid = "userPid";
    String userName = "userName";
    String password = "password";
    String givenName = "givenName";
    String surname = "surname";
    String emailAddress = "test@test.com";
    String organization = "organization";
    String commonName = "givenName surname";
    byte[] salt = "salt".getBytes();

    Login login = new Login(userPid, userName, password, givenName, surname, emailAddress, organization, salt);

    LoginConverter instance = new LoginConverter();

    // action
    SecurityInformation information = instance.toSecurityInformation(login);

    // verify
    assertThat(information, is(securityInformationWith(userPid, givenName, surname, emailAddress, organization, commonName)));

  }
}