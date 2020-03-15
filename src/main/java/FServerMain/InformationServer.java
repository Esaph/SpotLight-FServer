/*
 *  Copyright (C) Esaph, Julian Auguscik - All Rights Reserved
 *  * Unauthorized copying of this file, via any medium is strictly prohibited
 *  * Proprietary and confidential
 *  * Written by Julian Auguscik <esaph.re@gmail.com>, March  2020
 *
 */

package FServerMain;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import Commands.*;
import Esaph.ServerPolicy;
import org.json.JSONArray;
import org.json.JSONObject;
import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;

import Esaph.LogUtilsEsaph;

public class InformationServer extends Thread
{
	private LogUtilsEsaph logUtilsMain;
	private static final String mainServerLogPath = "/usr/server/Log/FServer/";
	private static final String ServerType = "InformationServer";
	private static final String placeholder = "InformationServer: ";
	private SSLServerSocket serverSocket;
	private static final int port = 1029;
	private final HashMap<String, Integer> connectionMap = new HashMap<String, Integer>();
	private final SQLPool pool = new SQLPool();

	public InformationServer() throws IOException
	{
		logUtilsMain = new LogUtilsEsaph(new File(InformationServer.mainServerLogPath),
				InformationServer.ServerType,
				"127.0.0.1",
				-100);

		Timer timer = new Timer();
		timer.schedule(new ResetDDOSProtection(), 0, 60000);
		this.logUtilsMain.writeLog(InformationServer.placeholder + "Thread pool loaded().");
	}

	private static final String KeystoreFilePath = "/usr/server/ECCMasterKey.jks";
	private static final String TrustStoreFilePath = "/usr/server/servertruststore.jks";
	private static final String KeystorePass = "8db3626e47";
	private static final String TruststorePassword = "842407c248";
	private SSLContext sslContext;

	public void startFServer()
	{
		try {
			this.initSSLKey();
			SSLServerSocketFactory sslServerSocketFactory = this.sslContext.getServerSocketFactory();
			this.serverSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(InformationServer.port);
			this.start();
			this.logUtilsMain.writeLog(InformationServer.placeholder + "server started.");
		} catch (Exception ec) {
			this.logUtilsMain.writeLog(InformationServer.placeholder + "Exception(Starting server): " + ec);
		}
	}

	private void initSSLKey() throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
			FileNotFoundException, IOException, UnrecoverableKeyException, KeyManagementException {
		this.logUtilsMain.writeLog(InformationServer.placeholder + "Setting up SSL-Encryption");
		KeyStore trustStore = KeyStore.getInstance("JKS");
		trustStore.load(new FileInputStream(InformationServer.TrustStoreFilePath),
				InformationServer.TruststorePassword.toCharArray());
		this.logUtilsMain.writeLog(InformationServer.placeholder + "SSL-Encryption TrustStore VALID.");
		KeyStore keystore = KeyStore.getInstance("JKS");
		keystore.load(new FileInputStream(InformationServer.KeystoreFilePath),
				InformationServer.KeystorePass.toCharArray());
		this.logUtilsMain.writeLog(InformationServer.placeholder + "SSL-Encryption Keystore VALID.");
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(keystore, InformationServer.KeystorePass.toCharArray());

		TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(trustStore);

		sslContext = SSLContext.getInstance("TLS");
		TrustManager[] trustManagers = tmf.getTrustManagers();
		sslContext.init(kmf.getKeyManagers(), trustManagers, null);
		this.logUtilsMain.writeLog(InformationServer.placeholder + "SSL-Encryption OK.");
	}

	private class ResetDDOSProtection extends TimerTask {
		public void run() {

			synchronized (connectionMap)
			{
				if (connectionMap.size() != 0) {
					logUtilsMain.writeLog(InformationServer.placeholder + "Clearing IP-HASHMAP");
					connectionMap.clear();
				}
			}
		}
	}

