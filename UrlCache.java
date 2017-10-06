
/**
 *	UrlCache Class
 * 
 *	@author Susant Pant 10153138
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import javax.tools.FileObject;
import java.nio.file.Files;

public class UrlCache {
	public HashMap<String, Long> cache;

	public int contentLength;
	public Long lastModified;

	public String currDir;

    /**
     * Default constructor to initialize data structures used for caching/etc
	 * If the cache already exists then load it. If any errors then throw runtime exception.
	 *
     * @throws IOException if encounters any errors/exceptions
     */
	public UrlCache() throws IOException {
		currDir = System.getProperty("user.dir");
		String cacheLocation = currDir + "/lastModified.cache"; //directory path on which the cache is saved
		File cacheFile = new File(cacheLocation);
		//if the cache doesn't exist, make a new empty one
		//else read the cache file
			//java doesn't like my code for this,
			//thus prompting the note "UrlCache.java uses unchecked or unsafe operations" at compile time
		if (!cacheFile.exists()) {
			cache = new HashMap<String, Long>();
		} else {
			FileInputStream fis = new FileInputStream(cacheLocation);
			ObjectInputStream ois = new ObjectInputStream(fis);

			try {
				cache = (HashMap<String, Long>) ois.readObject();
			} catch (Exception e) {
				System.out.println("Error: " + e.getMessage());
			}

			ois.close();
			fis.close();
		}
	}
	
	/**
     * Downloads the object specified by the parameter url if the local copy is out of date.
	 *
     * @param url	URL of the object to be downloaded. It is a fully qualified URL.
     * @throws IOException if encounters any errors/exceptions
     */
	public void getObject(String url) throws IOException {
		String[] pathData = urlParse(url);
		String host = pathData[0];
		String path = pathData[1];
		String fileName = pathData[2];

		//format the GET request
		String request = "GET /" + path + fileName + " HTTP/1.1\r\n";
		request += "Host: " + host + "\r\n";
		request += "Connection: close\r\n";
		request += "\r\n";

		try {
			//open a TCP connection to the server
			Socket socket = new Socket(host, 80);

			PrintWriter outputStream = new PrintWriter(new DataOutputStream(socket.getOutputStream()));

			//send GET request to server
			outputStream.print(request);
			outputStream.flush();

			//read the header for content length and last modified values
			readHeader(socket);

			//chceck whether the cache already has the data
			//if the cache does not have the path at all, then obviously we do not have the file either
				//load the url and its lastModified value into the HashMap structure
			//else, check if any change has been made to the file at the server
				//if so, download the new file, change the value of lastModified for the url key in the HashMap
				//else, the file is already in the cache. just chill mang
			if (!cache.containsKey(url)) {
				cache.put(url, lastModified);
				downloadFile(socket, pathData);
			} else {
				if (lastModified.longValue() != cache.get(url).longValue()) {
					cache.remove(url);
					cache.put(url, lastModified);

					//delete the old file (not strictly necessary? might be bloaty overhead)
					File file = new File(currDir + "/" + host + "/" + path + "/" + fileName);
					file.delete();
					//process the rest of the inputStream to obtain the object being downloaded
					downloadFile(socket, pathData);
				} else {
					System.out.println("Cache already contains necessary file.");
				}
			}
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}

		//write the data in the cache to the file before exiting the method
		String cacheLocation = currDir + "/lastModified.cache";
		FileOutputStream fos = new FileOutputStream(cacheLocation);
		ObjectOutputStream oos = new ObjectOutputStream(fos);

		oos.writeObject(cache);

		oos.close();
		fos.close();
	}

	/**
	 *	Takes any url and cuts it into 3 pieces: the host, the path, and the fileName.
	 *	The host is the name of the server, the path is every subdirectory within it
	 *	fileName is the name of the Object to be downloaded
	 *
	 *	@param url 	same URL as in getObject, it is the URL that holds the Object to be downloaded
	 *	@return String array that contains the 3 strings host, path, and fileName
	 */
	public String[] urlParse(String url) {
		String[] temp = url.split("/");
		String[] retArray = new String[3];

		int i = 0;
		while (temp[i].equals("") || temp[i].equals("http:") || temp[i].equals("https:"))
			i++;
		String[] temp2 = temp[i].split(":");
		retArray[0] = temp2[0];

		String path = "";
		i++;
		while (i < temp.length - 1) {
			path += temp[i] + "/";
			i++;
		}
		retArray[1] = path;
		retArray[2] = temp[i];

		return retArray;
	}

	/**
	 *	Creates a file in an appropriate directory that holds the data from the server
	 *
	 *	@param pathData	String array that holds the host, path, and fileName from the url. These are reflected in the names of the directory
	 *	@param body	Byte array that holds the data from the server
	 */
	public void createFile(String[] pathData, byte[] body) {
		String host = pathData[0];
		String path = pathData[1];
		String fileName = pathData[2];

		String dirPath = currDir + "/" + host + "/" + path;
		File dir = new File(dirPath);
		dir.mkdirs();

		String filePath = dirPath + "/" + fileName;
		try {
			FileOutputStream downloadedFile = new FileOutputStream(filePath);
			downloadedFile.write(body);
			downloadedFile.close();
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
	}

	/**
	 *	Reads everything after the header into a byte array and pushes it to method createFile
	 *
	 *	@param socket 	the socket that connects to the server to read inputStream
	 *	@param pathData	String array that holds the host, path, and fileName from the url. Needed to call creatFile
	 */
	public void downloadFile(Socket socket, String[] pathData) {
		String host = pathData[0];
		String path = pathData[1];
		String fileName = pathData[2];

		try {
			//create a byte array of exactly Content-Length (found using readHeader method)
			byte[] body = new byte[contentLength];
			int counter = 0;
			//while the byte array has not been filled completely, keep filling it
			//since we know that the size of the data should fit perfectly inside the byte array
			while (counter < contentLength) {
				socket.getInputStream().read(body, counter, 1);
				counter++;
			}
			//take the data in the byte array and create a file that holds it at pathData
			createFile(pathData, body);
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
	}

	/**
	 *	Parses through header lines, specifically for Content-Length and Last-Modified which are necessary.
	 *
	 *	@param socket 	the socket that connects to the server to read inputStream
	 */
	public void readHeader(Socket socket) {
		String header = "";
		byte[] headerBytes = new byte[1024];
		int counter = 0;

		try {
			//"\n\r\n" is only found at the end of the header
			//so as soon as it is part of the header string, we know we are at the end of the header
			while (!header.contains("\n\r\n")) {
				socket.getInputStream().read(headerBytes, counter, 1);
				header += (char) headerBytes[counter++];
			}
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
		//System.out.println(header); //DEBUG

		//finding Last-Modified
		String[] temp = header.split("Last-Modified: ");
		temp = temp[1].split("\r\n", 2);
		lastModified = new Long(turnLastModifiedToMillis(temp[0]));
		
		//finding Content-Length
		temp = temp[1].split("Content-Length: ");
		temp = temp[1].split("\r\n");
		contentLength = Integer.parseInt(temp[0]);
	}

    /**
     * Returns the Last-Modified time associated with the object specified by the parameter url.
	 *
     * @param url 	URL of the object 
	 * @return the Last-Modified time in millisecond as in Date.getTime()
     */
	public long getLastModified(String url) {
		//just pull it from the cache
		Long millis = cache.get(url);
		return millis.longValue();
	}

	public long turnLastModifiedToMillis(String last_Modified) {
		SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz");
		Date date = format.parse(last_Modified, new ParsePosition(0));
		return date.getTime();
	}
}
