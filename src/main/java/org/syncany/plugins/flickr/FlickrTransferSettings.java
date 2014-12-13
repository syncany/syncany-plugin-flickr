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

import java.io.StringWriter;

import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.core.Persister;
import org.syncany.plugins.transfer.Encrypted;
import org.syncany.plugins.transfer.Setup;
import org.syncany.plugins.transfer.TransferPluginOptionCallback;
import org.syncany.plugins.transfer.TransferSettings;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.auth.AuthInterface;
import com.flickr4java.flickr.auth.Permission;
import com.flickr4java.flickr.people.User;

public class FlickrTransferSettings extends TransferSettings {
	private Flickr flickr;
	private AuthInterface authInterface;
	private Token authToken;

	@Element(name = "key", required = true)
	@Setup(order = 1, singular = true, description = "API Key")	
	public String key;
	
	@Element(name = "secret", required = true)
	@Setup(order = 2, sensitive = true, singular = true, description = "API Key Secret")
	@Encrypted
	public String secret;
	
	@Element(name = "serializedAuth", required = true)
	@Setup(order = 3, sensitive = true, singular = true, description = "Token", callback = FlickrAuthPluginOptionCallback.class)
	public String serializedAuth;

	@Element(name = "album", required = true)
	@Setup(order = 4, description = "Album ID")
	public String album;

	public String getKey() {
		return key;
	}
	
	public String getSecret() {
		return secret;
	}
	
	public String getSerializedAuth() {
		return serializedAuth;
	}
	
	public String getAlbum() {
		return album;
	}
	
	public class FlickrAuthPluginOptionCallback implements TransferPluginOptionCallback {
		@Override
		public String preQueryCallback() {
			flickr = new Flickr(key, secret, new REST());
			Flickr.debugStream = false;
			
			authInterface = flickr.getAuthInterface();			
			authToken = authInterface.getRequestToken();		
			
			String authUrl = authInterface.getAuthorizationUrl(authToken, Permission.DELETE);

			return String.format(
				      "\n"
					+ "Follow this URL to authorize yourself on Flickr:\n\n"
					+ "    %s\n\n"
					+ "Input the token it gives you.\n", authUrl);
		}

		@Override
		public String postQueryCallback(String optionValue) {
			try {
				String authTokenKey = optionValue;
				
				Token requestToken = authInterface.getAccessToken(authToken, new Verifier(authTokenKey));
				System.out.println("Authentication success");
				
				Auth auth = authInterface.checkToken(requestToken);
				
				flickr.setAuth(auth);
				RequestContext.getRequestContext().setAuth(auth);
				
				// This token can be used until the user revokes it.
				System.out.println("Token: " + requestToken.getToken());
				System.out.println("Secret: " + requestToken.getSecret());
				System.out.println("nsid: " + auth.getUser().getId());
				System.out.println("Realname: " + auth.getUser().getRealName());
				System.out.println("Username: " + auth.getUser().getUsername());
				System.out.println("Permission: " + auth.getPermission().getType());
				
				FlickrAuth wrappedAuth = new FlickrAuth(auth);				
				StringWriter serializedAuthStr = new StringWriter();
				
				new Persister().write(wrappedAuth, serializedAuthStr);
				
				serializedAuth = serializedAuthStr.toString();
				System.out.println(serializedAuth);
				
				return null;
			}
			catch (Exception e) {
				throw new RuntimeException("Error requesting Flickr data: " + e.getMessage(), e);
			}
		}
	}
	
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
