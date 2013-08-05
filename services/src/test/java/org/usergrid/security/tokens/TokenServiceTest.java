package org.usergrid.security.tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.usergrid.utils.ClassUtils.cast;
import static org.usergrid.utils.MapUtils.hashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usergrid.cassandra.CassandraRunner;
import org.usergrid.management.ApplicationInfo;
import org.usergrid.management.ManagementService;
import org.usergrid.management.OrganizationInfo;
import org.usergrid.management.OrganizationOwnerInfo;
import org.usergrid.management.UserInfo;
import org.usergrid.persistence.EntityManager;
import org.usergrid.persistence.EntityManagerFactory;
import org.usergrid.persistence.entities.Application;
import org.usergrid.security.AuthPrincipalInfo;
import org.usergrid.security.AuthPrincipalType;
import org.usergrid.security.tokens.cassandra.TokenServiceImpl;
import org.usergrid.security.tokens.exceptions.ExpiredTokenException;
import org.usergrid.security.tokens.exceptions.InvalidTokenException;
import org.usergrid.test.ShiroHelperRunner;
import org.usergrid.utils.UUIDUtils;

@RunWith(ShiroHelperRunner.class)
public class TokenServiceTest {

  static Logger log = LoggerFactory.getLogger(TokenServiceTest.class);
  static ManagementService managementService;
  static TokenService tokenService;
  static EntityManagerFactory entityManagerFactory;
  // app-level data generated only once
  private static UserInfo adminUser;
  private static OrganizationInfo organization;
  private static UUID applicationId;

  @BeforeClass
  public static void setup() throws Exception {
    log.info("in setup");
    managementService = CassandraRunner.getBean(ManagementService.class);
    tokenService = CassandraRunner.getBean(TokenService.class);
    entityManagerFactory = CassandraRunner.getBean(EntityManagerFactory.class);
    setupLocal();
  }

  public static void setupLocal() throws Exception {
    adminUser = managementService.createAdminUser("edanuff", "Ed Anuff", "ed@anuff.com", "test", false, false);
    organization = managementService.createOrganization("ed-organization", adminUser, true);
    // TODO update to organizationName/applicationName
    applicationId = managementService.createApplication(organization.getUuid(), "ed-organization/ed-application")
        .getId();
  }

  @SuppressWarnings({ "unchecked", "serial" })
  @Test
  public void testEmailConfirmToken() throws Exception {
    Map<String, Object> data = new HashMap<String, Object>(){
      {
        put("email", "ed@anuff.com");
        put("username", "edanuff");
      }
    };
    
    
    String tokenStr = tokenService.createToken(TokenCategory.EMAIL, "email_confirm", null, data, 0);

    log.info("token: " + tokenStr);

    TokenInfo tokenInfo = tokenService.getTokenInfo(tokenStr);

    long last_access = tokenInfo.getAccessed();

    assertEquals("email_confirm", tokenInfo.getType());
    assertEquals("ed@anuff.com", tokenInfo.getState().get("email"));
    assertEquals("edanuff", tokenInfo.getState().get("username"));

    tokenInfo = tokenService.getTokenInfo(tokenStr);

    assertTrue(last_access < tokenInfo.getAccessed());
  }

  @Test
  public void testAdminPrincipalToken() throws Exception {

    AuthPrincipalInfo adminPrincipal = new AuthPrincipalInfo(AuthPrincipalType.ADMIN_USER, adminUser.getUuid(),
        UUIDUtils
            .newTimeUUID());

    String tokenStr = tokenService
        .createToken(TokenCategory.ACCESS, null, adminPrincipal, null, 0);

    log.info("token: " + tokenStr);

    TokenInfo tokenInfo = tokenService.getTokenInfo(tokenStr);

    long last_access = tokenInfo.getAccessed();

    assertEquals("access", tokenInfo.getType());
    assertEquals(adminUser.getUuid(), tokenInfo.getPrincipal().getUuid());

    tokenInfo = tokenService.getTokenInfo(tokenStr);

    assertTrue(last_access < tokenInfo.getAccessed());
  }

