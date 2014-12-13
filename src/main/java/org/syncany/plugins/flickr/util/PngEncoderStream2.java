package org.syncany.plugins.flickr.util;

/*
 * PngEncoder, encodes any byte array, stream or file into a 24-bit bitmap (BMP).
 * Copyright (C) 2013 Philipp C. Heckel <philipp.heckel@gmail.com> 
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

/**
 * Encodes any byte array, stream or file into a 24-bit bitmap (BMP).
 * 
 * The <code>encodeToPng()</code>-methods can be used to transform any binary data 
 * into a rectangular image and stored in the widely supported 24-bit bitmap
 * format. The method does not hide, encrypt or compress the source data in any
 * way. It merely prepends a bitmap header and transforms the payload as 
 * specified by the bitmap file format.
 * 
 * <p>The <code>decodeFromBitmap()</code>-methods retrieve the orginal payload from a 
 * previously encoded bitmap. A bitmap not encoded with this class cannot be read.
 * 
 * <p><b>Example:</b>
 * 
 * <pre>
 * // Encode file "/etc/hosts" into file "/tmp/hosts.bmp", and restore it to "/tmp/hosts-restored"
 * PngEncoder.encodeToPng(new File("/etc/hosts"), new File("/tmp/hosts.bmp"));
 * PngEncoder.decodeFromBitmap(new File("/tmp/hosts.bmp"), new File("/tmp/hosts-restored"));
 * 
 * // Encode 3 bytes into a bitmap file
 * PngEncoder.encodeToPng(new byte[] { 0x01, 0x02, 0x03 }, new File("/tmp/3-bytes.bmp"));
 * byte[] threebytes = PngEncoder.decodeFromBitmap(new File("/tmp/3-bytes.bmp"));
 * </pre>
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 * @see http://blog.philippheckel.com/2013/03/02/java-encode-any-byte-array-stream-or-file-into-a-24-bit-bitmap-bmp/
 */
public class PngEncoderStream2 {        
	private static final byte[] PNG_CHUNK_IHDR_TYPE                        = new byte[] { 0x49, 0x48, 0x44, 0x52 };
	private static final int    PNG_CHUNK_IHDR_OFFSET_DATA_TYPE            = 4;
	private static final int    PNG_CHUNK_IHDR_SIZE_DATA_AND_TYPE          = 17;
	
	private static final int    PNG_CHUNK_IHDR_OFFSET_IMAGE_WIDTH          = 8;
    private static final int    PNG_CHUNK_IHDR_OFFSET_IMAGE_HEIGHT         = 12;
    private static final int    PNG_CHUNK_IHDR_OFFSET_CRC_CHECKSUM         = 21;

	private static final int    PNG_CHUNK_IHDR_SIZE_IMAGE_WIDTH            = 4; 
	private static final int    PNG_CHUNK_IHDR_SIZE_IMAGE_HEIGHT           = 4;
	private static final int    PNG_CHUNK_IHDR_SIZE_BIT_DEPTH              = 1; 
	private static final int    PNG_CHUNK_IHDR_SIZE_COLOR_TYPE             = 1; 
	private static final int    PNG_CHUNK_IHDR_SIZE_COMPRESSION_METHOD     = 1; 
	private static final int    PNG_CHUNK_IHDR_SIZE_FILTER_METHOD          = 1; 
	private static final int    PNG_CHUNK_IHDR_SIZE_INTERLACE_METHOD       = 1; 
	private static final int    PNG_CHUNK_IHDR_SIZE_CRC_CHECKSUM           = 4; 

	private static final byte[] PNG_CHUNK_TEXT_TYPE                        = new byte[] { 0x74, 0x45, 0x58, 0x74 };
	private static final int    PNG_CHUNK_TEXT_OFFSET_DATA_TYPE            = 4;
	private static final int    PNG_CHUNK_TEXT_OFFSET_MAGIC_IDENTIFIER     = 8;
	private static final int    PNG_CHUNK_TEXT_SIZE_DATA_AND_TYPE          = 16;  
	private static final int    PNG_CHUNK_TEXT_SIZE_MAGIC_IDENTIFIER       = 4;
	private static final int    PNG_CHUNK_TEXT_SIZE_PAYLOAD                = 4;	
	
