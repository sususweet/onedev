package io.onedev.server.plugin.authenticator.ldap;

import io.onedev.commons.utils.ExplicitException;
import io.onedev.commons.utils.StringUtils;
import io.onedev.server.annotation.Editable;
import io.onedev.server.annotation.Password;
import io.onedev.server.annotation.ShowCondition;
import io.onedev.server.model.support.administration.authenticator.Authenticated;
import io.onedev.server.model.support.administration.authenticator.Authenticator;
import io.onedev.server.security.TrustCertsSSLSocketFactory;
import io.onedev.server.util.EditContext;
import io.onedev.server.util.Translation;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.naming.*;
import javax.naming.directory.*;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.*;

@Editable(name="Generic LDAP", order=200)
public class LdapAuthenticator extends Authenticator {

	private static final long serialVersionUID = 1L;
	
	private static final String PROP_AUTHENTICATION_REQUIRED = "authenticationRequired";
	
	private static final Logger logger  = LoggerFactory.getLogger(LdapAuthenticator.class);

	private String ldapUrl;
	
	private boolean authenticationRequired;
	
    private String managerDN;
    
    private String managerPassword;
    
    private List<String> userSearchBases;
    
    private String userSearchFilter;
    
    private String userFullNameAttribute = "displayName";
    
    private String userEmailAttribute = "mail";
    
    private String userSshKeyAttribute;
    
    private GroupRetrieval groupRetrieval = new DoNotRetrieveGroups();
    
    @Editable(order=100, name="LDAP URL", description="" +
			"Specifies LDAP URL, for example: <i>ldap://localhost</i>, or <i>ldaps://localhost</i>. In case" +
			"your ldap server is using a self-signed certificate for ldaps connection, you will need " +
			"to <a href='https://docs.onedev.io/administration-guide/trust-self-signed-certificates' target='_blank'>configure OneDev to trust the certificate</a>")
    @NotEmpty
	public String getLdapUrl() {
		return ldapUrl;
	}

	public void setLdapUrl(String ldapUrl) {
		this.ldapUrl = ldapUrl;
	}

	@Editable(order=200, description="OneDev needs to search and determine user DN, as well as searching user group "
			+ "information if group retrieval is enabled. Tick this option and specify 'manager' DN and password if "
			+ "these operations needs to be authenticated")
	public boolean isAuthenticationRequired() {
		return authenticationRequired;
	}

	public void setAuthenticationRequired(boolean authenticationRequired) {
		this.authenticationRequired = authenticationRequired;
	}

	@SuppressWarnings("unused")
	private static boolean isAuthenticationRequiredEnabled() {
		return (boolean) EditContext.get().getInputValue(PROP_AUTHENTICATION_REQUIRED);
	}
	
	@Editable(order=300, description="Specify manager DN to authenticate OneDev itself to LDAP server")
	@ShowCondition("isAuthenticationRequiredEnabled")
	@NotEmpty
	public String getManagerDN() {
		return managerDN;
	}

	public void setManagerDN(String managerDN) {
		this.managerDN = managerDN;
	}

	@Editable(order=400, description="Specifies password of above manager DN")
	@Password
	@ShowCondition("isAuthenticationRequiredEnabled")
	@NotEmpty
	public String getManagerPassword() {
		return managerPassword;
	}

	public void setManagerPassword(String managerPassword) {
		this.managerPassword = managerPassword;
	}

	@Editable(order=500, placeholder = "Input user search base. Hit ENTER to add", description=
			"Specifies base nodes for user search. For example: <i>ou=users, dc=example, dc=com</i>")
	@Size(min=1, message = "At least one user search base should be specified")
	public List<String> getUserSearchBases() {
		return userSearchBases;
	}

	public void setUserSearchBases(List<String> userSearchBases) {
		this.userSearchBases = userSearchBases;
	}

	@Editable(order=600, description=
		     "This filter is used to determine the LDAP entry for current user. " + 
		     "For example: <i>(&(uid={0})(objectclass=person))</i>. In this example, " +
		     "<i>{0}</i> represents login name of current user.")
	@NotEmpty
	public String getUserSearchFilter() {
		return userSearchFilter;
	}

	public void setUserSearchFilter(String userSearchFilter) {
		this.userSearchFilter = userSearchFilter;
	}

	@Editable(order=700, description=""
			+ "Optionally specifies name of the attribute inside the user LDAP entry whose value will be taken as user "
			+ "full name. This field is normally set to <i>displayName</i> according to RFC 2798. If left empty, full "
			+ "name of the user will not be retrieved")
	public String getUserFullNameAttribute() {
		return userFullNameAttribute;
	}

