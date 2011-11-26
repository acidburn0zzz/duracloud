/*
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 *     http://duracloud.org/license/
 */
package org.duracloud.security.vote;

import org.duracloud.client.ContentStore;
import org.duracloud.error.ContentStoreException;

import static org.easymock.EasyMock.createMock;

import org.duracloud.security.domain.HttpVerb;
import org.duracloud.security.impl.DuracloudUserDetails;
import org.easymock.IAnswer;
import org.easymock.classextension.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.Authentication;
import org.springframework.security.ConfigAttributeDefinition;
import org.springframework.security.GrantedAuthority;
import org.springframework.security.GrantedAuthorityImpl;
import org.springframework.security.SecurityConfig;
import org.springframework.security.intercept.web.FilterInvocation;
import org.springframework.security.providers.UsernamePasswordAuthenticationToken;
import org.springframework.security.providers.anonymous.AnonymousAuthenticationToken;
import org.springframework.security.userdetails.User;
import org.springframework.security.userdetails.UserDetails;
import org.springframework.security.userdetails.UserDetailsService;

import static org.springframework.security.vote.AccessDecisionVoter.ACCESS_ABSTAIN;
import static org.springframework.security.vote.AccessDecisionVoter.ACCESS_DENIED;
import static org.springframework.security.vote.AccessDecisionVoter.ACCESS_GRANTED;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Andrew Woods
 *         Date: Nov 18, 2011
 */
public class SpaceReadAccessVoterTest {

    private SpaceReadAccessVoter voter;

    private final String OPEN_SPACE_ID = "open-space";
    private Map<String, String> acls;
    private final String userRead = "username-r";
    private final String groupWrite = "group-curators-w";

    private ContentStoreUtil contentStoreUtil;
    private UserDetailsService userDetailsService;
    private FilterInvocation resource;
    private HttpServletRequest request;


    @Before
    public void setUp() {
        acls = new HashMap<String, String>();
        acls.put(userRead, "r");
        acls.put(groupWrite, "w");

        contentStoreUtil = createContentStoreUtilMock();
        userDetailsService = createUserDetailsServiceMock();
        resource = EasyMock.createMock("FilterInvocation",
                                       FilterInvocation.class);
        request = EasyMock.createMock("HttpServletRequest",
                                      HttpServletRequest.class);

        voter = new SpaceReadAccessVoter(contentStoreUtil, userDetailsService);
    }

    @After
    public void tearDown() {
        EasyMock.verify(contentStoreUtil,
                        userDetailsService,
                        resource,
                        request);
    }

    private void replayMocks() {
        EasyMock.replay(contentStoreUtil,
                        userDetailsService,
                        resource,
                        request);
    }

    @Test
    public void testUserAccessMethodsGET() {
        boolean securedSpace = true;
        Authentication caller = registeredUser(userRead, "none");
        createMockInvocation(caller, securedSpace, HttpVerb.GET);
        ConfigAttributeDefinition config = getConfigAttribute(securedSpace);

        replayMocks();

        int decision = voter.vote(caller, resource, config);
        Assert.assertEquals(ACCESS_GRANTED, decision);
    }

    @Test
    public void testUserNoAccessMethodsGET() {
        boolean securedSpace = true;
        Authentication caller = registeredUser("joe", "none");
        createMockInvocation(caller, securedSpace, HttpVerb.GET);
        ConfigAttributeDefinition config = getConfigAttribute(securedSpace);

        replayMocks();

        int decision = voter.vote(caller, resource, config);
        Assert.assertEquals(ACCESS_DENIED, decision);
    }

    @Test
    public void testGroupAccessMethodsGET() {
        boolean securedSpace = true;
        Authentication caller = registeredUser("joe", groupWrite);
        createMockInvocation(caller, securedSpace, HttpVerb.GET);
        ConfigAttributeDefinition config = getConfigAttribute(securedSpace);

        replayMocks();

        int decision = voter.vote(caller, resource, config);
        Assert.assertEquals(ACCESS_GRANTED, decision);
    }

    @Test
    public void testGroupNoAccessMethodsGET() {
        boolean securedSpace = true;
        Authentication caller = registeredUser("joe", "none");
        createMockInvocation(caller, securedSpace, HttpVerb.GET);
        ConfigAttributeDefinition config = getConfigAttribute(securedSpace);

        replayMocks();

        int decision = voter.vote(caller, resource, config);
        Assert.assertEquals(ACCESS_DENIED, decision);
    }

    @Test
    public void testMethodsPUT() {
        boolean securedSpace = true;
        Authentication caller = registeredUser("joe", "none");
        createMockInvocation(caller, securedSpace, HttpVerb.PUT);
        ConfigAttributeDefinition config = getConfigAttribute(securedSpace);

        replayMocks();

        int decision = voter.vote(caller, resource, config);
        Assert.assertEquals(ACCESS_ABSTAIN, decision);
    }

    @Test
    public void testVoteOpenAuthenticated() {
        boolean securedSpace = false;
        verifyVote(registeredUser("joe", "none"), securedSpace, ACCESS_GRANTED);
    }

    @Test
    public void testVoteOpenAnonymous() {
        boolean securedSpace = false;
        verifyVote(anonymousUser(), securedSpace, ACCESS_GRANTED);
    }