    private static final int    PNG_CHUNK_TEXT_OFFSET_PAYLOAD_LENGTH       = 12;
    private static final int    PNG_CHUNK_TEXT_OFFSET_CRC_CHECKSUM         = 16;
    private static final int    PNG_CHUNK_TEXT_SIZE_CRC_CHECKSUM           = 4;       
    
    private static final byte[] PNG_CHUNK_IDAT_TYPE                        = new byte[] { 0x49, 0x44, 0x41, 0x54 };
    private static final byte   PNG_CHUNK_IDAT_BEGIN_FILTER_METHOD         = 0x00;  
    private static final int    PNG_CHUNK_IDAT_BEGIN_OFFSET_DATA_SIZE      = 0;
    private static final int    PNG_CHUNK_IDAT_END_OFFSET_CRC_CHECKSUM     = 0;
    private static final int    PNG_CHUNK_IDAT_BEGIN_SIZE_FILTER_METHOD    = 1;
    private static final int    PNG_CHUNK_IDAT_MAX_SIZE                    = 512*1024;  
    
    private static final byte   UDEF                                       = 0x00;   // Undefined value in PNG header, to be overwritten by methods    
    		            
    private static final byte[] PNG_SIGNATURE = new byte[] {        
        /* 00 */ (byte) 0x89, 0x50, 0x4e, 0x47,                                    // PNG magic, signature
        /* 04 */ 0x0d, 0x0a, 0x1a, 0x0a,             
    };
        
    private static final byte[] PNG_CHUNK_IHDR = new byte[] {        
        /* 00 */ 0x00, 0x00, 0x00, 0x0d,                                            // Chunk size, always 0x0d = 13
        /* 04 */ 0x49, 0x48, 0x44, 0x52,                                            // Chunk type "IHDR"
        /* 08 */ UDEF, UDEF, UDEF, UDEF,                                            // Image width
        /* 12 */ UDEF, UDEF, UDEF, UDEF,                                            // Image height
        /* 16 */ 0x08,                                                              // Bit depth (here: 8 bits, for color type true color)
        /* 17 */ 0x02,                                                              // Color type (here: 2 / true color)
        /* 18 */ 0x00,                                                              // Compression method (here: 0, deflate/inflate)
        /* 19 */ 0x00,                                                              // Filter method (here: adaptive filtering with five basic filter type)
        /* 20 */ 0x00,                                                              // Interlace method (here: 0, no interlace)
        /* 21 */ UDEF, UDEF, UDEF, UDEF                                             // CRC of IHDR chunk data        
    };      
    
    private static final byte[] PNG_CHUNK_SRGB = new byte[] {        
        /* 00 */ 0x00, 0x00, 0x00, 0x01,                                            // Chunk size, always 01       
        /* 04 */ 0x73, 0x52, 0x47, 0x42,                                            // Chunk type "sRGB"
        /* 08 */ 0x00,                                                              // RGB mode, here: 0
        /* 09 */ (byte) 0xae, (byte) 0xce, 0x1c, (byte) 0xe9                        // CRC of sRGB chunk data         
    };    
    
    private static final byte[] PNG_CHUNK_TEXT = new byte[] {        
        /* 00 */ 0x00, 0x00, 0x00, 0x08,                                            // Chunk size, here: 8
        /* 04 */ 0x74, 0x45, 0x58, 0x74,                                            // Chunk type "tEXt"
        /* 08 */ UDEF, UDEF, UDEF, UDEF,                                            // Magic identifier (!)
        /* 12 */ UDEF, UDEF, UDEF, UDEF,                                            // Payload length (!)
        /* 16 */ UDEF, UDEF, UDEF, UDEF                                             // CRC of tEXt chunk data        
    };  
    