  @Test
  public void adminPrincipalTokenRevoke() throws Exception {

    AuthPrincipalInfo adminPrincipal = new AuthPrincipalInfo(AuthPrincipalType.ADMIN_USER, UUIDUtils.newTimeUUID(),
        UUIDUtils.newTimeUUID());

    String firstToken = tokenService.createToken(TokenCategory.ACCESS, null, adminPrincipal, null, 0);
    String secondToken = tokenService.createToken(TokenCategory.ACCESS, null, adminPrincipal, null, 0);

    assertNotNull(firstToken);
    assertNotNull(secondToken);

    TokenInfo firstInfo = tokenService.getTokenInfo(firstToken);
    assertNotNull(firstInfo);

    TokenInfo secondInfo = tokenService.getTokenInfo(secondToken);
    assertNotNull(secondInfo);

    tokenService.removeTokens(adminPrincipal);

    // tokens shouldn't be there anymore
    boolean invalidTokenException = false;

    try {
      tokenService.getTokenInfo(firstToken);
    } catch (InvalidTokenException ite) {
      invalidTokenException = true;
    }

    assertTrue(invalidTokenException);

    invalidTokenException = false;

    try {
      tokenService.getTokenInfo(secondToken);
    } catch (InvalidTokenException ite) {
      invalidTokenException = true;
    }

    assertTrue(invalidTokenException);
  }

  @Test
  public void userPrincipalTokenRevoke() throws Exception {
    AuthPrincipalInfo adminPrincipal = new AuthPrincipalInfo(AuthPrincipalType.APPLICATION_USER,
        UUIDUtils.newTimeUUID(), UUIDUtils.newTimeUUID());

    String firstToken = tokenService.createToken(TokenCategory.ACCESS, null, adminPrincipal, null, 0);
    String secondToken = tokenService.createToken(TokenCategory.ACCESS, null, adminPrincipal, null, 0);

    assertNotNull(firstToken);
    assertNotNull(secondToken);

    TokenInfo firstInfo = tokenService.getTokenInfo(firstToken);
    assertNotNull(firstInfo);

    TokenInfo secondInfo = tokenService.getTokenInfo(secondToken);
    assertNotNull(secondInfo);

    tokenService.removeTokens(adminPrincipal);

    // tokens shouldn't be there anymore
    boolean invalidTokenException = false;

    try {
      tokenService.getTokenInfo(firstToken);
    } catch (InvalidTokenException ite) {
      invalidTokenException = true;
    }

    assertTrue(invalidTokenException);

    invalidTokenException = false;

    try {
      tokenService.getTokenInfo(secondToken);
    } catch (InvalidTokenException ite) {
      invalidTokenException = true;
    }

    assertTrue(invalidTokenException);
  }

  @Test
  public void tokenDurationExpiration() throws Exception {
    AuthPrincipalInfo adminPrincipal = new AuthPrincipalInfo(AuthPrincipalType.APPLICATION_USER,
        UUIDUtils.newTimeUUID(), UUIDUtils.newTimeUUID());

    // 2 second token
    long expirationTime = 2000;

    String token = tokenService.createToken(TokenCategory.ACCESS, null, adminPrincipal, null, expirationTime);

    long start = System.currentTimeMillis();

    assertNotNull(token);

    TokenInfo tokenInfo = tokenService.getTokenInfo(token);
    assertNotNull(tokenInfo);
    assertEquals(expirationTime, tokenInfo.getDuration());
    long maxTokenAge = tokenService.getMaxTokenAge(token);
    assertEquals(expirationTime, maxTokenAge);


    tokenInfo = tokenService.getTokenInfo(token);
    assertNotNull(tokenInfo);
    assertEquals(expirationTime, tokenInfo.getDuration());

    maxTokenAge = tokenService.getMaxTokenAge(token);
    assertEquals(expirationTime, maxTokenAge);

    /**
     * Sleep at least expirationTime millis to allow token to expire
     */
    Thread.sleep(expirationTime - (System.currentTimeMillis() - start) + 1000);

    boolean invalidTokenException = false;

    try {
      tokenService.getTokenInfo(token);
    } catch (ExpiredTokenException ite) {
      invalidTokenException = true;
    }

    assertTrue(invalidTokenException);
  }

