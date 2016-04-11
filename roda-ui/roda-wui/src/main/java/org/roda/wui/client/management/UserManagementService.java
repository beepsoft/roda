/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
/**
 *
 */
package org.roda.wui.client.management;

import java.util.List;

import org.roda.core.common.LdapUtilityException;
import org.roda.core.data.adapter.facet.Facets;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.sort.Sorter;
import org.roda.core.data.adapter.sublist.Sublist;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.EmailAlreadyExistsException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.IllegalOperationException;
import org.roda.core.data.exceptions.InvalidTokenException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.exceptions.UserAlreadyExistsException;
import org.roda.core.data.v2.agents.Agent;
import org.roda.core.data.v2.formats.Format;
import org.roda.core.data.v2.index.IndexResult;
import org.roda.core.data.v2.log.LogEntry;
import org.roda.core.data.v2.messages.Message;
import org.roda.core.data.v2.risks.Risk;
import org.roda.core.data.v2.user.Group;
import org.roda.core.data.v2.user.RODAMember;
import org.roda.core.data.v2.user.User;
import org.roda.wui.client.management.recaptcha.RecaptchaException;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.ServiceDefTarget;

/**
 * @author Luis Faria
 *
 */
public interface UserManagementService extends RemoteService {

  /**
   * Service URI path
   */
  public static final String SERVICE_URI = "UserManagementService";

  /**
   * Factory utility
   */
  public static class Util {

    /**
     * Get a new instance of the service
     *
     * @return the instance
     */
    public static UserManagementServiceAsync getInstance() {

      UserManagementServiceAsync instance = (UserManagementServiceAsync) GWT.create(UserManagementService.class);
      ServiceDefTarget target = (ServiceDefTarget) instance;
      target.setServiceEntryPoint(GWT.getModuleBaseURL() + SERVICE_URI);
      return instance;
    }
  }

  public Long getMemberCount(Filter filter)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException;

  public IndexResult<RODAMember> findMembers(Filter filter, Sorter sorter, Sublist sublist, Facets facets,
    String localeString) throws AuthorizationDeniedException, GenericException, RequestNotValidException;

  /**
   * Get a group
   *
   * @param groupname
   *          the group name
   * @return the group
   * @throws RODAException
   */
  public Group getGroup(String groupname) throws RODAException;

  public List<Group> listAllGroups() throws AuthorizationDeniedException, GenericException;

  /**
   * Get a user
   *
   * @param username
   *          the user name
   * @return the user
   *
   * @throws RODAException
   */
  public User getUser(String username) throws RODAException;

  /**
   * Register a new user
   *
   * @param user
   *          The user to register
   * @param password
   *          user password
   * @param captcha
   *          the captcha challenge
   * @return true if passed the challenge, false otherwise
   * @throws GenericException
   * @throws UserAlreadyExistsException
   * @throws EmailAlreadyExistsException
   * @throws RecaptchaException
   */
  public void registerUser(User user, String password, String captcha)
    throws GenericException, UserAlreadyExistsException, EmailAlreadyExistsException, RecaptchaException;

  /**
   * Create a new user
   *
   * @param user
   *          the user
   * @param password
   *          the user password
   * @return
   * @throws AuthorizationDeniedException
   * @throws NotFoundException
   * @throws EmailAlreadyExistsException
   * @throws UserAlreadyExistsException
   * @throws GenericException
   */
  public User addUser(User user, String password) throws AuthorizationDeniedException, NotFoundException,
    EmailAlreadyExistsException, UserAlreadyExistsException, GenericException, IllegalOperationException;

  /**
   * Modify a user
   *
   * @param user
   *          the modified users
   * @param password
   *          the new user password, or null to stay the same
   * @throws AuthorizationDeniedException
   * @throws NotFoundException
   * @throws AlreadyExistsException
   * @throws GenericException
   */
  public void modifyUser(User user, String password)
    throws AuthorizationDeniedException, NotFoundException, AlreadyExistsException, GenericException;

  /**
   * Modify the authenticated user
   *
   * @param user
   *          the modified user
   * @param password
   *          the user password if modified, or null if it remains the same
   * @throws AuthorizationDeniedException
   * @throws NotFoundException
   * @throws AlreadyExistsException
   * @throws GenericException
   * @throws IllegalOperationException
   */
  public void editMyUser(User user, String password) throws AuthorizationDeniedException, NotFoundException,
    AlreadyExistsException, GenericException, IllegalOperationException;

  /**
   * Try to remove a user, if user cannot be removed it will be deactivated
   *
   * @param username
   *          the user name
   * @return true if user was removed, false if it was only deactivated
   * @throws AuthorizationDeniedException
   * @throws GenericException
   */
  public void removeUser(String username) throws AuthorizationDeniedException, GenericException;

  /**
   * Create a group
   *
   * @param group
   *          the new group
   * @throws AuthorizationDeniedException
   * @throws GenericException
   * @throws AlreadyExistsException
   */
  public void addGroup(Group group) throws AuthorizationDeniedException, GenericException, AlreadyExistsException;

  /**
   * Modify a group
   *
   * @param group
   *          the modified group
   * @throws AuthorizationDeniedException
   * @throws GenericException
   * @throws NotFoundException
   */
  public void modifyGroup(Group group) throws AuthorizationDeniedException, GenericException, NotFoundException;