    private static final byte[] PNG_CHUNK_TEXT_MAGIC_IDENTIFIER = new byte[] {        
        /* 00 */ 0x4e, 0x33, 0x52, 0x44                                             // Magic identifier "N3RD"
    };      

    private static final byte[] PNG_CHUNK_IDAT_BEGIN = new byte[] {        
        /* 00 */ UDEF, UDEF, UDEF, UDEF,                                            // Chunk size, unsigned int
        /* 04 */ 0x49, 0x44, 0x41, 0x54,                                            // Chunk type "IDAT"
    };  
    
    private static final byte[] PNG_CHUNK_IDAT_END = new byte[] {        
        UDEF, UDEF, UDEF, UDEF,                                                     // CRC of IDAT chunk data        
    };      
    
    private static final byte[] PNG_CHUNK_IEND = new byte[] {        
        0x00, 0x00, 0x00, 0x00,                                                     // Chunk size, always 0        
        0x49, 0x45, 0x4e, 0x44,                                                     // Chunk type "IEND"
        (byte) 0xae, 0x42, 0x60, (byte) 0x82                                        // CRC of IEND chunk data 
    };
	
    
    /**
     * Encodes any <code>File</code> into a 24-bit bitmap (BMP) and outputs the image
     * to another <code>File</code>.
     * 
     * <p>The method does not hide, encrypt or compress the source data in any
     * way. It merely prepends a bitmap header and transforms the payload as 
     * specified by the bitmap file format.
	 * 
     * @param srcFile File to be read pointing to the input data (payload)
     * @param destFile File to write the 24-bit bitmap file to
     * @throws IOException Thrown if the input/output stream cannot be read/written
     */     
    public static void encodeToPng(File srcFile, File destFile) throws IOException {
    	encodeToPng(new FileInputStream(srcFile), (int) srcFile.length(), new FileOutputStream(destFile));
    }
    
    /**
     * Encodes any <code>File</code> into a 24-bit bitmap (BMP) and outputs the image
     * to another <code>File</code>.
     * 
     * <p>The method does not hide, encrypt or compress the source data in any
     * way. It merely prepends a bitmap header and transforms the payload as 
     * specified by the bitmap file format.
	 * 
     * @param srcBytes Byte array of input data (payload)
     * @param destStream Stream to write the 24-bit bitmap file to
     * @throws IOException Thrown if the input/output stream cannot be read/written
     */    
    public static void encodeToPng(byte[] srcBytes, File destFile) throws IOException {
    	encodeToPng(new ByteArrayInputStream(srcBytes), srcBytes.length, new FileOutputStream(destFile));
    }
    
    public static List<File> encodeToPngs(File srcFile, File destFolder, int maxChunkSize, String namePattern) throws IOException {
    	List<File> destFiles = new ArrayList<File>();
    	
    	int fileCount = 0;
    	byte[] buffer = new byte[maxChunkSize];
    	
    	FileInputStream srcStream = new FileInputStream(srcFile);
    	
    	int len;
    	while ((len = srcStream.read(buffer)) != -1) {
    		File partPngFile = new File(destFolder+File.separator+String.format(namePattern, fileCount));
    		encodeToPng(new ByteArrayInputStream(buffer), len, new FileOutputStream(partPngFile));
    		
    		destFiles.add(partPngFile);
    		fileCount++;
    	}    	
    	
    	return destFiles;
    }       
    
    /**
     * Encodes a byte array into a 24-bit bitmap (BMP) and outputs the image
     * to an <code>OutputStream</code>.
     * 
     * <p>The method does not hide, encrypt or compress the source data in any
     * way. It merely prepends a bitmap header and transforms the payload as 
     * specified by the bitmap file format.
	 * 
     * @param srcBytes Byte array of input data (payload)
     * @param destStream Stream to write the 24-bit bitmap file to
     * @throws IOException Thrown if the input/output stream cannot be read/written
     */
    public static void encodeToPng(byte[] srcBytes, OutputStream destStream) throws IOException {
    	encodeToPng(new ByteArrayInputStream(srcBytes), srcBytes.length, destStream);
    }
    
