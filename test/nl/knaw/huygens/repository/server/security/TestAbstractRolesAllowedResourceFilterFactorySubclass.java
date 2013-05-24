package nl.knaw.huygens.repository.server.security;

import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;

public class TestAbstractRolesAllowedResourceFilterFactorySubclass extends AbstractRolesAllowedResourceFilterFactory {

  public TestAbstractRolesAllowedResourceFilterFactorySubclass(boolean securityEnabled) {
    super(securityEnabled);
  }

  @Override
  protected ResourceFilter createResourceFilter() {
    return new SecurityFilter();
  }

  @Override
  protected ResourceFilter createNoSecurityResourceFilter() {
    return new NoSecurityFilter();
  }

  static final class NoSecurityFilter implements ResourceFilter {

    @Override
    public ContainerRequestFilter getRequestFilter() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public ContainerResponseFilter getResponseFilter() {
      // TODO Auto-generated method stub
      return null;
    }

  }

  static final class SecurityFilter implements ResourceFilter {

    @Override
    public ContainerRequestFilter getRequestFilter() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public ContainerResponseFilter getResponseFilter() {
      // TODO Auto-generated method stub
      return null;
    }

  }

}
