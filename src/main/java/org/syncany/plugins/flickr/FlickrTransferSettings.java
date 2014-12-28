/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.plugins.flickr;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.simpleframework.xml.Element;
import org.syncany.plugins.flickr.FlickrTransferSettings.FlickrOAuthGenerator;
import org.syncany.plugins.transfer.OAuth;
import org.syncany.plugins.transfer.OAuthGenerator;
import org.syncany.plugins.transfer.Setup;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.TransferSettings;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.auth.AuthInterface;
import com.flickr4java.flickr.auth.Permission;
import com.flickr4java.flickr.people.User;

@OAuth(FlickrOAuthGenerator.class)
public class FlickrTransferSettings extends TransferSettings {
	private static final Logger logger = Logger.getLogger(FlickrTransferSettings.class.getSimpleName());

	private Flickr flickr;
	private AuthInterface authInterface;
	private Token authToken;

	@Element(name = "album", required = false)
	@Setup(order = 1, description = "Album ID")
	public String album;

	@Element(name = "auth", required = true)
	@Setup(visible = false)
	public FlickrAuth auth;
	
	public String getAlbum() {
		return album;
	}
	
	public void setAlbum(String album) {
		this.album = album;
	}
	
	public FlickrAuth getAuth() {
		return auth;
	}	
	
	public class FlickrOAuthGenerator implements OAuthGenerator {	
		@Override
		public URI generateAuthUrl() throws StorageException {
			logger.log(Level.INFO, "Creating Flickr instance to get OAuth URL.");
			
			flickr = new Flickr(FlickrTransferPlugin.APP_KEY, FlickrTransferPlugin.APP_SECRET, new REST());
			Flickr.debugStream = false;
			
			authInterface = flickr.getAuthInterface();			
			authToken = authInterface.getRequestToken();		
			
			String authUrl = authInterface.getAuthorizationUrl(authToken, Permission.DELETE);
			logger.log(Level.INFO, "OAuth Token is " + authToken + ", auth URL is " + authUrl);

			try {
				return new URI(authUrl);
			}
			catch (URISyntaxException e) {
				throw new StorageException(e);
			}
		}

		@Override
		public void checkToken(String authTokenKey) throws StorageException {
			try {
				// Trade auth token for request token
				logger.log(Level.INFO, "Now trading auth token for request token ...");			
				Token requestToken = authInterface.getAccessToken(authToken, new Verifier(authTokenKey));
								
				// Check request token
				logger.log(Level.INFO, "Request token is " + requestToken.getToken() + "; Now checking token ...");
				Auth originalAuth = authInterface.checkToken(requestToken);
				
				// Let all requests (via Flickr object and others) use this auth.
				flickr.setAuth(originalAuth);
				RequestContext.getRequestContext().setAuth(originalAuth);
				
				// Save auth to config				
				auth = new FlickrAuth(originalAuth);				
				logger.log(Level.INFO, "Flickr auth object is: " + auth);									
			}
			catch (Exception e) {
				throw new RuntimeException("Error requesting Flickr data: " + e.getMessage(), e);
			}
		}
	}
	
	/**
	 * Wrapper class used to store the Flickr authentication in the 
	 * config settings. This is used instead of {@link Auth}.
	 */
	public static class FlickrAuth {
		private String token;
		private String tokenSecret;
		private User user;
		private String permission;
		
		public FlickrAuth() {
			// Nothing
		}
		
		public FlickrAuth(Auth auth) {
			token = auth.getToken();
			tokenSecret = auth.getTokenSecret();
			permission = auth.getPermission().toString();
			user = auth.getUser();
		}
		
		public Auth toAuth() {
			Auth auth = new Auth();
			
			auth.setToken(token);
			auth.setTokenSecret(tokenSecret);
			auth.setPermission(Permission.fromString(permission));
			auth.setUser(user);
			
			return auth;
		}
		
		public String getToken() {
			return token;
		}
		
		public String getTokenSecret() {
			return tokenSecret;
		}
		
		public User getUser() {
			return user;
		}
		
		public String getPermission() {
			return permission;
		}
	}
}