    /**
     * Encodes an <code>InputStream</code> into a 24-bit bitmap (BMP) and outputs the image
     * as byte array.
	 *
     * <p>The method does not hide, encrypt or compress the source data in any
     * way. It merely prepends a bitmap header and transforms the payload as 
     * specified by the bitmap file format.
	 * 
     * @param srcStream Stream of input data (payload)
     * @param srcStreamLength Length of the input data stream (in bytes)
     * @param destStream Stream to write the 24-bit bitmap file to
     * @throws IOException Thrown if the input/output stream cannot be read/written
     */
    public static void encodeToPng(InputStream srcStream, int srcStreamLength, OutputStream destStream) throws IOException {	
        if (srcStreamLength > Integer.MAX_VALUE) {
            throw new IOException("File too big; max. "+Integer.MAX_VALUE+" bytes supported.");
        }        

        // Write PNG signature
        destStream.write(PNG_SIGNATURE, 0, PNG_SIGNATURE.length);
        
        // Write IHDR chunk
        int ihdrImageWidth = (int) Math.ceil(Math.sqrt((double) srcStreamLength / 3));  // Image width, sqrt(payload/3), divided by 3 because of RGB
        int ihdrImageHeight = (int) Math.ceil((double) srcStreamLength                  // Image height, payload / image width / 3
        		/ (double) ihdrImageWidth / 3);
        
        byte[] ihdrChunk = PNG_CHUNK_IHDR.clone();                                  // Clone bitmap header template, and overwrite with fields

        writeIntBE(ihdrChunk, PNG_CHUNK_IHDR_OFFSET_IMAGE_WIDTH, ihdrImageWidth);
        writeIntBE(ihdrChunk, PNG_CHUNK_IHDR_OFFSET_IMAGE_HEIGHT, ihdrImageHeight);
        
        int ihdrChecksum = calculateChunkChecksum(ihdrChunk, PNG_CHUNK_IHDR_OFFSET_DATA_TYPE, PNG_CHUNK_IHDR_SIZE_DATA_AND_TYPE); 
        writeIntBE(ihdrChunk, PNG_CHUNK_IHDR_OFFSET_CRC_CHECKSUM, ihdrChecksum);
        
        destStream.write(ihdrChunk, 0, ihdrChunk.length);
        
        // Write sRGB chunk
        destStream.write(PNG_CHUNK_SRGB);        
        
        // Write tEXt chunk (with magic ID and hidden data size)
        byte[] textChunk = PNG_CHUNK_TEXT.clone();
        
        System.arraycopy(PNG_CHUNK_TEXT_MAGIC_IDENTIFIER, 0, textChunk, PNG_CHUNK_TEXT_OFFSET_MAGIC_IDENTIFIER, PNG_CHUNK_TEXT_SIZE_MAGIC_IDENTIFIER);
        writeIntBE(textChunk, PNG_CHUNK_TEXT_OFFSET_PAYLOAD_LENGTH, srcStreamLength);
        
        int textChecksum = calculateChunkChecksum(textChunk, PNG_CHUNK_TEXT_OFFSET_DATA_TYPE, PNG_CHUNK_TEXT_SIZE_DATA_AND_TYPE); 
        writeIntBE(textChunk, PNG_CHUNK_TEXT_OFFSET_CRC_CHECKSUM, textChecksum);
        
        destStream.write(textChunk, 0, textChunk.length);
        
        
        // Create IDAT chunks (multiple!)
        Deflater deflater = new Deflater();
        deflater.setStrategy(Deflater.FILTERED);
        deflater.setLevel(1);       
        
        IdatChunkEncoderOutputStream pngStream = new IdatChunkEncoderOutputStream(destStream);
        DeflaterOutputStream deflaterDestStream = new DeflaterOutputStream(pngStream, deflater);
                     
        byte[] row = new byte[ihdrImageWidth*3];
        int numberOfLines = ihdrImageHeight;
        int numberOfBytesRead = -1;        
        
        for (int i=0; i<numberOfLines; i++) {
        	numberOfBytesRead = srcStream.read(row);
        	
        	if (numberOfBytesRead == -1) {
        		throw new IOException("Unable to read input stream data.");        		
        	}
        	
        	deflaterDestStream.write(PNG_CHUNK_IDAT_BEGIN_FILTER_METHOD);
        	deflaterDestStream.write(row, 0, numberOfBytesRead);
        }
        
        int numberOfPaddingBytesForLastRow = row.length - numberOfBytesRead;
        deflaterDestStream.write(new byte[numberOfPaddingBytesForLastRow]);        
        
        deflaterDestStream.finish();
        deflaterDestStream.flush();
        
        pngStream.close(); // write last IDAT
                
        // Create IEND chunk
        destStream.write(PNG_CHUNK_IEND);

        srcStream.close();
        destStream.close();
    }       
    