  @Test
  public void tokenDefaults() throws Exception {
    AuthPrincipalInfo adminPrincipal = new AuthPrincipalInfo(AuthPrincipalType.APPLICATION_USER,
        UUIDUtils.newTimeUUID(), UUIDUtils.newTimeUUID());

    long maxAge = ((TokenServiceImpl) tokenService).getMaxPersistenceTokenAge();

    String token = tokenService.createToken(TokenCategory.ACCESS, null, adminPrincipal, null, 0);

    assertNotNull(token);

    TokenInfo tokenInfo = tokenService.getTokenInfo(token);
    assertNotNull(tokenInfo);
    assertEquals(maxAge, tokenInfo.getDuration());
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidDurationValue() throws Exception {

    long maxAge = ((TokenServiceImpl) tokenService).getMaxPersistenceTokenAge();

    AuthPrincipalInfo adminPrincipal = new AuthPrincipalInfo(AuthPrincipalType.APPLICATION_USER,
        UUIDUtils.newTimeUUID(), UUIDUtils.newTimeUUID());

    tokenService.createToken(TokenCategory.ACCESS, null, adminPrincipal, null, maxAge + 1);

  }

  @Test
  public void appDefaultExpiration() throws Exception {

    OrganizationOwnerInfo orgInfo = managementService.createOwnerAndOrganization("foo", "foobar", "foobar",
        "foo@bar.com", "foobar");
    ApplicationInfo appInfo = managementService.createApplication(orgInfo.getOrganization().getUuid(), "bar");
    EntityManager em = entityManagerFactory.getEntityManager(appInfo.getId());
    Application app = em.getApplication();
    AuthPrincipalInfo userPrincipal = new AuthPrincipalInfo(AuthPrincipalType.APPLICATION_USER,
        UUIDUtils.newTimeUUID(), app.getUuid());
    String token = tokenService.createToken(TokenCategory.ACCESS, null, userPrincipal, null, 0);
    assertNotNull(token);
    TokenInfo tokenInfo = tokenService.getTokenInfo(token);
    assertNotNull(tokenInfo);
    assertEquals(TokenServiceImpl.LONG_TOKEN_AGE, tokenInfo.getDuration());

  }

  @Test
  public void appExpiration() throws Exception {

    OrganizationOwnerInfo orgInfo = managementService.createOwnerAndOrganization("foo2", "foobar2", "foobar",
        "foo2@bar.com", "foobar");

    ApplicationInfo appInfo = managementService.createApplication(orgInfo.getOrganization().getUuid(), "bar");

    EntityManager em = entityManagerFactory.getEntityManager(appInfo.getId());

    Application app = em.getApplication();

    long appTokenAge = 1000;

    app.setAccesstokenttl(appTokenAge);

    em.updateApplication(app);

    AuthPrincipalInfo userPrincipal = new AuthPrincipalInfo(AuthPrincipalType.APPLICATION_USER,
        UUIDUtils.newTimeUUID(), app.getUuid());

    String token = tokenService.createToken(TokenCategory.ACCESS, null, userPrincipal, null, 0);

    long start = System.currentTimeMillis();

    assertNotNull(token);

    TokenInfo tokenInfo = tokenService.getTokenInfo(token);
    assertNotNull(tokenInfo);
    assertEquals(appTokenAge, tokenInfo.getDuration());

    /**
     * Sleep at least expirationTime millis to allow token to expire
     */
    Thread.sleep(appTokenAge - (System.currentTimeMillis() - start) + 1000);

    boolean invalidTokenException = false;

    try {
      tokenService.getTokenInfo(token);
    } catch (ExpiredTokenException ite) {
      invalidTokenException = true;
    }

    assertTrue(invalidTokenException);
  }

  @Test
  public void tokenDeletion() throws Exception {
    AuthPrincipalInfo adminPrincipal = new AuthPrincipalInfo(AuthPrincipalType.APPLICATION_USER,
        UUIDUtils.newTimeUUID(), UUIDUtils.newTimeUUID());

    String realToken = tokenService.createToken(TokenCategory.ACCESS, null, adminPrincipal, null, 0);

    assertNotNull(realToken);

    TokenInfo tokenInfo = tokenService.getTokenInfo(realToken);
    assertNotNull(tokenInfo);

    tokenService.revokeToken(realToken);

    boolean invalidTokenException = false;

    try {
      tokenService.getTokenInfo(realToken);
    } catch (InvalidTokenException ite) {
      invalidTokenException = true;
    }

    assertTrue(invalidTokenException);

    String fakeToken = "notarealtoken";

    tokenService.revokeToken(fakeToken);

  }

  @Test
  public void appExpirationInfinite() throws Exception {

    OrganizationOwnerInfo orgInfo = managementService.createOwnerAndOrganization("appExpirationInfinite",
        "appExpirationInfinite", "foobar",
        "appExpirationInfinite@bar.com", "foobar");

    ApplicationInfo appInfo = managementService.createApplication(orgInfo.getOrganization().getUuid(), "bar");

    EntityManager em = entityManagerFactory.getEntityManager(appInfo.getId());

    Application app = em.getApplication();

    long appTokenAge = 0;

    app.setAccesstokenttl(appTokenAge);

    em.updateApplication(app);

    AuthPrincipalInfo userPrincipal = new AuthPrincipalInfo(AuthPrincipalType.APPLICATION_USER,
        UUIDUtils.newTimeUUID(), app.getUuid());

    String token = tokenService.createToken(TokenCategory.ACCESS, null, userPrincipal, null, 0);

    assertNotNull(token);

    TokenInfo tokenInfo = tokenService.getTokenInfo(token);
    assertNotNull(tokenInfo);
    assertEquals(Long.MAX_VALUE, tokenInfo.getDuration());

    boolean invalidTokenException = false;

    try {
      tokenService.getTokenInfo(token);
    } catch (InvalidTokenException ite) {
      invalidTokenException = true;
    }

    assertFalse(invalidTokenException);

    tokenService.revokeToken(token);

    invalidTokenException = false;

    try {
      tokenService.getTokenInfo(token);
    } catch (InvalidTokenException ite) {
      invalidTokenException = true;
    }

    assertTrue(invalidTokenException);

  }


  /**
   * Tests that tokens are not expired longer than the default values in the system.
   * 
   * USERGRID-1845
   * @throws Exception
   */
  @Test
  public void appExpirationInfiniteTokenValidAfter2Weeks() throws Exception {

    long twoWeeks = TimeUnit.DAYS.toMillis(14);
    
    long createdTime = System.currentTimeMillis() - twoWeeks;
    
    TokenServiceImpl tokenServiceImpl = CassandraRunner.getBean(TokenServiceImpl.class);
    
    OrganizationOwnerInfo orgInfo = managementService.createOwnerAndOrganization("appExpirationInfiniteNotDenied",
        "appExpirationInfiniteNotDenied", "foobar",
        "appExpirationInfiniteNotDenied@bar.com", "foobar");

    ApplicationInfo appInfo = managementService.createApplication(orgInfo.getOrganization().getUuid(), "bar");

    EntityManager em = entityManagerFactory.getEntityManager(appInfo.getId());

    Application app = em.getApplication();

    long appTokenAge = 0;

    app.setAccesstokenttl(appTokenAge);

    em.updateApplication(app);

    AuthPrincipalInfo userPrincipal = new AuthPrincipalInfo(AuthPrincipalType.APPLICATION_USER,
        UUIDUtils.newTimeUUID(), app.getUuid());

    String token = tokenServiceImpl.createToken(TokenCategory.ACCESS, null, userPrincipal, null, 0, createdTime);

    assertNotNull(token);

    TokenInfo tokenInfo = tokenServiceImpl.getTokenInfo(token);
    assertNotNull(tokenInfo);
    assertEquals(Long.MAX_VALUE, tokenInfo.getDuration());

    boolean invalidTokenException = false;

    try {
      tokenService.getTokenInfo(token);
    } catch (InvalidTokenException ite) {
      invalidTokenException = true;
    }

    assertFalse(invalidTokenException);

    tokenService.revokeToken(token);

    invalidTokenException = false;

    try {
      tokenService.getTokenInfo(token);
    } catch (InvalidTokenException ite) {
      invalidTokenException = true;
    }

    assertTrue(invalidTokenException);

  }
  
}
