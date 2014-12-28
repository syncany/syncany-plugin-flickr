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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.syncany.config.Config;
import org.syncany.database.MultiChunkEntry.MultiChunkId;
import org.syncany.plugins.transfer.AbstractTransferManager;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.files.MultichunkRemoteFile;
import org.syncany.plugins.transfer.files.RemoteFile;
import org.syncany.plugins.transfer.files.SyncanyRemoteFile;
import org.syncany.plugins.transfer.files.TempRemoteFile;

import com.flickr4java.flickr.Flickr;
import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.REST;
import com.flickr4java.flickr.RequestContext;
import com.flickr4java.flickr.auth.Auth;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotoList;
import com.flickr4java.flickr.photos.Size;
import com.flickr4java.flickr.photosets.Photoset;
import com.flickr4java.flickr.uploader.UploadMetaData;
import com.flickr4java.flickr.uploader.Uploader;

public class FlickrTransferManager extends AbstractTransferManager {
	private static final Logger logger = Logger.getLogger(FlickrTransferManager.class.getSimpleName());
	private static final int FLICKR_MIN_IMAGE_BYTES = 17*17*3; // < 16x16 PNGs are rejected sometimes!

	private Flickr flickr;
	private Auth auth;
	private String photosetId;	
	private Map<RemoteFile, Photo> remoteFilePhotoIdCache;

	public FlickrTransferManager(FlickrTransferSettings settings, Config config) throws Exception {
		super(settings, config);

		this.flickr = new Flickr(FlickrTransferPlugin.APP_KEY, FlickrTransferPlugin.APP_SECRET, new REST());
		this.auth = settings.getAuth().toAuth();
		this.photosetId = settings.getAlbum();
		this.remoteFilePhotoIdCache = new HashMap<RemoteFile, Photo>();
		
		// Init Flickr object
		flickr.setAuth(auth);
		RequestContext.getRequestContext().setAuth(auth);
		
		Flickr.debugRequest = false;
		Flickr.debugStream = false;
	}

	public FlickrTransferSettings getSettings() {
		return (FlickrTransferSettings) settings;
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
		if (createIfRequired) {
			if (photosetId == null) {
				logger.log(Level.INFO, "Flickr Init: Create target enabled, and NO album ID given. Creating album ...");				
				
				photosetId = createNewAlbum();					
				getSettings().setAlbum(photosetId);
			}
			else {
				logger.log(Level.INFO, "Flickr Init: Create target enabled, but album ID given (" + photosetId + "). Using this album. Nothing to do.");				
			}
		}
		else {
			if (photosetId == null) {
				logger.log(Level.INFO, "Flickr Init: Create target NOT enabled, and NO album ID given. Cannot continue.");				
				throw new StorageException("Album ID required if 'create target' option not selected.");
			}
			else {
				logger.log(Level.INFO, "Flickr Init: Create target NOT enabled, album ID given (" + photosetId + "). Using this album. Nothing to do.");
			}
		}
	}

	private String createNewAlbum() throws StorageException {
		try {
			// Create and upload dummy file (album needs at least one photo)
		    Path dummyFileTempPath = Files.createTempFile("syncany-temp", ".tmp");
	        Files.write(dummyFileTempPath, "Syncany rocks!".getBytes());	        
	        
	        String dummyPhotoId = upload(dummyFileTempPath.toFile(), new TempRemoteFile(new MultichunkRemoteFile(MultiChunkId.secureRandomMultiChunkId())), false);
	        
			Files.delete(dummyFileTempPath);

			// Create album		        
			String title = "Syncany " + (1000 + Math.abs(new Random().nextInt(8999)));
			String description = "Flickr-based Syncany repository. Details at www.syncany.org!";
			
			Photoset photoset = flickr.getPhotosetsInterface().create(title, description, dummyPhotoId);
			return photoset.getId();			
		}
		catch (Exception e) {
			throw new StorageException("Cannot initialize repository. Creating Flickr album failed.", e);
		}
	}