    public static String toHex(byte[] bytes) {
        BigInteger bi = new BigInteger(1, bytes);
        return String.format("%0" + (bytes.length << 1) + "x", bi);
    }
    
    private static int calculateChunkChecksum(byte[] chunk, int offset, int length) {
    	CRC32 crc32 = new CRC32();
    	crc32.update(chunk, offset, length);
    	
		return (int) crc32.getValue();
	}
    
    private static class IdatChunkEncoderOutputStream extends OutputStream {
    	private CRC32 chunkChecksum;
    	private ByteArrayOutputStream chunkCompressedBytesBuffer;    	
    	private OutputStream outputStream;
    	
    	public IdatChunkEncoderOutputStream(OutputStream out) {
    		this.chunkChecksum = new CRC32();
    		this.outputStream = out;
    		this.chunkCompressedBytesBuffer = new ByteArrayOutputStream();    		    		 
    	}
    	    	
    	@Override
    	public void write(byte[] b, int off, int len) throws IOException {
    		if (chunkCompressedBytesBuffer.size() == 0) {    			
        		chunkChecksum.update(PNG_CHUNK_IDAT_TYPE);
    		}
    		
    		chunkChecksum.update(b, off, len);
    		chunkCompressedBytesBuffer.write(b, off, len);
    		
    		if (chunkCompressedBytesBuffer.size() > PNG_CHUNK_IDAT_MAX_SIZE) {
    			writeChunk();
    		}
    	}
    	
    	@Override
    	public void close() throws IOException {
    		writeChunk();    		
    	}
    	
    	private void writeChunk() throws IOException {
			byte[] idatChunkBegin = PNG_CHUNK_IDAT_BEGIN.clone(); 		        
			int idatChunkSize = chunkCompressedBytesBuffer.size();
	        writeIntBE(idatChunkBegin, PNG_CHUNK_IDAT_BEGIN_OFFSET_DATA_SIZE, idatChunkSize);
	        
	        byte[] idatChunkEnd = PNG_CHUNK_IDAT_END.clone();       
	        int idatChunkChecksum = (int) chunkChecksum.getValue();
	        writeIntBE(idatChunkEnd, PNG_CHUNK_IDAT_END_OFFSET_CRC_CHECKSUM, idatChunkChecksum);
	        
	        outputStream.write(idatChunkBegin);
			outputStream.write(chunkCompressedBytesBuffer.toByteArray());
			outputStream.write(idatChunkEnd);
			
			chunkChecksum.reset();
			chunkCompressedBytesBuffer.reset();    		
    	}    	
    	    	
    	@Override
    	public void write(byte[] b) throws IOException {
    		write(b, 0, b.length);
    	}

    	@Override
		public void write(int b) throws IOException {
			throw new IOException("Not implemented.");
		}    	
    }
    