	public void setUserFullNameAttribute(String userFullNameAttribute) {
		this.userFullNameAttribute = userFullNameAttribute;
	}

	@Editable(order=800, description=""
			+ "Optionally specifies name of the attribute inside the user LDAP entry whose value will be taken as user "
			+ "email. This field is normally set to <i>mail</i> according to RFC 2798")
	public String getUserEmailAttribute() {
		return userEmailAttribute;
	}

	public void setUserEmailAttribute(String userEmailAttribute) {
		this.userEmailAttribute = userEmailAttribute;
	}

	@Editable(name="User SSH Key Attribute", order=850, description=""
			+ "Optionally specify name of the attribute inside the user LDAP entry whose values will be taken as user "
			+ "SSH keys. SSH keys will be managed by LDAP only if this field is set")
	public String getUserSshKeyAttribute() {
		return userSshKeyAttribute;
	}

	public void setUserSshKeyAttribute(String userSshKeyAttribute) {
		this.userSshKeyAttribute = userSshKeyAttribute;
	}

	@Editable(order=900, description="Specify the strategy to retrieve group membership information. "
			+ "To give appropriate permissions to a LDAP group, a OneDev group with same name should "
			+ "be defined. Use strategy <tt>Do Not Retrieve Groups</tt> if you want to manage group "
			+ "memberships at OneDev side")
	@NotNull(message="may not be empty")
	public GroupRetrieval getGroupRetrieval() {
		return groupRetrieval;
	}

	public void setGroupRetrieval(GroupRetrieval groupRetrieval) {
		this.groupRetrieval = groupRetrieval;
	}