  /**
   * Remove a group
   *
   * @param groupname
   *          the group name
   * @throws AuthorizationDeniedException
   * @throws GenericException
   */
  public void removeGroup(String groupname) throws AuthorizationDeniedException, GenericException;

  /**
   * Get the number log entries
   *
   * @param filter
   * @return
   * @throws RODAException
   */
  public Long getLogEntriesCount(Filter filter) throws RODAException;

  public IndexResult<LogEntry> findLogEntries(Filter filter, Sorter sorter, Sublist sublist, Facets facets)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException;

  public LogEntry retrieveLogEntry(String logEntryId)
    throws AuthorizationDeniedException, GenericException, NotFoundException;

  public IndexResult<Message> findMessages(Filter filter, Sorter sorter, Sublist sublist, Facets facets)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException;

  /**
   * Get risk
   *
   * @param riskId
   * @return
   * @throws RODAException
   */
  public Risk retrieveRisk(String riskId) throws AuthorizationDeniedException, GenericException, NotFoundException;

  /**
   * Add risk
   *
   * @param risk
   * @return
   * @throws RequestNotValidException
   * @throws AuthorizationDeniedException
   * @throws GenericException
   * @throws RODAException
   */
  Risk addRisk(Risk risk) throws GenericException, AuthorizationDeniedException, RequestNotValidException;

  /**
   * Edit risk
   *
   * @param risk
   * @return
   * @throws RequestNotValidException
   * @throws GenericException
   * @throws AuthorizationDeniedException
   * 
   */
  public void modifyRisk(Risk risk) throws AuthorizationDeniedException, GenericException, RequestNotValidException;

  /**
   * Send the email challenge to a user email
   *
   * @param username
   *          the name of the user
   * @throws GenericException
   * @throws NotFoundException
   */
  public void sendEmailVerification(String username) throws GenericException, NotFoundException;

  /**
   * Confirm a user email. If verified user will become active
   *
   * @param username
   *          the name of the user
   * @param token
   *          the token used in email verification
   * @throws InvalidTokenException
   * @throws NotFoundException
   * @throws GenericException
   */
  public void confirmUserEmail(String username, String emailConfirmationToken)
    throws InvalidTokenException, NotFoundException, GenericException;

  /**
   * Change the email of a user that is still not active due to a email
   * unverified
   *
   * @param username
   *          the name of the user
   * @param email
   *          the new email
   * @return true if email was successfully changed, false otherwise
   * @throws RODAException
   *
   */
  public boolean changeUnverifiedEmail(String username, String email) throws RODAException;

  /**
   * Request to reset the password. An email will be sent to the user with the
   * password reset token.
   *
   * @param usernameOrEmail
   *          the user name or email
   * @param captcha
   *          the captcha challenge answer
   * @throws GenericException
   * @throws NotFoundException
   * @throws IllegalOperationException
   * @throws LdapUtilityException
   * @throws RecaptchaException
   */
  public void requestPasswordReset(String usernameOrEmail, String captcha)
    throws GenericException, NotFoundException, IllegalOperationException, RecaptchaException;

  /**
   * Reset a user password
   *
   * @param username
   *          the user name
   * @param password
   *          the new password
   * @param resetPasswordToken
   *          the password token that was sent by email on
   *          requestPasswordReset(String, String)
   * @throws InvalidTokenException
   * @throws IllegalOperationException
   * @throws NotFoundException
   * @throws GenericException
   */
  public void resetUserPassword(String username, String password, String resetPasswordToken)
    throws InvalidTokenException, IllegalOperationException, NotFoundException, GenericException;

  /**
   * Get agent
   *
   * @param agentId
   * @return
   * @throws GenericException
   * @throws NotFoundException
   * @throws AuthorizationDeniedException
   */
  public Agent retrieveAgent(String agentId) throws AuthorizationDeniedException, NotFoundException, GenericException;

  /**
   * Add agent
   *
   * @param agent
   * @return
   * @throws RequestNotValidException
   * @throws GenericException
   * @throws AuthorizationDeniedException
   */
  public Agent addAgent(Agent agent) throws AuthorizationDeniedException, GenericException, RequestNotValidException;

  /**
   * Edit agent
   *
   * @param agent
   * @throws RequestNotValidException
   * @throws GenericException
   * @throws AuthorizationDeniedException
   */
  public void modifyAgent(Agent agent) throws GenericException, RequestNotValidException, AuthorizationDeniedException;

  /**
   * Get format
   *
   * @param formatId
   * @return
   * @throws GenericException
   * @throws NotFoundException
   * @throws AuthorizationDeniedException
   */
  public Format retrieveFormat(String formatId)
    throws NotFoundException, GenericException, AuthorizationDeniedException;

  /**
   * Add format
   *
   * @param format
   * @return
   * @throws RequestNotValidException
   * @throws GenericException
   * @throws NotFoundException
   * @throws AuthorizationDeniedException
   */
  public Format addFormat(Format format)
    throws AuthorizationDeniedException, GenericException, RequestNotValidException;

  /**
   * Edit format
   *
   * @param format
   * @throws RequestNotValidException
   * @throws GenericException
   * @throws AuthorizationDeniedException
   */
  public void modifyFormat(Format format)
    throws GenericException, RequestNotValidException, AuthorizationDeniedException;

  Message retrieveMessage(String messageId) throws NotFoundException, GenericException, AuthorizationDeniedException;

}