	private static final int MAX_CONN_PER_MINUTE = 25;

	
	private static final ThreadPoolExecutor threadPoolMain = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
            100,
            15,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(100),
            new ThreadPoolExecutor.CallerRunsPolicy());

	@Override
	public void run() {
		while (true) {
			try {
				SSLSocket socket = (SSLSocket) serverSocket.accept();
				if (this.connectionMap.get(socket.getInetAddress().toString()) != null) {
					if (this.connectionMap
							.get(socket.getInetAddress().toString()) >= InformationServer.MAX_CONN_PER_MINUTE) {
						socket.close();
					} else {
						this.connectionMap.put(socket.getInetAddress().toString(),
								this.connectionMap.get(socket.getInetAddress().toString()) + 1);
						this.logUtilsMain.writeLog("Connection: " + socket.getInetAddress());

						InformationServer.threadPoolMain.submit(new RequestHandler(socket));
					}
				}
				else  {
					this.connectionMap.put(socket.getInetAddress().toString(), 1);
					this.logUtilsMain.writeLog("Connection: " + socket.getInetAddress());
					InformationServer.threadPoolMain.submit(new RequestHandler(socket));
				}
			} catch (Exception ec) {
				this.logUtilsMain.writeLog("InforamtionServer(ACCEPT_ERROR): " + ec);
			}
		}
	}

	private static final String cmd_Friends = "FRIENDS";
	private static final String cmd_getProfiInformationsAllData = "PBI";
	private static final String cmd_SynchSocialMediaData = "SSMD";
	private static final String cmdGetAllConversationMessagesLast24Hours = "FGACM";

	private static final String cmdGetLatestConversationMessagesFromUser = "FGAMFU";
	private static final String cmd_GetCurrentFriendShipStatus = "FGCFSS";
	private static final String cmd_SynchAllPostsFromAllFriendsByTime = "FSAPFA";
	private static final String cmd_getUserProfil = "PBGU";
	private static final String cmd_UpdateDescriptionPlopp = "FUDP";

	private static final String queryGetFullProfil = "SELECT * FROM Users WHERE UID=? LIMIT 1";
	private static final String queryGetVornameAndRegion = "SELECT Region, Vorname FROM Users WHERE UID=? LIMIT 1";

	// QUERY FÜR FREUNDE
	private static final String queryUserInformation = "SELECT Description, PB, UID, Benutzername, Vorname, Geburtstag, Region FROM Users WHERE UID=? LIMIT 1";
	private static final String queryUserFriends = "SELECT * FROM Watcher WHERE UID=? AND AD=0 OR FUID=? AND AD=0";

	private static final String queryLookUpUsername = "SELECT Benutzername FROM Users WHERE UID=? LIMIT 1";
	private static final String queryLookUpIfPostSaved = "SELECT * FROM PrivateMomentsSaved WHERE PID=?";
	private static final String queryGetFriendAnfragen = "SELECT * FROM Following WHERE FUID_FOLLOWING=? AND VALID=0";
	private static final String queryGetHashtagsFromPost = "SELECT TAG_NAME FROM TAGS WHERE PID=?";

	public class RequestHandler extends Thread
	{
		private LogUtilsEsaph logUtilsRequest;
		private JSONObject jsonMessage;
		private static final String queryLookUpUID = "SELECT UID FROM Users WHERE Benutzername=?";
		private SSLSocket socket;
		private PrintWriter writer;
		private BufferedReader reader;
		private Connection connection;
		private long ThreadUID;

		private RequestHandler(SSLSocket socket)
		{
			this.socket = socket;
		}

		public PrintWriter getWriter()
		{
			return this.writer;
		}

		public SSLSocket getSocket()
		{
			return this.socket;
		}

		public void returnConnectionToPool()
		{
			this.connection = pool.returnConnectionToPool(this.connection);
		}

		public long getThreadUID()
		{
			return this.ThreadUID;
		}

		public JSONObject getJSONMessage()
		{
			return this.jsonMessage;
		}

		public Connection getCurrentConnectionToSql()
		{
			return this.connection;
		}

		public LogUtilsEsaph getThreadLogUtilsEsaph()
		{
			return this.logUtilsRequest;
		}

		@Override
		public void run()
		{
			try
			{
				this.logUtilsRequest = new LogUtilsEsaph(new File(InformationServer.mainServerLogPath),
						InformationServer.ServerType,
						socket.getInetAddress().getHostAddress(), -1);
				
				this.writer = new PrintWriter(
						new OutputStreamWriter(this.socket.getOutputStream(), StandardCharsets.UTF_8), true);
				this.reader = new BufferedReader(
						new InputStreamReader(this.socket.getInputStream(), StandardCharsets.UTF_8));
				this.socket.setSoTimeout(13000);

				
				this.jsonMessage = new JSONObject(this.readDataCarefully(1800));
				this.getConnectionToSql();
			
				
				if (checkSID())
				{
					this.logUtilsRequest.setUID(this.ThreadUID);
					String anfrage = this.jsonMessage.getString("ISC");
					this.logUtilsRequest.writeLog("ANFRAGE: " + anfrage);

					if (anfrage.equals(InformationServer.cmd_Friends)) // GET ALL FRIENDS.
					{
						this.logUtilsRequest.writeLog("Handling friends");
						try {
							PreparedStatement preparedStatementGetFriends = (PreparedStatement) this.connection
									.prepareStatement(InformationServer.queryUserFriends);
							preparedStatementGetFriends.setLong(1, this.ThreadUID);
							preparedStatementGetFriends.setLong(2, this.ThreadUID);
							ResultSet resultGetFriends = preparedStatementGetFriends.executeQuery();

							JSONArray userFriendsDataMain = new JSONArray();

							while (resultGetFriends.next())
							{
								PreparedStatement userInformation = (PreparedStatement) this.connection
										.prepareStatement(InformationServer.queryUserInformation);
								this.logUtilsRequest.writeLog("Friendship found.");
								if (resultGetFriends.getLong("UID") == this.ThreadUID)
								{
									this.logUtilsRequest.writeLog("UID");
									userInformation.setLong(1, resultGetFriends.getLong("FUID"));
								}
								else {
									this.logUtilsRequest.writeLog("FUID");
									userInformation.setLong(1, resultGetFriends.getLong("UID"));
								}
								
								ResultSet informationResult = userInformation.executeQuery();
								if (informationResult.next())
								{
									JSONObject singleFriend = new JSONObject();
									singleFriend.put("Benutzername", informationResult.getString("Benutzername"));
									singleFriend.put("UID", informationResult.getLong("UID"));
									singleFriend.put("DESCPL", new JSONObject(informationResult.getString("Description")));
									singleFriend.put("Vorname", informationResult.getString("Vorname"));
									singleFriend.put("Geburtstag",
											informationResult.getTimestamp("Geburtstag").getTime());
									singleFriend.put("Region", informationResult.getString("Region"));

									if(resultGetFriends.getShort("WF") == 1)
									{
										singleFriend.put("WS", ServerPolicy.getFriendshipState(
												this.connection,
												this.ThreadUID,
												informationResult.getLong("UID")));
									}
									singleFriend.put("WF", resultGetFriends.getShort("WF"));
									userFriendsDataMain.put(singleFriend);
								}

								informationResult.close();
								userInformation.close();
							}

							preparedStatementGetFriends.close();
							resultGetFriends.close();
							this.writer.println(userFriendsDataMain.toString());
						}
						catch (Exception ec)
						{
							this.logUtilsRequest.writeLog("GET FRIENDS(FATAL ERROR): " + ec);
							this.writer.println("ERROR");
						}
					}
					else if (anfrage.equals(InformationServer.cmdGetAllConversationMessagesLast24Hours))
					{
						new GetAllConversationMessagesLast24Hours(InformationServer.this, this, this.logUtilsRequest).run();
					}
					else if (anfrage.equals(InformationServer.cmdGetLatestConversationMessagesFromUser))
					{
						new SynchDataFromChat24Hours(InformationServer.this, this, this.logUtilsRequest).run();
					}
					// Keine ahnung hier.
					else if (anfrage.equals(InformationServer.cmd_getProfiInformationsAllData)) // Läd alle
																								// profilinformationen
																								// herunter solange es
																								// der nutzer gewährt.
					{
						PreparedStatement preparedStatementGetProfil = (PreparedStatement) this.connection
								.prepareStatement(InformationServer.queryGetFullProfil);
						preparedStatementGetProfil.setLong(1, this.jsonMessage.getLong("FUSRN"));
						ResultSet result = preparedStatementGetProfil.executeQuery();
						if (result.next())
						{
							final short WatchStatus = ServerPolicy.getFriendshipState(this.connection,
									this.ThreadUID,
									result.getLong("UID"));
							if (WatchStatus == ServerPolicy.POLICY_DETAIL_CASE_FRIENDS) // Beide folgen einander, du kannst dir das Profil angucken.
							{
								this.writer.println("1");
								JSONObject singleFriend = new JSONObject();
								singleFriend.put("Benutzername", result.getString("Benutzername"));
								singleFriend.put("Vorname", result.getString("Vorname"));
								singleFriend.put("Geburtstag", result.getTimestamp("Geburtstag").getTime());
								singleFriend.put("Region", result.getString("Region"));
								this.writer.println(singleFriend.toString());
							}
							else
							{
								this.writer.println("0");
							}
						}

						preparedStatementGetProfil.close();
						result.close();
					}

					else if(anfrage.equals(InformationServer.cmd_getUserProfil))
					{
						new GetUserProfil(InformationServer.this, this, this.logUtilsRequest).run();
					}

					else if(anfrage.equals(InformationServer.cmd_UpdateDescriptionPlopp))
					{
						new UpdateProfilDescriptionPlopp(InformationServer.this, this, this.logUtilsRequest).run();
					}

					else if (anfrage.equals(InformationServer.cmd_SynchSocialMediaData))
					{
						PreparedStatement pr = (PreparedStatement) this.connection
								.prepareStatement(InformationServer.queryGetFriendAnfragen);
						pr.setLong(1, this.ThreadUID);

						ResultSet result = pr.executeQuery();
						JSONArray jsonArray = new JSONArray();
						while (result.next()) {
							long anfrager = result.getLong("UID_FOLLOWS");
							long angefragter = result.getLong("FUID_FOLLOWING");
							JSONObject object = new JSONObject();
							if (anfrager == this.ThreadUID) // I BIMS
							{
								PreparedStatement prLookUpPbPID = (PreparedStatement) this.connection.prepareStatement(InformationServer.queryGetVornameAndRegion);
								prLookUpPbPID.setLong(1, angefragter);
								ResultSet resultSetPbLookUp = prLookUpPbPID.executeQuery();
								if(resultSetPbLookUp.next())
								{
									object.put("VORN", resultSetPbLookUp.getString("Vorname"));
									object.put("REG", resultSetPbLookUp.getString("Region"));
								}

								object.put("USRN", this.lookUpUsername(angefragter));
								object.put("UID", angefragter);
								object.put("FST", ServerPolicy.POLICY_DETAIL_CASE_I_SENT_ANFRAGE);
								resultSetPbLookUp.close();
								prLookUpPbPID.close();
							}
							else // DER ANGEFRAGTE
							{
								PreparedStatement prLookUpPbPID = (PreparedStatement) this.connection.prepareStatement(InformationServer.queryGetVornameAndRegion);
								prLookUpPbPID.setLong(1, anfrager);
								ResultSet resultSetPbLookUp = prLookUpPbPID.executeQuery();
								if(resultSetPbLookUp.next())
								{
									object.put("VORN", resultSetPbLookUp.getString("Vorname"));
									object.put("REG", resultSetPbLookUp.getString("Region"));
								}

								object.put("USRN", this.lookUpUsername(anfrager));
								object.put("UID", anfrager);
								object.put("FST", ServerPolicy.POLICY_DETAIL_CASE_I_WAS_ANGEFRAGT);

								resultSetPbLookUp.close();
								prLookUpPbPID.close();
							}

							jsonArray.put(object);
						}
						pr.close();
						result.close();

						this.writer.println(jsonArray.toString());
					}
					else if (anfrage.equals(InformationServer.cmd_GetCurrentFriendShipStatus))
					{
						long fuid = this.jsonMessage.getLong("FUSRN");
						if (fuid > -1)
						{
							this.writer.println(ServerPolicy.getFriendshipState(
									this.connection,
									this.ThreadUID,
									fuid));
						}
					}
					else if(anfrage.equals(InformationServer.cmd_SynchAllPostsFromAllFriendsByTime))
					{
						new SynchAllPostsFromAllUsers(InformationServer.this, this, this.logUtilsRequest).run();
					}
				}
				else
				{
					this.writer.println("0");
					this.writer.println("NAC");
				}

				// VERBINDUNG SCHLIEßEN.

				this.reader.close();
				this.writer.close();
				this.socket.close();
				this.logUtilsRequest.writeLog(InformationServer.placeholder + "Connection closed.");
			}
			catch (Exception ec)
			{
				this.logUtilsRequest.writeLog(InformationServer.placeholder + "Exception: " + ec);
			}
			finally
			{
				this.connection = (Connection) pool.returnConnectionToPool(this.connection);
				if(this.logUtilsRequest != null)
				{
					this.logUtilsRequest.closeFile();
				}
			}
		}

		public String readDataCarefully(int bufferSize) throws Exception {
			String msg = this.reader.readLine();
			if (msg == null || msg.length() > bufferSize) {
				throw new Exception("Exception: msg " + msg + " length: " + msg.length() + ">" + bufferSize);
			}
			return msg;
		}

		private static final String QUERY_CHECK_SESSION = "SELECT SID FROM Sessions WHERE SID=? AND UID=?";

		public boolean checkSession(long UID, String SID) {
			PreparedStatement qSID = null;
			ResultSet result = null;
			try
			{
				this.logUtilsRequest.writeLog("Checking session");
				qSID = (PreparedStatement) this.connection.prepareStatement(RequestHandler.QUERY_CHECK_SESSION);
				qSID.setString(1, SID);
				qSID.setLong(2, UID);
				result = qSID.executeQuery();
				if (result.next())
				{
					this.logUtilsRequest.writeLog("Session: NEXT()");
					return true;
				}
				else {
					this.logUtilsRequest.writeLog("Session: !=NEXT()");
					return false;
				}
			}
			catch (Exception ec)
			{
				this.logUtilsRequest.writeLog("Exception: " + ec);
				return false;
			}
			finally
			{
				try
				{
					if(qSID != null)
					{
						qSID.close();
					}

					if(result != null)
					{
						result.close();
					}
				}
				catch (Exception ec)
				{
				}
			}
		}

		private boolean checkSID()
		{
			try
			{
				long UID = this.jsonMessage.getLong("USRN");

				if (UID > 0)
				{
					if (checkSession(UID, this.jsonMessage.getString("SID")))
					{
						this.logUtilsRequest.writeLog(InformationServer.placeholder + "Session OK.");
						this.ThreadUID = UID;
						return true;
					} else {
						this.logUtilsRequest.writeLog(InformationServer.placeholder + "Session WRONG.");
						return false;
					}
				}
				else {
					this.logUtilsRequest.writeLog(InformationServer.placeholder + " client has passed a null object!");
					return false;
				}

			} catch (Exception ec) {
				this.logUtilsRequest.writeLog(InformationServer.placeholder + "(checkSID): FATAL ERROR");
				return false;
			}

		}

		private void getConnectionToSql() throws InterruptedException, SQLException
		{
			this.connection = (Connection) pool.getConnectionFromPool();
		}

		public String lookUpUsername(long UID) throws SQLException
		{
			PreparedStatement pr = null;
			ResultSet result = null;

			try
			{
				pr = (PreparedStatement) this.connection
						.prepareStatement(InformationServer.queryLookUpUsername);
				pr.setLong(1, UID);
				result = pr.executeQuery();

				String Username = null;

				if (result.next())
				{
					Username = result.getString("Benutzername");
				}

				if(Username == null)
				{
					throw new SQLException("Benutzername nicht gefunden");
				}

				return Username;
			}
			catch (Exception ec)
			{
				return null;
			}
			finally
			{
				if(pr != null)
				{
					pr.close();
				}

				if(result != null)
				{
					result.close();
				}
			}
		}
	}
}