    @Test
    public void testVoteClosedAuthenticatedNoAccess() {
        boolean securedSpace = true;
        verifyVote(registeredUser("joe", "none"), securedSpace, ACCESS_DENIED);
    }

    @Test
    public void testVoteClosedAuthenticatedAccess() {
        boolean securedSpace = true;
        verifyVote(registeredUser(userRead, "x"), securedSpace, ACCESS_GRANTED);
    }

    @Test
    public void testVoteClosedAnonymous() {
        boolean securedSpace = true;
        verifyVote(anonymousUser(), securedSpace, ACCESS_DENIED);
    }

    private void verifyVote(Authentication caller,
                            boolean securedSpace,
                            int expected) {
        resource = createMockInvocation(caller, securedSpace, HttpVerb.GET);
        ConfigAttributeDefinition config = getConfigAttribute(securedSpace);
        replayMocks();

        int decision = voter.vote(caller, resource, config);
        Assert.assertEquals(expected, decision);
    }

    private Authentication registeredUser(String username, String group) {
        List<String> groups = new ArrayList<String>();
        groups.add(group);

        GrantedAuthority[] authorities =
            new GrantedAuthorityImpl[]{new GrantedAuthorityImpl("ROLE_USER")};
        DuracloudUserDetails user = new DuracloudUserDetails(username,
                                                             "x",
                                                             true,
                                                             true,
                                                             true,
                                                             true,
                                                             authorities,
                                                             groups);
        return new UsernamePasswordAuthenticationToken(user, "", authorities);
    }

    private Authentication anonymousUser() {
        GrantedAuthority[] authorities =
            new GrantedAuthorityImpl[]{new GrantedAuthorityImpl("ROLE_ANONYMOUS")};
        User user = new User("anon", "x", true, true, true, true, authorities);
        return new AnonymousAuthenticationToken("x", user, authorities);
    }

    private FilterInvocation createMockInvocation(Authentication caller,
                                                  boolean securedSpace,
                                                  HttpVerb method) {
        String spaceId = OPEN_SPACE_ID;
        if (securedSpace) {
            spaceId = "some-closed-space";
        }

        EasyMock.expect(request.getMethod()).andReturn(method.name());

        int times = 1;
        if (securedSpace && !(caller instanceof AnonymousAuthenticationToken)) {
            times = 2;
        }

        if (method.isRead()) {
            EasyMock.expect(request.getLocalPort())
                    .andReturn(8080)
                    .times(times);
            EasyMock.expect(request.getQueryString()).andReturn(
                "storeID=5&attachment=true").times(times);
            EasyMock.expect(request.getPathInfo()).andReturn(spaceId).times(
                times);
        }

        EasyMock.expect(resource.getHttpRequest()).andReturn(request);
        return resource;
    }

    public ConfigAttributeDefinition getConfigAttribute(boolean securedSpace) {
        if (securedSpace) {
            return new ConfigAttributeDefinition(new SecurityConfig("ROLE_USER"));
        }
        return new ConfigAttributeDefinition(new SecurityConfig("ROLE_ANONYMOUS"));
    }

    private UserDetailsService createUserDetailsServiceMock() {
        userDetailsService = EasyMock.createMock("UserDetailsService",
                                                 UserDetailsService.class);
        UserDetails userDetails = new User("username",
                                           "password",
                                           true,
                                           true,
                                           true,
                                           true,
                                           new GrantedAuthority[]{});

        EasyMock.expect(userDetailsService.loadUserByUsername(EasyMock.<String>anyObject()))
                .andReturn(userDetails).anyTimes();

        return userDetailsService;
    }

    private ContentStoreUtil createContentStoreUtilMock() {
        contentStoreUtil = EasyMock.createMock("ContentStoreUtil",
                                               ContentStoreUtil.class);
        ContentStore store = createMock("ContentStore", ContentStore.class);
        try {
            EasyMock.expect(store.getSpaceAccess(EasyMock.isA(String.class)))
                    .andAnswer(getAccess());
            EasyMock.expect(store.getSpaceACLs(EasyMock.isA(String.class)))
                    .andReturn(acls);
        } catch (ContentStoreException e) {
            // do nothing
        }

        EasyMock.expect(contentStoreUtil.getContentStore(EasyMock.<String>anyObject(),
                                                         EasyMock.<String>anyObject(),
                                                         EasyMock.<String>anyObject()))
                .andReturn(store)
                .anyTimes();
        EasyMock.replay(store);
        return contentStoreUtil;
    }

    /**
     * This method returns 'open' or 'closed' base on the argument passed
     * to the getContentStore() call above.
     *
     * @return ContentStore.AccessType
     */
    private IAnswer<? extends ContentStore.AccessType> getAccess() {
        return new IAnswer<ContentStore.AccessType>() {
            public ContentStore.AccessType answer() throws Throwable {
                Object[] args = EasyMock.getCurrentArguments();
                Assert.assertNotNull(args);
                Assert.assertEquals(1, args.length);
                String arg = (String) args[0];
                if (arg.equals(OPEN_SPACE_ID)) {
                    return ContentStore.AccessType.OPEN;
                }
                return ContentStore.AccessType.CLOSED;
            }
        };
    }

}