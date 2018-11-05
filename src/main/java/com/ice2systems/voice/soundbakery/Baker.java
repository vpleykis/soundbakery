package com.ice2systems.voice.soundbakery;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.client.ClientProtocolException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Baker 
{
		final ResourceProvider provider;
		final MultiMediaHandler mmHandler;
		
		public Baker(final String workingDir, final String title) {
			provider = new ResourceProvider(workingDir, title);
			mmHandler = new MultiMediaHandler(workingDir, title);
		}
	
		private double timeToSeconds(final JSONObject time) {
			long h = (Long)time.get("h");
			long m = (Long)time.get("m");
			long s = (Long)time.get("s");
			long ms = (Long)time.get("ms");		
			
			return h*3600.0 + m*60.0 + s + ms/1000.0;
		}
		
		private String addSilence(double time) {
			String fileName = mmHandler.makeSilence(time);
			return fileName;
		}
		
		private String secondsToTime(double sec) {
			long iPart = (long) sec;
			long h = iPart/3600;
			long m = (iPart - h*3600)/60;
			long s = iPart - h*3600 - m*60;
			long ms = (long) ((sec - iPart)*1000);
			
			return String.format("%02d:%02d:%02d,%03d,", h, m, s, ms );
		}

		private void bake(final List<String> descr) throws IOException {
			
			final List<String> batch = new LinkedList<String>();
			int i = 1;
			
			for(String fileName: descr) {
				if(i%100000 == 0) {
					mmHandler.joinMedia(batch);
					batch.clear();
				}
				else {
					batch.add(fileName);
				}
				i++;
			}
			
			mmHandler.joinMedia(batch);
		}

		private void printOutDescriptor(final List<String> descr) throws IOException {
			/*
			System.out.println();
			System.out.println("---------- Descriptor ----------");
			for(String entry: descr) {
				System.out.println(entry);
			}
			System.out.println("------- End of Descriptor -------\n");		
			*/
			Path file = Paths.get("/tmp/descriptor.txt");
			Files.write(file, descr, Charset.forName("UTF-8"));
		}
		
		private List<String> createMediaDescritor(JSONObject jsonContent) throws IOException {
			List<String> descriptor = new LinkedList<String>();
			
			JSONArray array = (JSONArray)jsonContent.get("content");
			double correction = 0, start = 0, end = 0;
					
			for(int i=0;i<array.size();i++) {
				System.out.println(String.format("%d/%d", i+1, array.size()));
				
				JSONObject item = (JSONObject)array.get(i);
				JSONArray voices = (JSONArray)item.get("voices");
				
				start = timeToSeconds((JSONObject)item.get("startTS"));
				
				// if it's not the first title
				// generate silence in between
				// otherwise record correction
				if( i == 0 ) {
					correction = start;
				}
				else {
					double silentBreakLength = start - correction - end;// previous end
					
					if(silentBreakLength > 0) {
						descriptor.add(addSilence(silentBreakLength));
					}
					System.out.println(String.format("silentBreakLength=%.3f", silentBreakLength));						
				}
				
				end = timeToSeconds((JSONObject)item.get("endTS"));
				
				start -= correction;
				end -= correction;
				
				double fragmentLength = end - start;
				double itemsLength = 0;
				
				// calculate total length
				for(int j=0;j<voices.size();j++) {
					itemsLength += mmHandler.getLength((String)voices.get(j));
				}
				
				double pauseLength = ( voices.size() > 1 ) ? (fragmentLength - itemsLength)/(voices.size()-1) : 0;
				
				//limit pause length
				if(pauseLength > 1) {
					pauseLength = 1;
				}
				//real end
				end = start + itemsLength + pauseLength*(voices.size()-1);
				
				System.out.println(String.format("fragmentLength=%.3f itemsLength=%.3f pauseLength=%.3f", fragmentLength, itemsLength, pauseLength));	
				// to match with SRT
				System.out.println(String.format("%s --> %s", secondsToTime(start + correction), secondsToTime(end + correction)));
				//populate descriptor
				for(int j=0;j<voices.size();j++) {
					if(j>0) {
						if(pauseLength > 0) {
							descriptor.add(addSilence(pauseLength));
						}
					}
					descriptor.add((String)voices.get(j));
				}
			}
			
			return descriptor;
		}
		
		@SuppressWarnings("unchecked")
		private void downloadMedia(JSONObject jsonContent) throws ClientProtocolException, IOException {
			JSONArray array = (JSONArray)jsonContent.get("content");
	
			for(int i=0;i<array.size();i++) {
				System.out.println(String.format("%d/%d", i+1, array.size()));
				JSONObject item = (JSONObject)array.get(i);
				JSONArray voices = (JSONArray)item.get("voices");
				
				for(int j=0;j<voices.size();j++) {
					String name = (String)voices.get(j);
					voices.set(j, name.replaceAll("\\.pcm", "\\.wav"));// replace name with wav extension
					System.out.println(String.format("downloading %s", name));
					provider.getMedia(name);
					mmHandler.pcm2Wav(name);
				}
			}
		}
		
    public static void main( String[] args )
    {
			if( args.length < 2) {
				 System.out.println("Usage: <working directory for caching> <title name>");
				 System.exit(-1);
			}
   	 
			Baker baker = new Baker(args[0], args[1]);
			
			try {   		
				JSONParser parser = new JSONParser();
			
				Object content = parser.parse(baker.provider.getDescriptor());
			 
				JSONObject jsonContent = (JSONObject)content;
			
				System.out.println("Start Download...");
				
				baker.downloadMedia(jsonContent);
				
				System.out.println("Start pre-baking...");
				
				List<String> descr = baker.createMediaDescritor(jsonContent);
			  
			  System.out.println("Done pre-baking");
			  
			  baker.printOutDescriptor(descr);
			  
				System.out.println("Start baking...");
				
				baker.bake(descr);
				
			  System.out.println("Done baking");
				
			} catch (Exception e) {
				e.printStackTrace();
			}
    }
}