	@Override
	public Authenticated authenticate(UsernamePasswordToken token) {
		String fullName = null;
		String email = null;
		Collection<String> groupNames = null;
        Collection<String> sshKeys = null;

        String userSearchFilter = StringUtils.replace(getUserSearchFilter(), "{0}", 
        		escape(token.getUsername()));
        userSearchFilter = StringUtils.replace(userSearchFilter, "\\", "\\\\");
        logger.debug("Evaluated user search filter: " + userSearchFilter);
        
        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        List<String> attributeNames = new ArrayList<String>();
        if (getUserFullNameAttribute() != null)
            attributeNames.add(getUserFullNameAttribute());
        
        if (getUserSshKeyAttribute() != null)
        	attributeNames.add(getUserSshKeyAttribute());
        if (getUserEmailAttribute() != null)
        	attributeNames.add(getUserEmailAttribute());
        
        if (getGroupRetrieval() instanceof GetGroupsUsingAttribute) {
        	GetGroupsUsingAttribute groupRetrieval = (GetGroupsUsingAttribute)getGroupRetrieval();
            attributeNames.add(groupRetrieval.getUserGroupsAttribute());
        }
        searchControls.setReturningAttributes((String[]) attributeNames.toArray(new String[0]));
        searchControls.setReturningObjFlag(true);

        Hashtable<String, String> ldapEnv = new Hashtable<>();
		if (getLdapUrl().trim().startsWith("ldaps"))
			ldapEnv.put("java.naming.ldap.factory.socket", TrustCertsSSLSocketFactory.class.getName());
        ldapEnv.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        ldapEnv.put(Context.PROVIDER_URL, getLdapUrl());
        ldapEnv.put(Context.SECURITY_AUTHENTICATION, "simple");
        ldapEnv.put("com.sun.jndi.ldap.connect.timeout", String.valueOf(getTimeout()*1000L));
        ldapEnv.put("com.sun.jndi.ldap.read.timeout", String.valueOf(getTimeout()*1000L));
        ldapEnv.put(Context.REFERRAL, "follow");
        
        if (isAuthenticationRequired()) {
	        ldapEnv.put(Context.SECURITY_PRINCIPAL, getManagerDN());
	        ldapEnv.put(Context.SECURITY_CREDENTIALS, getManagerPassword());
        }

        DirContext ctx = null;
        DirContext referralCtx = null;
        try {
            logger.debug("Binding to ldap url '" + getLdapUrl() + "'...");
            try {
            	ctx = new InitialDirContext(ldapEnv);
            } catch (AuthenticationException e) {
        		throw new RuntimeException("Can not bind to ldap server '" + getLdapUrl() + "': " + e.getMessage());
            }

			NamingEnumeration<SearchResult> results = null;
			for (var userSearchBase: getUserSearchBases()) {
				 results = ctx.search(new CompositeName().add(userSearchBase), 
						userSearchFilter, searchControls);
				 if (results.hasMore())
					 break;
			}
			if (results == null)
				throw new ExplicitException("No user search base specified");
			if (!results.hasMore())
				throw new UnknownAccountException(Translation.get("Invalid_Credentials"));
            
            SearchResult searchResult = results.next();
            String userDN = searchResult.getNameInNamespace();
            if (!searchResult.isRelative()) {
            	StringBuilder builder = new StringBuilder();
                builder.append(StringUtils.substringBefore(searchResult.getName(), "//"));
                builder.append("//");
                builder.append(StringUtils.substringBefore(
                		StringUtils.substringAfter(searchResult.getName(), "//"), "/"));
                
                ldapEnv.put(Context.PROVIDER_URL, builder.toString());
                logger.debug("Binding to referral ldap url '" + builder.toString() + "'...");
                referralCtx = new InitialDirContext(ldapEnv);
            }
            if (userDN.startsWith("ldap")) {
            	userDN = StringUtils.substringAfter(userDN, "//");
            	userDN = StringUtils.substringAfter(userDN, "/");
            }

            ldapEnv.put(Context.SECURITY_PRINCIPAL, userDN);
            ldapEnv.put(Context.SECURITY_CREDENTIALS, new String(token.getPassword()));
            DirContext userCtx = null;
            try {
                userCtx = new InitialDirContext(ldapEnv);
            } catch (AuthenticationException e) {
                logger.error("Unable to bind as '" + userDN + "'", e);
            	throw new org.apache.shiro.authc.AuthenticationException(Translation.get("Invalid_Credentials"));
            } finally {
                if (userCtx != null) {
                    try {
                        userCtx.close();
                    } catch (NamingException e) {
                    }
                }
            }
            
            DirContext effectiveCtxt = referralCtx != null? referralCtx: ctx;
            Attributes searchResultAttributes = searchResult.getAttributes();
            if (searchResultAttributes != null) {
                if (getUserFullNameAttribute() != null) {
                    Attribute attribute = searchResultAttributes.get(getUserFullNameAttribute());
                    if (attribute != null && attribute.get() != null)
                        fullName = (String) attribute.get();
                }
                
				if (getUserEmailAttribute() != null) {
					Attribute attribute = searchResultAttributes.get(getUserEmailAttribute());
					if (attribute != null && attribute.get() != null)
						email = (String) attribute.get();
				}
                
                if (getGroupRetrieval() instanceof GetGroupsUsingAttribute) 
                	groupNames = retrieveGroupsByAttribute(effectiveCtxt, searchResultAttributes);
                
                if (getUserSshKeyAttribute() != null) 
                	sshKeys = retrieveSshKeys(searchResultAttributes);
            }
            
            if (getGroupRetrieval() instanceof SearchGroupsUsingFilter) 
            	groupNames = retrieveGroupsByFilter(effectiveCtxt, userDN);
            
			return new Authenticated(email, fullName, groupNames, sshKeys);
        } catch (NamingException e) {
        	throw new RuntimeException(e);
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException e) {
                }
            }
            if (referralCtx != null) {
                try {
                    referralCtx.close();
                } catch (NamingException e) {
                }
            }
        }
	}
	
	private Collection<String> retrieveGroupsByAttribute(DirContext ctx, Attributes searchResultAttributes) {
		Collection<String> groupNames = new HashSet<>();
		try {
        	GetGroupsUsingAttribute groupRetrieval = (GetGroupsUsingAttribute) getGroupRetrieval();
            Attribute attribute = searchResultAttributes.get(groupRetrieval.getUserGroupsAttribute());
            if (attribute != null) {
                for (NamingEnumeration<?> e = attribute.getAll(); e.hasMore();) {

                	// use composite name instead of DN according to
                    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4307193
                    Name groupDN = new CompositeName().add((String) e.next());
                    logger.debug("Looking up group entry '" + groupDN + "'...");
                    
                    DirContext groupCtx = null;
                    try {
                        groupCtx = (DirContext) ctx.lookup(groupDN);

                        if (groupCtx == null) {
                            throw new RuntimeException("Can not find group entry " +
                            		"identified by '" + groupDN + "'.");
                        }
                        String groupNameAttribute = groupRetrieval.getGroupNameAttribute();
                        Attributes groupAttributes = groupCtx.getAttributes("", 
                        		new String[]{groupNameAttribute});
                        if (groupAttributes == null 
                        		|| groupAttributes.get(groupNameAttribute) == null
                                || groupAttributes.get(groupNameAttribute).get() == null) {
                            throw new RuntimeException("Can not find attribute '" 
                            		+ groupNameAttribute + "' in returned group entry.");
                        }
                        groupNames.add((String) groupAttributes.get(groupNameAttribute).get());
                    } finally {
                        if (groupCtx != null) {
                            try {
                                groupCtx.close();
                            } catch (NamingException ne) {
                            }
                        }
                    }
                }
            } else {
                logger.warn("No attribute identified by '" + groupRetrieval.getUserGroupsAttribute() 
                		+ "' inside fetched user LDAP entry.");
            }
		} catch (NamingException e) {
			logger.error("Error retrieving groups by attribute");
		}
		return groupNames;
	}
	
	private Collection<String> retrieveGroupsByFilter(DirContext ctx, String userDN) {
		Collection<String> groupNames = new HashSet<>();
		try {
	    	SearchGroupsUsingFilter groupRetrieval = (SearchGroupsUsingFilter) getGroupRetrieval();
	    	String groupNameAttribute = groupRetrieval.getGroupNameAttribute();
	        Name groupSearchBase = new CompositeName().add(groupRetrieval.getGroupSearchBase());
	        String groupSearchFilter = StringUtils.replace(groupRetrieval.getGroupSearchFilter(), "{0}", userDN);
	        groupSearchFilter = StringUtils.replace(groupSearchFilter, "\\", "\\\\");
	
	        logger.debug("Evaluated group search filter: " + groupSearchFilter);
	        SearchControls searchControls = new SearchControls();
	        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
	        searchControls.setReturningAttributes(new String[]{groupNameAttribute});
	        searchControls.setReturningObjFlag(true);
	
	    	NamingEnumeration<SearchResult> results = ctx.search(groupSearchBase, groupSearchFilter, searchControls);
	        if (results != null) {
	            while (results.hasMore()) {
	            	SearchResult searchResult = (SearchResult) results.next();
	                Attributes searchResultAttributes = searchResult.getAttributes();
	                if (searchResultAttributes == null 
	                		|| searchResultAttributes.get(groupNameAttribute) == null
	                        || searchResultAttributes.get(groupNameAttribute).get() == null) {
	                    throw new RuntimeException("Can not find attribute '" 
	                    		+ groupNameAttribute + "' in the returned group object.");
	                }
	                groupNames.add((String) searchResultAttributes.get(groupNameAttribute).get());
	            }
	        }
        } catch (PartialResultException pre) {
            logger.warn("Partial exception detected. You may try to set property " +
            		"'follow referrals' to true to avoid this exception.", pre);
		} catch (NamingException e) {
			logger.error("Error retrieving groups by filter", e);
		}
		return groupNames;
	}

	@Nullable
	private Collection<String> retrieveSshKeys(Attributes searchResultAttributes) {
		Attribute attribute = searchResultAttributes.get(getUserSshKeyAttribute());
		if (attribute != null) {
			Collection<String> sshKeys = new ArrayList<>();
			try {
				NamingEnumeration<?> ldapValues = attribute.getAll();
				while (ldapValues.hasMore()) {
					Object value = ldapValues.next();
					if (value instanceof String) 
						sshKeys.add((String) value);
					else 
						logger.error("SSH key from ldap is not a String");
				}

			} catch (NamingException e) {
				logger.error("Error retrieving SSH keys", e);
			}
			return sshKeys;
		} else {
			return null;
		}
	}

	@Override
	public boolean isManagingMemberships() {
		return !(getGroupRetrieval() instanceof DoNotRetrieveGroups);
	}
	
	/* Copied from Spring LdapEncoder.java */
    private static String[] FILTER_ESCAPE_TABLE = new String['\\' + 1];

    static {

        // Filter encoding table -------------------------------------

        // fill with char itself
        for (char c = 0; c < FILTER_ESCAPE_TABLE.length; c++) {
            FILTER_ESCAPE_TABLE[c] = String.valueOf(c);
        }

        // escapes (RFC2254)
        FILTER_ESCAPE_TABLE['*'] = "\\2a";
        FILTER_ESCAPE_TABLE['('] = "\\28";
        FILTER_ESCAPE_TABLE[')'] = "\\29";
        FILTER_ESCAPE_TABLE['\\'] = "\\5c";
        FILTER_ESCAPE_TABLE[0] = "\\00";

    }

    private static String escape(String value) {
        // make buffer roomy
        StringBuilder encodedValue = new StringBuilder(value.length() * 2);

        int length = value.length();

        for (int i = 0; i < length; i++) {

            char c = value.charAt(i);

            if (c < FILTER_ESCAPE_TABLE.length) {
                encodedValue.append(FILTER_ESCAPE_TABLE[c]);
            } else {
                // default: add the char
                encodedValue.append(c);
            }
        }

        return encodedValue.toString();
    }	
	
}
