
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

public class UrlCache {
	public String lastModified;
	public int contentLength;

    /**
     * Default constructor to initialize data structures used for caching/etc
	 * If the cache already exists then load it. If any errors then throw runtime exception.
	 *
     * @throws IOException if encounters any errors/exceptions
     */
	public UrlCache() throws IOException {
		
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
		String[] temp = header.split("Last-Modified: ");
		temp = temp[1].split("\r\n", 2);
		lastModified = temp[0];

		temp = temp[1].split("Content-Length: ");
		temp = temp[1].split("\r\n");
		contentLength = Integer.parseInt(temp[0]);
	}

	public void createFile(String[] pathData, byte[] body) {
		String host = pathData[0];
		String path = pathData[1];
		String fileName = pathData[2];

		String dirPath = System.getProperty("user.dir") + "/" + host + "/" + path;
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
     * Returns the Last-Modified time associated with the object specified by the parameter url.
	 *
     * @param url 	URL of the object 
	 * @return the Last-Modified time in millisecond as in Date.getTime()
     */
	public long getLastModified(String url) {
		long millis = 0;
		SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz");
		Date date = format.parse(lastModified, new ParsePosition(0));
		millis = date.getTime();
		return millis;
	}

}
