
/**
 * UrlCache Class
 * 
 *
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
		String cacheLocation = currDir + "/lastModified.cache";
		File cacheFile = new File(cacheLocation);
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

		/*format the GET request*/
		String request = "GET /" + path + fileName + " HTTP/1.1\r\n";
		request += "Host: " + host + "\r\n";
		request += "Connection: close\r\n";
		request += "\r\n";

		try {
			/*open a TCP connection to the server*/
			Socket socket = new Socket(host, 80);

			PrintWriter outputStream = new PrintWriter(new DataOutputStream(socket.getOutputStream()));

			/*send GET request to server*/
			outputStream.print(request);
			outputStream.flush();

			readHeader(socket);

			if (!cache.containsKey(url)) {
				cache.put(url, lastModified);
				downloadFile(socket, pathData);
			} else {
				if (lastModified.longValue() != cache.get(url).longValue()) {
					cache.remove(url);
					cache.put(url, lastModified);

					File file = new File(currDir + "/" + host + "/" + path + "/" + fileName);
					file.delete();
					downloadFile(socket, pathData);
				} else {
					System.out.println("Cache already contains necessary file.");
				}
			}
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}

		String cacheLocation = currDir + "/lastModified.cache";
		FileOutputStream fos = new FileOutputStream(cacheLocation);
		ObjectOutputStream oos = new ObjectOutputStream(fos);

		oos.writeObject(cache);

		oos.close();
		fos.close();
	}

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

	public void downloadFile(Socket socket, String[] pathData) {
		String host = pathData[0];
		String path = pathData[1];
		String fileName = pathData[2];

		try {
			byte[] body = new byte[contentLength];
			int counter = 0;
			while (counter < contentLength) {
				socket.getInputStream().read(body, counter, 1);
				counter++;
			}

			createFile(pathData, body);
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
	}

	public void readHeader(Socket socket) {
		String header = "";
		byte[] headerBytes = new byte[1024];
		int counter = 0;

		try {
			while (!header.contains("\n\r\n")) {
				socket.getInputStream().read(headerBytes, counter, 1);
				header += (char) headerBytes[counter++];
			}
		} catch (Exception e) {
			System.out.println("Error: " + e.getMessage());
		}
		//System.out.println(header);

		String[] temp = header.split("Last-Modified: ");
		temp = temp[1].split("\r\n", 2);
		lastModified = new Long(turnLastModifiedToMillis(temp[0]));
		
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
		Long millis = cache.get(url);
		return millis.longValue();
	}

	public long turnLastModifiedToMillis(String last_Modified) {
		SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz");
		Date date = format.parse(last_Modified, new ParsePosition(0));
		return date.getTime();
	}
}
