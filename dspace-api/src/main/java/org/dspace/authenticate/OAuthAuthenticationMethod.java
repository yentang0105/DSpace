/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate;

import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.authority.orcid.Orcid;
import org.dspace.authority.orcid.model.Bio;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.eperson.EPerson;

/**
 *
 * @author mdiggory at atmire.com
 */
public class OAuthAuthenticationMethod implements AuthenticationMethod{

    /** log4j category */
    private static Logger log = Logger.getLogger(OAuthAuthenticationMethod.class);

    @Override
    public boolean canSelfRegister(Context context, HttpServletRequest request, String username) throws SQLException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void initEPerson(Context context, HttpServletRequest request, EPerson eperson) throws SQLException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean allowSetPassword(Context context, HttpServletRequest request, String username) throws SQLException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean isImplicit() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int[] getSpecialGroups(Context context, HttpServletRequest request) throws SQLException {
        return new int[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int authenticate(Context context, String username, String password, String realm, HttpServletRequest request) throws SQLException {


        String email = null;

        String orcid = (String) request.getAttribute("orcid");
        //String refreshToken = (String) request.getAttribute("refresh_token");
        if (request == null||orcid==null)
        {
            return BAD_ARGS;
        }
        
        EPerson eperson = EPerson.findByNetid(context, orcid);
        
        // No email address, perhaps the eperson has been setup, better check it
        if (eperson == null)
        {
        	eperson = context.getCurrentUser();
            if (eperson != null)
            {
                //if eperson exists then get ORCID Profile and binding data to Eperson Account
                email = eperson.getEmail();
            }
        }
        //get the orcid profile
        Bio bio = null;
        Orcid orcidObject = Orcid.getOrcid();
        if(orcid!=null)
        {
            	// try to retrieve public information
            	bio = orcidObject.getBio(orcid);
            
        }
        //get the email from orcid
        if(bio!=null && email == null)
        {
            email = bio.getEmail();
        }

//        //If Eperson does not exist follow steps similar to Shib....
//        if (eperson == null && email == null)
//        {
//            log.error("No email is given, you're denied access by OAuth, please release email address");
//            return AuthenticationMethod.BAD_ARGS;
//        }

        if (email != null) {
        	email = email.toLowerCase();
        }
        
        String fname = "";
        String lname = "";
        if (bio != null && bio.getName() != null)
        {
            // try to grab name from the orcid profile
            fname = bio.getName().getGivenNames();
        
            // try to grab name from the orcid profile
            lname = bio.getName().getFamilyName();
        }

        if (eperson == null && email != null) {
	        try
	        {
	            eperson = EPerson.findByEmail(context, email);
	        }
	        catch (AuthorizeException e)
	        {
	            log.warn("Fail to locate user with email:" + email, e);
	            eperson = null;
	        }
        }
        
        try
        {
        	// TEMPORARILY turn off authorisation
            context.turnOffAuthorisationSystem();
	        // auto create user if needed
	        if (eperson == null
	                && ConfigurationManager
	                .getBooleanProperty("authentication-oauth", "autoregister") && email!=null)
	        {
	            log.info(LogManager.getHeader(context, "autoregister", "orcid="
	                    + orcid));
            
                eperson = EPerson.create(context);
                eperson.setEmail(email);
                eperson.setFirstName(fname);
                eperson.setLastName(lname);
                eperson.setCanLogIn(true);
                AuthenticationManager.initEPerson(context, request, eperson);
                eperson.setNetid(orcid);
//                eperson.setMetadata("access_token",token);
                eperson.update();
                context.commit();
                context.setCurrentUser(eperson);
	        }
	        else if(eperson!=null)
	        {
	            //found the eperson , update the eperson record with orcid id
                eperson.setNetid(orcid);
                if (eperson.getEmail() == null) {
                	eperson.setEmail(email);
                } 
                //eperson.setMetadata("access_token",token);
                eperson.update();
                context.commit();
	        }
        }
        catch (AuthorizeException e)
        {
            log.warn("Fail to authorize user with email:" + email, e);
            eperson = null;
        }
        finally
        {
            context.restoreAuthSystemState();
        }

        if (eperson == null)
        {
            return AuthenticationMethod.NO_SUCH_USER;
        }
        else
        {
            // the person exists, just return ok
            context.setCurrentUser(eperson);
            request.getSession().setAttribute("oauth.authenticated",
                    Boolean.TRUE);
        }

        return AuthenticationMethod.SUCCESS;
    }
    @Override
    public String loginPageURL(Context context, HttpServletRequest request, HttpServletResponse response) {
        if(ConfigurationManager.getBooleanProperty("authentication-oauth","choice-page")){
            return response.encodeRedirectURL(request.getContextPath()
                + "/oauth-login");
        }
        else
        {
            return null;
        }
    }

    @Override
    public String loginPageTitle(Context context) {
        return "org.dspace.authenticate.OAuthAuthentication.title";
    }
}
