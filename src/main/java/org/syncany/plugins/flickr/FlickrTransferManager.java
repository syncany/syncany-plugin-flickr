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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.simpleframework.xml.core.Persister;
import org.syncany.config.Config;
import org.syncany.plugins.flickr.FlickrTransferSettings.FlickrAuth;
import org.syncany.plugins.flickr.util.PngEncoder;
import org.syncany.plugins.transfer.AbstractTransferManager;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.files.RemoteFile;
import org.syncany.plugins.transfer.files.SyncanyRemoteFile;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photosets.Photoset;
import com.flickr4java.flickr.uploader.UploadMetaData;
import com.flickr4java.flickr.uploader.Uploader;
import com.google.common.collect.Sets;

public class FlickrTransferManager extends AbstractTransferManager {
	private static final Logger logger = Logger.getLogger(FlickrTransferManager.class.getSimpleName());

	private Flickr flickr;
	private Auth auth;
	private String photosetId;	
	private Map<RemoteFile, Photo> remoteFilePhotoIdCache;

	public FlickrTransferManager(FlickrTransferSettings settings, Config config) throws Exception {
		super(settings, config);

		this.flickr = new Flickr(settings.getKey(), settings.getSecret(), new REST());
		this.auth = new Persister().read(FlickrAuth.class, settings.getSerializedAuth()).toAuth();
		this.photosetId = settings.getAlbum();
		this.remoteFilePhotoIdCache = new HashMap<RemoteFile, Photo>();
		
		// Init Flickr object
		flickr.setAuth(auth);
		RequestContext.getRequestContext().setAuth(auth);
		
		Flickr.debugRequest = false;
		Flickr.debugStream = false;
	}

	@Override
	public void connect() throws StorageException {
		// Nothing
	}

	@Override
	public void disconnect() {
		// Nothing
	}

	@Override
	public void init(boolean createIfRequired) throws StorageException {
		// Nothing
	}

	@Override
	public void download(RemoteFile remoteFile, File localFile) throws StorageException {
		
	}

	@Override
	public void upload(File localFile, RemoteFile remoteFile) throws StorageException {
		try {
			UploadMetaData metaData = new UploadMetaData();
			
			metaData.setFilename(remoteFile.getName() + ".png");
			metaData.setTitle(remoteFile.getName());
			metaData.setFilemimetype("image/png");	
			metaData.setTags(Sets.newHashSet("type='" + remoteFile.getClass().getSimpleName() + "'"));
					
			byte[] fileContents = FileUtils.readFileToByteArray(localFile);
			
			if (fileContents.length == 0) {
				fileContents = new byte[1024];
			}
			
			// Encode local file to PNG image
			ByteArrayOutputStream encodedPngOutputStream = new ByteArrayOutputStream();

			//File pngEncodedTempFile = config.getCache().createTempFile(remoteFile.getName());
			PngEncoder.encodeToPng(fileContents, encodedPngOutputStream);
			encodedPngOutputStream.close();
			
			byte[] pngEncodedFileContents = encodedPngOutputStream.toByteArray();
			
			FileUtils.writeByteArrayToFile(new File("/tmp/testfile"), pngEncodedFileContents);
			//FileUtils.copyFile(pngEncodedTempFile, new File("/tmp/testfile"));
			
			// Upload PNG image to Flickr 
			Uploader uploader = flickr.getUploader();
			String photoId = uploader.upload(pngEncodedFileContents, metaData);

			// Add image to photoset (album)
			flickr.getPhotosetsInterface().addPhoto(photosetId, photoId);
			
			logger.log(Level.INFO, "Uploaded file " + localFile + " to " + remoteFile + ", as photo ID " + photoId);			
		}
		catch (Exception e) {
			throw new StorageException("Cannot upload file " + localFile + " to remote file ", e);
		}
	}

	@Override
	public boolean delete(RemoteFile remoteFile) throws StorageException {
		return tryDelete(remoteFile, true);
	}
	
	public boolean tryDelete(RemoteFile remoteFile, boolean retry) throws StorageException {
		try {
			Photo photo = remoteFilePhotoIdCache.get(remoteFile);
			
			if (photo != null) {
				flickr.getPhotosInterface().delete(photo.getId());
				return true;
			}
			else {
				list(remoteFile.getClass()); // Update cache!
				
				if (retry) {
					return tryDelete(remoteFile, false);
				}
				else {
					return false;
				}
			}
		}
		catch (Exception e) {
			throw new StorageException(e);
		}		
	}

	@Override
	public void move(RemoteFile sourceFile, RemoteFile targetFile) throws StorageException {
		
	}

	@Override
	public <T extends RemoteFile> Map<String, T> list(Class<T> remoteFileClass) throws StorageException {
		try {
			Map<String, T> fileList = new HashMap<String, T>();
					
			boolean morePhotos = true;
			int maxPhotos = 1000;
			int currentPage = 1;
			
			while (morePhotos) {
				PhotoList<Photo> partialPhotoList = flickr.getPhotosetsInterface().getPhotos(photosetId, maxPhotos, currentPage);
				
				for (Photo photo : partialPhotoList) {
					try {
						RemoteFile remoteFile = RemoteFile.createRemoteFile(photo.getTitle());
						
						if (remoteFile.getClass().equals(remoteFileClass)) {					
							T concreteRemoteFile = remoteFileClass.cast(remoteFile);					
							fileList.put(remoteFile.getName(), concreteRemoteFile);
						}
						
						System.out.println(photo.getTitle());
						remoteFilePhotoIdCache.put(remoteFile, photo);
					}
					catch (Exception e) {
						// Ignore invalid filenames
					}					
				}
				
				if (partialPhotoList.size() < 1000) {
					morePhotos = false;
				}
				else {
					currentPage++;
					morePhotos = true;
				}
			}
			
			return fileList;
		}
		catch (FlickrException e) {
			throw new StorageException(e);
		}
	}
	
	@Override
	public boolean testTargetCanWrite() {
		return true; // TODO
	}

	@Override
	public boolean testTargetExists() {
		try {
			Photoset photoset = flickr.getPhotosetsInterface().getInfo(photosetId);
			return photoset != null;
		}
		catch (FlickrException e) {
			logger.log(Level.SEVERE, "Cannot get information about photoset.", e);
			return false;
		}		
	}

	@Override
	public boolean testTargetCanCreate() {
		return true;
	}

	@Override
	public boolean testRepoFileExists() {
		try {
			return list(SyncanyRemoteFile.class).size() == 1;
		}
		catch (StorageException e) {
			logger.log(Level.SEVERE, "Cannot get information about repo file.", e);
			return false;
		}		
	}	
}
