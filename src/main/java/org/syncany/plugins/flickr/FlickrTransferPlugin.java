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

import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.syncany.plugins.transfer.TransferPlugin;

public class FlickrTransferPlugin extends TransferPlugin {
	public static final String APP_KEY = "52ce44c5ea23ad0219f7099af69748fb";
	public static final String APP_SECRET = "08929f959267bf7c";
	
	static {
		try {
			// Remove debug output from Log4J console appender!
			// See issue #304.
			
			Logger.getRootLogger().removeAllAppenders();
			Logger.getRootLogger().addAppender(new NullAppender());
		}
		catch (Exception e) {
			// Don't care!
		}
	}
	
	public FlickrTransferPlugin() {
		super("flickr");
	}
}
