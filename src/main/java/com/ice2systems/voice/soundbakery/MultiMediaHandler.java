package com.ice2systems.voice.soundbakery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class MultiMediaHandler {
  enum op {
	  add, length, silence
  }

  enum expernalProcessor {
	  sox, ffmpeg
  }
  
	private final String workingDir;
	private final String title;
	private final String mp3fileName;
	private final String logFile;
	private final String tempFile;
	
	private double currentLength = 0;
	
	private final static String silenceFileFormat = "silence%.3f.wav";
	private final static String silenceCommandFormat =  "sox -n -r 16000 -c 1 %s trim 0.0 %.3f";//"ffmpeg -f lavfi -i anullsrc=channel_layout=mono:sample_rate=22050 -t %.3f %s";
	private final static String lengthCommandFormatFFMpeg = "sh getLength.sh %s %s";//"ffmpeg -i %s -f null -";
	private final static String lengthCommandFormatSox = "sh getSoxLength.sh %s %s";//"sox %s -n stat > %s 2>&1";
	private final static String joinCommandFormat = "sh joinMP3.sh %s %s %s %s";//"ffmpeg -i %s -i %s -filter_complex \"[0:0] [1:0] concat=n=2:v=0:a=1\" %s";
	private final static String joinCommandFormatSox = "sh joinSOX-MP3.sh %s %s";// 1st parameter will be comprised of number of files, 2nd is output
	private final static String pcm2WavCommand = "sh pcm2wav.sh %s %s";
	
	public MultiMediaHandler(String workingDir, String title) {
		this.workingDir = workingDir;
		this.title = title;
		mp3fileName = workingDir + "/" + title + "/" + title + ".wav";
		logFile = workingDir + "/" + title + "/" + "media.log";
		tempFile = workingDir + "/" + title + "/" + "temp.wav";
		try {
			Files.deleteIfExists(Paths.get(mp3fileName));
			Files.deleteIfExists(Paths.get(tempFile));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
	}

	private String executeCommand(String command) throws IOException, InterruptedException {

		System.out.println("Executing: " + command);	
		StringBuffer output = new StringBuffer();

		Process p;

		p = Runtime.getRuntime().exec(command);
		p.waitFor();

		BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

    String line = "";			
    
		while ((line = reader.readLine())!= null) {
			output.append(line + "\n");
		}

		return output.toString();
	}	
	
	public String pcm2Wav(final String name) {
		String shortName = name.split("\\.pcm")[0];
		
		String pathIn = workingDir + "/" + title + "/" + name;
		String pathOut = workingDir + "/" + title + "/" + shortName + ".wav";
		
		try {
			if( !Files.exists(Paths.get(pathOut), LinkOption.NOFOLLOW_LINKS)) {
				executeCommand(String.format(pcm2WavCommand, pathIn, pathOut));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
		return shortName;
	}
	
	public String makeSilence(double time) {
		String fileName = String.format(silenceFileFormat, time);
		String path = workingDir + "/" + title + "/" + fileName;
		
		try {
			if( !Files.exists(Paths.get(path), LinkOption.NOFOLLOW_LINKS)) {
				executeCommand(String.format(silenceCommandFormat, path, time));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
		return fileName;
	}
	
	private double parseFFMpeg(String[] list) {
		double length = 0;
		
		for(String line: list) {
			line = line.replaceAll("\\r$", "");
			if( line.contains("time=") && line.contains("size=")) {				
				int ndx = line.lastIndexOf("time=");
				
				String time = line.substring( ndx + 5, ndx + 17);
				
				if(time != null && time != "") {
					double fraction = 0;
					double hour = 0;
					double minute = 0;					
					double second = 0;
					
					String timeBase = null;
					
					if(time.contains(".")) {
						timeBase = time.split("\\.")[0];
						fraction = Double.parseDouble(time.split("\\.")[1]);
					}
					else {
						timeBase = time;
					}
					String[] timeParts = timeBase.split(":");
					
					if(timeParts.length != 3) {
						throw new RuntimeException("Length can't be calculated: time format problem 2");
					}

					hour = Double.parseDouble(timeParts[0]);
					minute = Double.parseDouble(timeParts[1]);	
					second = Double.parseDouble(timeParts[2]);						
					
					
					length = hour*3600 + minute*60 + second + fraction/100;
					break;
				}
				else {
					throw new RuntimeException("Length can't be calculated: time format problem 1");
				}
			}
		}		
		
		return length;
	}

	private double parseSox(String[] list) {
		double length = 0;
		
		for(String line: list) {
			if( line.startsWith("Length (seconds)") ) {
				String[] parts = line.split(":");

				if(parts.length == 2) {
					length = Math.round(Double.parseDouble(parts[1].trim())*1000)/1000.0;
					break;
				}
				else {
					throw new RuntimeException("Length can't be calculated");
				}
			}
		}		
		
		return length;
	}

	// seconds
	public double getLengthFromFFMpeg() {
		return getLengthFromCommand(String.format(lengthCommandFormatFFMpeg, logFile, mp3fileName ), expernalProcessor.ffmpeg);
	}
	
	// seconds
	public double getLengthFromSox() {
		return getLengthFromCommand(String.format(lengthCommandFormatSox, mp3fileName, logFile ), expernalProcessor.sox);
	}
	
	// seconds
	private double getLengthFromCommand(final String command, final expernalProcessor exProc) {
		try {
			Files.deleteIfExists(Paths.get(logFile));
			
			executeCommand(command);
		
			String output = new String ( Files.readAllBytes( Paths.get(logFile) ) );

			double length = 0;

			if( output != null ) {
				//System.out.println("parsing output..." );
				String[] list = output.split("\n");

				switch(exProc) {
					case ffmpeg:
						length = parseFFMpeg(list);
						break;
					case sox:					
						length = parseSox(list);
						break;
				}
			}
			return length;
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
	public double getLength(String fileName) {
		String path = workingDir + "/" + title + "/" + fileName;
		
		return getLengthFromCommand(String.format(lengthCommandFormatSox, path, logFile ), expernalProcessor.sox);
	}
	
	public void addMedia(String fileName) {
		String path = workingDir + "/" + title + "/" + fileName;
		
		try {
			if( !Files.exists(Paths.get(mp3fileName), LinkOption.NOFOLLOW_LINKS)) {
				Files.copy(Paths.get(path), Paths.get(mp3fileName), LinkOption.NOFOLLOW_LINKS);
			}
			else {
				currentLength = getLengthFromCommand(String.format(joinCommandFormat, logFile, mp3fileName, path, tempFile), expernalProcessor.ffmpeg);
				Files.copy(Paths.get(tempFile), Paths.get(mp3fileName), StandardCopyOption.REPLACE_EXISTING );
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}		
	}
	
	public void joinMedia(final List<String> batch) {
		if(batch.size() > 0) {
			StringBuffer buff = new StringBuffer();
			try {				
				// make temp 1st input if it's not the 1st batch
				if( Files.exists(Paths.get(mp3fileName), LinkOption.NOFOLLOW_LINKS)) {
					Files.copy(Paths.get(mp3fileName), Paths.get(tempFile), StandardCopyOption.REPLACE_EXISTING);
					Files.deleteIfExists(Paths.get(mp3fileName));
					buff.append(tempFile);
				}
				
				for(final String fileName: batch) {
					buff.append(" ");// break in between					
					buff.append(workingDir);
					buff.append("/");
					buff.append(title);
					buff.append("/");
					buff.append(fileName);
				}
			
				executeCommand(String.format(joinCommandFormatSox, buff.toString(), mp3fileName));
			} catch (IOException e) {
				throw new RuntimeException(e);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	public double getCurrentLength() {
		return currentLength;
	}
	
  public static void main( String[] args )
  {
  	 if( args.length < 3) {
  		 System.out.println("Usage: <working directory for caching> <title name> <operation: add, length, silence> <argument>");
  		 System.exit(-1);
  	 }
  	 
  	 MultiMediaHandler handler = new MultiMediaHandler(args[0], args[1]);
  	 

  	 switch(op.valueOf(args[2])) {
  	 	case add:
				handler.addMedia(args[3]);
  	 		break;
  	 	case length:
  	 		System.out.println(handler.getLengthFromSox());
  	 		break; 
  	 	case silence:
  	 		System.out.println(handler.makeSilence(Double.parseDouble(args[3])));
  	 		break;  	 		
  	 }
  }
}
