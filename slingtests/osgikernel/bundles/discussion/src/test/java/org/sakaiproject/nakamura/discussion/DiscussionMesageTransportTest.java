/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.discussion;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.commons.testing.jcr.MockNode;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.easymock.PowerMock;
import org.sakaiproject.nakamura.api.message.AbstractMessageRoute;
import org.sakaiproject.nakamura.api.message.MessageConstants;
import org.sakaiproject.nakamura.api.message.MessageRoute;
import org.sakaiproject.nakamura.api.message.MessageRoutes;
import org.sakaiproject.nakamura.api.message.MessagingService;
import org.sakaiproject.nakamura.testutils.easymock.AbstractEasyMockTest;
import org.sakaiproject.nakamura.util.ACLUtils;

import java.security.Principal;

import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.security.AccessControlManager;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.AccessControlPolicyIterator;
import javax.jcr.version.VersionException;

/**
 *
 */
public class DiscussionMesageTransportTest extends AbstractEasyMockTest {

  private DiscussionMessageTransport transport;
  private MessagingService messagingService;
  private SlingRepository repository;
  private MessageRoutes routes;
  private JackrabbitSession adminSession;
  private UserManager userManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    routes = new MockMessageRoutes();
    transport = new DiscussionMessageTransport();
    messagingService = createMock(MessagingService.class);

    userManager = createMock(UserManager.class);

    adminSession = createMock(JackrabbitSession.class);

    repository = createMock(SlingRepository.class);
    expect(repository.loginAdministrative(null)).andReturn(adminSession);
    adminSession.logout();

    transport.bindMessagingService(messagingService);
    transport.bindSlingRepository(repository);
  }

  @After
  public void tearDown() {
    transport.unbindMessagingService(messagingService);
    transport.unbindSlingRepository(repository);
  }

  // PowerMock should allow us to mock static methods.
  // As of 2010-02-11 It gave an IncompatibleClassChangeError

  @Test
  public void testSend() throws ValueFormatException, VersionException, LockException,
      ConstraintViolationException, RepositoryException {
    MockNode node = new MockNode("/path/to/msg");
    node.setProperty(MessageConstants.PROP_SAKAI_ID, "a1b2c3d4e5f6");
    node.setProperty(MessageConstants.PROP_SAKAI_FROM, "johndoe");
    expect(messagingService.getFullPathToMessage("s-site", "a1b2c3d4e5f6", adminSession))
        .andReturn("/sites/site/store/a1b2c3d4e5f6");

    // Authorizable
    Principal principal = createMock(Principal.class);
    expect(principal.getName()).andReturn("johndoe");
    Authorizable auth = createMock(Authorizable.class);
    expect(auth.getPrincipal()).andReturn(principal);
    expect(userManager.getAuthorizable("johndoe")).andReturn(auth);
    expect(adminSession.getUserManager()).andReturn(userManager);
    AccessControlManager accessControlManager = createNiceMock(AccessControlManager.class);
    expect(adminSession.getAccessControlManager()).andReturn(accessControlManager).anyTimes();
    AccessControlPolicyIterator accessControlPolicyIterator = createNiceMock(AccessControlPolicyIterator.class);
    expect(accessControlManager.getApplicablePolicies("/sites/site/store/a1b2c3d4e5f6")).andReturn(accessControlPolicyIterator).atLeastOnce();
    expect(accessControlManager.getPolicies("/sites/site/store/a1b2c3d4e5f6")).andReturn(new AccessControlPolicy[0]).atLeastOnce();

    // mockStatic(ACLUtils.class);
    // ACLUtils.addEntry("/sites/site/store/a1b2c3d4e5f6", auth, adminSession,
    // ACLUtils.WRITE_GRANTED, ACLUtils.READ_GRANTED, ACLUtils.REMOVE_NODE_GRANTED);
    // PowerMock.replay(ACLUtils.class);

    // deepGetOrCreate new node
    MockNode messageNode = new MockNode("/sites/site/store/a1b2c3d4e5f6");
    expect(adminSession.itemExists("/sites/site/store/a1b2c3d4e5f6")).andReturn(true);
    expect(adminSession.getItem("/sites/site/store/a1b2c3d4e5f6")).andReturn(messageNode)
        .anyTimes();
    expect(adminSession.hasPendingChanges()).andReturn(true);
    adminSession.save();

    MessageRoute route = new AbstractMessageRoute("discussion:s-site") {
    };
    routes.add(route);

    replay();
    transport.send(routes, null, node);

    PowerMock.verify(ACLUtils.class);

    assertEquals("notified", messageNode.getProperty(
        MessageConstants.PROP_SAKAI_SENDSTATE).getString());
    assertEquals("inbox", messageNode.getProperty(MessageConstants.PROP_SAKAI_MESSAGEBOX)
        .getString());
    assertEquals("s-site", messageNode.getProperty(MessageConstants.PROP_SAKAI_TO)
        .getString());
  }
}