	/**
     * Decodes a 24-bit bitmap previously encoded by this class from 
     * an <code>InputStream</code> to a byte array.
     * 
     * <p>The method can only read bitmaps that were encoded using one of the 
     * <code>encodeToPng()</code> methods. It will throw an exception if
     * any other bitmaps are read.
     * 
     * @param srcStream Stream of input data (24-bit bitmap)
     * @return The original data read from the bitmap (payload)
     * @throws IOException Thrown if the input/output stream cannot be read/written
     */
    public static byte[] decodeFromBitmap(InputStream srcStream) throws IOException {
    	ByteArrayOutputStream destStream = new ByteArrayOutputStream();
    	decodeFromPng(srcStream, destStream, true);
    	
    	return destStream.toByteArray();
    }

    /**
     * Decodes a 24-bit bitmap previously encoded by this class from 
     * a <code>File</code> to a byte array.
     * 
     * <p>The method can only read bitmaps that were encoded using one of the 
     * <code>encodeToPng()</code> methods. It will throw an exception if
     * any other bitmaps are read.
     * 
     * @param srcFile File to be read pointing to the input data (24-bit bitmap)
     * @return The original data read from the bitmap (payload)
     * @throws IOException Thrown if the input/output stream cannot be read/written
     */
    public static byte[] decodeFromPng(File srcFile) throws IOException {
    	ByteArrayOutputStream destStream = new ByteArrayOutputStream();
    	decodeFromPng(new FileInputStream(srcFile), destStream, true);
    	
    	return destStream.toByteArray();    	
    }
    
    /**
     * Decodes a 24-bit bitmap previously encoded by this class from 
     * a <code>File</code> to another <code>File</code>.
     * 
     * <p>The method can only read bitmaps that were encoded using one of the 
     * <code>encodeToPng()</code> methods. It will throw an exception if
     * any other bitmaps are read.
     * 
     * @param srcFile File to be read pointing to the input data (24-bit bitmap)
     * @param destFile File to be written the original data to (payload)  
     * @throws IOException Thrown if the input/output stream cannot be read/written
     */    
    public static void decodeFromPng(File srcFile, File destFile) throws IOException {
    	decodeFromPng(new FileInputStream(srcFile), new FileOutputStream(destFile), true);
    }
    
    public static void decodeFromPngNEWNOTFINISHED(InputStream srcStream, OutputStream destStream, boolean closeDestStream) throws IOException {
    	// READ HEADER
    	Chunk chunk = null;

    	int imageWidth = -1;
    	int payloadLength = -1;
    	
    	// Skip over PNG signature
    	srcStream.skip(PNG_SIGNATURE.length);
  
    	while (null != (chunk = readChunk(srcStream))) {    	
    		// Chunk "IHDR"
    		if (Arrays.equals(chunk.type, PNG_CHUNK_IHDR_TYPE)) {
    			imageWidth = toIntBE(chunk.data, PNG_CHUNK_IHDR_OFFSET_IMAGE_WIDTH);
    		}
    		
    		// Chunk "tEXT"
    		else if (Arrays.equals(chunk.type, PNG_CHUNK_TEXT_TYPE)) {    			
    			byte[] magicIdentifierBytes = Arrays.copyOfRange(chunk.data, PNG_CHUNK_TEXT_OFFSET_MAGIC_IDENTIFIER, PNG_CHUNK_TEXT_SIZE_MAGIC_IDENTIFIER);
    			
    			if (!Arrays.equals(magicIdentifierBytes, PNG_CHUNK_TEXT_MAGIC_IDENTIFIER)) {
    	        	throw new IOException("Magic identifier in tEXt chunk not valid. Maybe PNG file was not encoded with this class.");
    	        }
    			
    			payloadLength = toIntBE(chunk.data, PNG_CHUNK_TEXT_OFFSET_PAYLOAD_LENGTH);
    		}
    		
    		// Chunk "IDAT"
    		else if (Arrays.equals(chunk.type, PNG_CHUNK_IDAT_TYPE)) {
    			
    		}
    	}
    }
    
    
    private class IdatChunkEncoderInputStream extends InputStream {