	@Override
	public void download(RemoteFile remoteFile, File localFile) throws StorageException {
		Photo photo = getPhoto(remoteFile);
		
		try {
			// Copy PNG file to local cache; This indirection is necessary, because there are some 
			// ZIP/stream issues when the input stream is directly handed to the PNG decoder.
			
			InputStream rawImageStream = flickr.getPhotosInterface().getImageAsStream(photo, Size.ORIGINAL);
			File tmpFile = createTempFile(remoteFile.getName());
			
			FileUtils.copyInputStreamToFile(rawImageStream, tmpFile);	
			rawImageStream.close();
			
			// Decode PNG from file to byte array and write to final file (removes Flickr-bug padding)
			byte[] paddedPngData = PngEncoder.decodeFromPng(tmpFile);
			FileOutputStream localFileOutputStream = new FileOutputStream(localFile);
			
			localFileOutputStream.write(paddedPngData, FLICKR_MIN_IMAGE_BYTES, paddedPngData.length-FLICKR_MIN_IMAGE_BYTES);
			localFileOutputStream.close();
		}
		catch (Exception e) {
			throw new StorageException("Cannot download image " + remoteFile + ", Flickr photo ID " + photo.getId(), e);
		}		
	}

	@Override
	public void upload(File localFile, RemoteFile remoteFile) throws StorageException {
		upload(localFile, remoteFile, true);
	}	

	private String upload(File localFile, RemoteFile remoteFile, boolean addToPhotoset) throws StorageException {
		try {
			UploadMetaData metaData = new UploadMetaData();
			
			metaData.setFilename(remoteFile.getName() + ".png");
			metaData.setTitle(remoteFile.getName());
			metaData.setFilemimetype("image/png");	
				
			// Some weird Flickr bug: Images with dimensions < 17x17 are sometimes rejected.			
			ByteArrayOutputStream paddedContentsOutputStream = new ByteArrayOutputStream();			
			paddedContentsOutputStream.write(new byte[FLICKR_MIN_IMAGE_BYTES]);
			paddedContentsOutputStream.write(FileUtils.readFileToByteArray(localFile));
			
			byte[] fileContents = paddedContentsOutputStream.toByteArray();
			
			// Encode local file to PNG image
			ByteArrayOutputStream encodedPngOutputStream = new ByteArrayOutputStream();

			PngEncoder.encodeToPng(fileContents, encodedPngOutputStream);
			encodedPngOutputStream.close();
			
			byte[] pngEncodedFileContents = encodedPngOutputStream.toByteArray();			
			
			// Upload PNG image to Flickr 
			Uploader uploader = flickr.getUploader();
			String photoId = uploader.upload(pngEncodedFileContents, metaData);

			// Add image to photoset (album)
			if (addToPhotoset) {
				flickr.getPhotosetsInterface().addPhoto(photosetId, photoId);
			}
			
			logger.log(Level.INFO, "Uploaded file " + localFile + " to " + remoteFile + ", as photo ID " + photoId);
			return photoId;
		}
		catch (Exception e) {
			throw new StorageException("Cannot upload file " + localFile + " to remote file ", e);
		}
	}	

	@Override
	public boolean delete(RemoteFile remoteFile) throws StorageException {		
		try {
			Photo photo = getPhoto(remoteFile);					
			flickr.getPhotosInterface().delete(photo.getId());
			
			return true;
		}
		catch (Exception e) {
			logger.log(Level.WARNING, "Cannot delete remote file " + remoteFile + ". IGNORING.", e);
			return false;
		}		
	}

	@Override
	public void move(RemoteFile sourceFile, RemoteFile targetFile) throws StorageException {		
		try {
			Photo photo = getPhoto(sourceFile);			
			flickr.getPhotosInterface().setMeta(photo.getId(), targetFile.getName(), null);
		}
		catch (Exception e) {
			throw new StorageException(e);
		}		
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
		return true;
	}

	@Override
	public boolean testTargetExists() {
		try {
			if (photosetId == null) {
				return false;
			}
			else {
				Photoset photoset = flickr.getPhotosetsInterface().getInfo(photosetId);
				return photoset != null;
			}
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

	private Photo getPhoto(RemoteFile remoteFile) throws StorageException {
		Photo photo = remoteFilePhotoIdCache.get(remoteFile);
		
		if (photo != null) {
			return photo;
		}
		else {
			list(remoteFile.getClass()); // Update cache!
			
			photo = remoteFilePhotoIdCache.get(remoteFile);
			
			if (photo != null) {
				return photo;
			}
			else {
				throw new StorageException("Cannot find remote file " + remoteFile);
			}
		}
	}
}
