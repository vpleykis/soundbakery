package com.ice2systems.voice.soundbakery;

import java.io.BufferedInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;

import org.apache.http.client.ClientProtocolException;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

public class ResourceProvider {
  private static String bucketName = "<voice bucket here>";
  String descriptorName = "voice.ini";
	private AmazonS3 s3;
	
	private final String workingDir;
	private final String title;
	private final String descriptorPath;
	
	public ResourceProvider(final String workingDir, final String title) {	
		BasicAWSCredentials awsCreds = new BasicAWSCredentials("<access key>", "<secret here>");
		s3 = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(awsCreds)).build();  

		
		if( workingDir == null || workingDir == "") {
			throw new IllegalArgumentException("working directory path not provided");
		}
		
		if( Files.notExists(Paths.get(workingDir)) ) {
			throw new IllegalArgumentException("working directory does not exist");
		}

		if( title == null || title == "") {
			throw new IllegalArgumentException("title not provided");
		}
		
		this.workingDir = workingDir;
		this.title = title;
		descriptorPath = workingDir + "/" + title + "/" + descriptorName;
		
		if( !Files.exists(Paths.get(descriptorPath), LinkOption.NOFOLLOW_LINKS)) {
			try {
				Files.createDirectories(Paths.get(workingDir + "/" + title));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		
	}

	public boolean downloadDescriptor() throws ClientProtocolException, IOException {
		return downloadObject(ResourceType.descriptor, descriptorName);
	}
	
	public boolean downloadMedia(final String name) throws ClientProtocolException, IOException {
		return downloadObject(ResourceType.media, name);
	}
	
	private boolean downloadObject(final ResourceType resourceType, final String name) throws IOException {
  	String keyName = null;
		 
  	switch(resourceType) {
  		case descriptor:
  			keyName = String.format("%s/%s", title, descriptorName);
  			break;
  		case media:
  			keyName = String.format("%s/%s", title, name);
  			break;
  		default:
  			return false;
  	}
  	
    S3Object s3Obj = s3.getObject(new GetObjectRequest(bucketName, keyName));
    final BufferedInputStream inputStream = new BufferedInputStream(s3Obj.getObjectContent());
  			
  	Files.copy(inputStream, Paths.get(workingDir + "/" + keyName));
  	
  	return true;
	}
	
	public Reader getDescriptor() throws IOException {
		if( !Files.exists(Paths.get(descriptorPath), LinkOption.NOFOLLOW_LINKS) ) {
			downloadDescriptor();
		}
		
		return new FileReader(descriptorPath);
	}
	
	public boolean getMedia(final String name) throws IOException {
		if( !Files.exists(Paths.get(workingDir + "/" + title + "/" +name), LinkOption.NOFOLLOW_LINKS) ) {			
			return downloadMedia(name);
		}
		
		return true;
	}	
	
}