		@Override
		public int read() throws IOException {
			// TODO Auto-generated method stub
			return 0;
		}
    	
    }
    
    /**
     * Decodes a 24-bit bitmap previously encoded by this class from 
     * an <code>InputStream</code> to an <code>OutputStream</code>.
     * 
     * <p>The method can only read bitmaps that were encoded using one of the 
     * <code>encodeToPng()</code> methods. It will throw an exception if
     * any other bitmaps are read.
     * 
     * @param srcStream Stream to be read pointing to the input data (24-bit bitmap)
     * @param destStream Stream to be written the original data to (payload)  
     * @throws IOException Thrown if the input/output stream cannot be read/written
     */        
    public static void decodeFromPng(InputStream srcStream, OutputStream destStream, boolean closeDestStream) throws IOException {
        // READ HEADER
    	long bytesRead = 0;
        
    	// Skip over PNG signature
    	bytesRead += srcStream.skip(PNG_SIGNATURE.length);
    	
    	// Read/skip IHDR ('image width' field)
    	bytesRead += srcStream.skip(PNG_CHUNK_IHDR_OFFSET_IMAGE_WIDTH);
    	
        byte[] imageWidthBytes = new byte[PNG_CHUNK_IHDR_SIZE_IMAGE_WIDTH];                    
        bytesRead += srcStream.read(imageWidthBytes);
        int imageWidth = toIntBE(imageWidthBytes);    
        
        bytesRead += srcStream.skip(
        	  PNG_CHUNK_IHDR_SIZE_IMAGE_HEIGHT
        	+ PNG_CHUNK_IHDR_SIZE_BIT_DEPTH
        	+ PNG_CHUNK_IHDR_SIZE_COLOR_TYPE
        	+ PNG_CHUNK_IHDR_SIZE_COMPRESSION_METHOD
        	+ PNG_CHUNK_IHDR_SIZE_FILTER_METHOD
        	+ PNG_CHUNK_IHDR_SIZE_INTERLACE_METHOD
        	+ PNG_CHUNK_IHDR_SIZE_CRC_CHECKSUM);
        
        // Read/skip sRGB
        bytesRead += srcStream.skip(PNG_CHUNK_SRGB.length);
        
        // Read/skip tEXt ('payload length' field) 
        bytesRead += srcStream.skip(PNG_CHUNK_TEXT_OFFSET_MAGIC_IDENTIFIER);
        byte[] magicIdentifierBytes = new byte[PNG_CHUNK_TEXT_SIZE_MAGIC_IDENTIFIER];
        bytesRead += srcStream.read(magicIdentifierBytes);
        //System.out.println("magic id = "+toHex(magicIdentifierBytes));
        
        if (!Arrays.equals(magicIdentifierBytes, PNG_CHUNK_TEXT_MAGIC_IDENTIFIER)) {
        	throw new IOException("Magic identifier in tEXt chunk not valid. Maybe PNG file was not encoded with this class.");
        }
        
        byte[] payloadLengthBytes = new byte[PNG_CHUNK_TEXT_SIZE_PAYLOAD];
        bytesRead += srcStream.read(payloadLengthBytes);
        int payloadLength = toIntBE(payloadLengthBytes);
        
        //System.out.println("payload = "+payloadLength);
        bytesRead += srcStream.skip(PNG_CHUNK_TEXT_SIZE_CRC_CHECKSUM);
        
        ByteArrayOutputStream compressedPayloadStream = new ByteArrayOutputStream();
        
        Chunk nextChunk = readChunk(srcStream);
        
        while (Arrays.equals(PNG_CHUNK_IDAT_TYPE, nextChunk.type)) {
    		compressedPayloadStream.write(nextChunk.data, 0, nextChunk.size);        
        	nextChunk = readChunk(srcStream);
        }
        
        compressedPayloadStream.close();
        
        ByteArrayOutputStream filteredPayloadStream = new ByteArrayOutputStream();
        InflaterOutputStream inflaterStream = new InflaterOutputStream(filteredPayloadStream);
        
        inflaterStream.write(compressedPayloadStream.toByteArray());    // FIXME: Full memory dump, stream based processing needed

        inflaterStream.close();
        filteredPayloadStream.close();
        
        byte[] filteredPayload = filteredPayloadStream.toByteArray();  // FIXME: Full memory dump, stream based processing needed
        int maxRowLength = imageWidth*3;
        
    	for (int filteredPayloadOffset=0, payloadRead=0; payloadRead<payloadLength; ) {
    		filteredPayloadOffset += PNG_CHUNK_IDAT_BEGIN_SIZE_FILTER_METHOD; 

    		int rowLength = (payloadRead+maxRowLength < payloadLength) ? maxRowLength : payloadLength - payloadRead;
    		destStream.write(filteredPayload, filteredPayloadOffset, rowLength);
    		
    		payloadRead += rowLength;
    		filteredPayloadOffset += rowLength;
    	}
        
        srcStream.close();
        
        if (closeDestStream) {
        	destStream.close();	
        }
    }
    
