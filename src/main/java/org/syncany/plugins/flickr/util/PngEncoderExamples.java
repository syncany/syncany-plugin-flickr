package org.syncany.plugins.flickr.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class PngEncoderExamples {
    
    public static void main(String[] args) throws IOException, DataFormatException {
    	// Encode 3 bytes into a bitmap file
    	PngEncoder.encodeToPng(new byte[] { (byte) 0xff, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, 0x00, 0x00, (byte) 0xff, (byte) 0xff, 0x00, 0x00 }, new File("/tmp/12-bytes.png"));
    	byte[] redblueredblue = PngEncoder.decodeFromPng(new File("/tmp/12-bytes.png"));
    	
    	System.out.println("EXAMPLE 1");
    	System.out.println("----");
    	System.out.println("12 bytes = "+new BigInteger(1, redblueredblue).toString(16));
    	System.out.println("----");
    	System.out.println("");
    	
    	
    	// Encode file "/etc/vmlinuz" (about 4 MB) to "/tmp/vmlinuz.png" and restore it to "/tmp/vmlinuz-restored"
    	PngEncoder.encodeToPng(new File("/tmp/syncany-notifier.app.zip"), new File("/tmp/vmlinuz.png"));
    	PngEncoder.decodeFromPng(new File("/tmp/vmlinuz.png"), new File("/tmp/vmlinuz-restored"));
    	
    	System.out.println("EXAMPLE 2");
    	System.out.println("----");
    	System.out.println("/vmlinuz              "+new File("/tmp/syncany-notifier.app.zip").length()+" bytes");
    	System.out.println("/tmp/vmlinuz.png      "+new File("/tmp/vmlinuz.png").length()+" bytes");
    	System.out.println("/tmp/vmlinuz-restored "+new File("/tmp/vmlinuz-restored").length()+" bytes");
    	System.out.println("----");
    	System.out.println("");
    	
    	
    	// Encode file "/etc/motd" into file "/tmp/motd.png" and restore it to memory
    	PngEncoder.encodeToPng(new File("/etc/hostname"), new File("/tmp/hostname.png"));
    	String motdFileContent = new String(PngEncoder.decodeFromPng(new File("/tmp/hostname.png")));
    	
    	System.out.println("EXAMPLE 3");
    	System.out.println("----");
    	System.out.println("/etc/hostname              "+new File("/etc/hostname").length()+" bytes");
    	System.out.println("/tmp/hostname.png          "+new File("/tmp/hostname.png").length()+" bytes");
    	System.out.println("");
    	System.out.println("Restored Contents:");
    	System.out.println(motdFileContent);
    	System.out.println("----");
    	System.out.println("");
    	
    	
    	// Encode file "/etc/motd" into file "/tmp/motd.png.gz", and gzip before writing
    	// Then restore and print it
    	PngEncoder.encodeToPng(
    			new FileInputStream(new File("/etc/hosts")), (int) new File("/etc/hosts").length(),
    			new GZIPOutputStream(new FileOutputStream("/tmp/hosts.png.gz")));
    	
    	String hostsFileContent = 
    			new String(PngEncoder.decodeFromBitmap(
    					new GZIPInputStream(new FileInputStream(new File("/tmp/hosts.png.gz")))));
    	
    	System.out.println("EXAMPLE 4");
    	System.out.println("----");    	
    	System.out.println("/etc/hosts              "+new File("/etc/hosts").length()+" bytes");
    	System.out.println("/tmp/hosts.png.gz       "+new File("/tmp/hosts.png.gz").length()+" bytes");
    	System.out.println("");
    	System.out.println("Restored Contents:");
    	System.out.println(hostsFileContent);
    	System.out.println("----");
    	System.out.println("");
    	
        
    }
}