    private static Chunk readChunk(InputStream srcStream) throws IOException {
    	 byte[] chunkSizeBytes = new byte[4];                    
         srcStream.read(chunkSizeBytes);
         int chunkSize = toIntBE(chunkSizeBytes);
         
    	 byte[] chunkType = new byte[4];                    
         srcStream.read(chunkType);         
         
         byte[] chunkData = new byte[chunkSize];
         srcStream.read(chunkData);
         
         byte[] chunkChecksumBytes = new byte[4];
         srcStream.read(chunkChecksumBytes);
         int chunkChecksum = toIntBE(chunkChecksumBytes);
         
         return new Chunk(chunkSize, chunkType, chunkData, chunkChecksum);
    }
    
    private static class Chunk {
    	private int size;
    	private byte[] type;
    	private byte[] data;
    	private int checksum;
    	
		public Chunk(int size, byte[] type, byte[] data, int checksum) {
			this.size = size;
			this.type = type;
			this.data = data;
			this.checksum = checksum;
		}    	    	
    }
    
    /**
     *  Write an integer to a byte array (as little endian) at a
     *  specific offset. 
     */
    private static void writeIntBE(byte[] bytes, int startoffset, int value) {
        bytes[startoffset] = (byte)(value >>> 24);
        bytes[startoffset+1] = (byte)(value >>> 16);
        bytes[startoffset+2] = (byte)(value >>> 8);
        bytes[startoffset+3] = (byte)(value);
    }
    
    /**
     * Read an integer value from a 4-byte array (as little endian).
     */
    private static int toIntBE(byte[] value, int offset) {
        return ((value[offset+0] & 0xff) << 24) |
            ((value[offset+1] & 0xff) << 16) |
            ((value[offset+2] & 0xff) << 8) |
            (value[offset+3] & 0xff);
    }  
    
    private static int toIntBE(byte[] value) {
    	return toIntBE(value, 0);
    }
    
    /**
     * Encode/decode a bitmap from the command line.
     * 
     * <p><b>Syntax:</b><br />
     * <tt>PngEncoder encode SRCFILE DESTOFILE.bmp<br />
     * <tt>PngEncoder decode SRCFILE.bmp DESTOFILE
     */
    public static void main(String[] args) throws IOException  {
    	if (args.length == 3 && "encode".equals(args[0])) {
    		PngEncoderStream2.encodeToPng(new File(args[1]), new File(args[2]));
    	}
    	else if (args.length == 3 && "decode".equals(args[0])) {
    		PngEncoderStream2.decodeFromPng(new File(args[1]), new File(args[2]));
    	}
    	else {
    		System.out.println("Usage: PngEncoder encode SRCFILE DESTOFILE.png");
    		System.out.println("       PngEncoder decode SRCFILE.png DESTOFILE");
    	}
    }
}