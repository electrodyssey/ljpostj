package space.cyberworlds.ljpostj;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcCommonsTransportFactory;
import org.apache.xmlrpc.common.XmlRpcHttpRequestConfigImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;




public class LjApiClient
{
	public final static String URL_XML_RPC = "http://www.livejournal.com/interface/xmlrpc";
	public final static String CLIENT_VER = "java/jlpostj v1.0";
	
	public final static int TIMEOUT_CONNECT = 2000;
	public final static int TIMEOUT_REPLY = 1000;
	
	public final static int SECURITY_LEVEL_PUBLIC = 0;
	public final static int SECURITY_LEVEL_FRIENDS = 1;
	public final static int SECURITY_LEVEL_PRIVATE = 2;
	
	
	
	private final Logger logger_ = LoggerFactory.getLogger(LjApiClient.class);

	
	private static XmlRpcClientConfigImpl configClient_;
	private static XmlRpcHttpRequestConfigImpl configRequest_;
	
	private static String urlXmlRpc_ = URL_XML_RPC;
	
	private static XmlRpcClient client_;
	
	private String authChallengeServerSupplied_ = ""; 
	private String authClientResponse_ = "";
	
	private String user_ = "";
	private String password_ = "";
	
	

	public LjApiClient (String user, String password) throws MalformedURLException
	{
		user_ = user;
		password_ = password;
		

		configClient_ = new XmlRpcClientConfigImpl ();
		configClient_.setServerURL(new URL(urlXmlRpc_));

		
		configRequest_ = new XmlRpcHttpRequestConfigImpl ();
		configRequest_.setConnectionTimeout(TIMEOUT_CONNECT);
		configRequest_.setReplyTimeout(TIMEOUT_REPLY);
		
		client_ = new XmlRpcClient();
		
		client_.setTransportFactory(new XmlRpcCommonsTransportFactory(client_));
		
		client_.setConfig(configClient_);
	}
	

	
	/**
	 * Function obtains a challenge string from LJ server
	 * @return challenge string or null if error occured.
	 * @throws XmlRpcException 
	 */
	public String getAuthChallengeString() throws XmlRpcException
	{
		String res = null;
		
		HashMap <Object, Object>  hmres = (HashMap <Object, Object>) client_.execute("LJ.XMLRPC.getchallenge", new LinkedList<String>());
		
		
		for (Map.Entry <Object, Object> entry : hmres.entrySet())
		{
			logger_.debug(entry.getKey().toString() + " : " + entry.getValue().toString());

			if (entry.getKey().toString().equals("challenge"))
			{
				res = entry.getValue().toString();
				logger_.debug("received challenge from the server: " + res);
				
				authChallengeServerSupplied_ = res;
			}
		}

		
		return res;
	}
	

	/**
	 * Obtains a hash map with a challenge info from the LJ server 
	 * @return HashMap <Object, Object> containing an auth challenge reply
	 * @throws XmlRpcException
	 */
	public HashMap <Object, Object> getAuthChallengeMap() throws XmlRpcException
	{
		return (HashMap <Object, Object>) client_.execute("LJ.XMLRPC.getchallenge", new LinkedList<String>());
	}
	
	
	
	
	/**
	 * Creates an authentication challenge reply 
	 * @param password user password
	 * @param challenge challenge string obtained from the LJ server using a getchallenge method
	 * @return challenge response string
	 */
	private String createAuthResponse_ (String password, String challenge)
	{
		String hpass = DigestUtils.md5Hex(password);
		
		String constr = challenge + hpass;
		String authResponse = DigestUtils.md5Hex(constr);
		
		return authResponse;
	}
	

	/**
	 * Updates the internal fields authChallengeServerSupplied_ and authClientResponse_ with a 'fresh' challenge 
	 * @param challengeMap server reply obtained with a getAuthChallengeMap() function
	 * @return
	 */
	private boolean updateAuthChallengeInfo_ (HashMap <Object, Object> challengeMap)
	{
		boolean res = false;
		
		for (Map.Entry <Object, Object> entry : challengeMap.entrySet())
		{

			if (entry.getKey().toString().equals("challenge"))
			{
				authChallengeServerSupplied_ = entry.getValue().toString();

				logger_.debug("server supplied challenge: " + authChallengeServerSupplied_);
				
				authClientResponse_ = createAuthResponse_(password_, authChallengeServerSupplied_);
				
				logger_.debug("new auth challenge response: " + authClientResponse_);
				
				res = true;
			}
		}

		return res;
	}
	
	
	/**
	 * 
	 * @return
	 */
	private boolean authenticate ()
	{
		boolean res = false;
		
		try
		{
			HashMap <Object, Object> challengeMap = getAuthChallengeMap();
			
			if (updateAuthChallengeInfo_(challengeMap) )
			{
				res = true;
			}
			
		} catch (XmlRpcException e)
		{
			logger_.error("Can't obtain a challenge string from the server. Error: " + e.getMessage());			
			e.printStackTrace();
		}
		
		return res;
	}
	
	
	
	
	/**
	 * Performs a  login
	 * @param user
	 * @param password
	 * @return HashMap with a servers response
	 * @throws XmlRpcException 
	 */

	public HashMap <Object, Object> login () throws XmlRpcException
	{
		HashMap <Object, Object> resMap = null;
		HashMap <Object, Object> paramMap = new HashMap <Object, Object>();
		
		
		if (authenticate())
		{

			paramMap.put(new String("mode"), new String("login"));
			paramMap.put(new String("username"), new String (user_));
			paramMap.put(new String("auth_method"), new String ("challenge"));
			paramMap.put(new String("password"), new String (""));
			paramMap.put(new String("auth_challenge"), new String (authChallengeServerSupplied_));
			paramMap.put(new String("auth_response"), new String (authClientResponse_));
			paramMap.put(new String("ver"), new String ("1"));
			paramMap.put(new String("clientversion"), new String (CLIENT_VER));

			try
			{

				resMap = (HashMap <Object, Object>) client_.execute("LJ.XMLRPC.login", new Object [] {paramMap});
				
				if (null != resMap)
				{
					for (Map.Entry <Object, Object> entry : resMap.entrySet())
					{
						logger_.debug("login response: " + entry.getKey().toString() + " : " + entry.getValue().toString());
					}
				}
				else
				{
					logger_.error("null response from login rpc call");
				}

			} catch (Exception e)
			{
				logger_.error("Login failed. Can't authenticate to the LJ server. Error: " + e.getMessage());
				resMap = null;
			}

		}
		else
		{
			logger_.error("Login authentication failed.");
			resMap = null;
		}
		

			return resMap;
	}
	
	
	

	/**
	 * 
	 * @param subject
	 * @param text
	 * @param linefeedings
	 * @param secLevel valid values are SECURITY_LEVEL_PUBLIC, SECURITY_LEVEL_PRIVATE, SECURITY_LEVEL_FRIENDS 
	 * @param year
	 * @param mon
	 * @param day
	 * @param hr
	 * @param min
	 * @param optProps optional property list, could be null
	 * @return
	 * @throws XmlRpcException
	 */
	public HashMap <Object, Object> postevent (String subject, String text, String linefeedings,
			int secLevel,
			int year, int mon, int day, 
			int hr, int min,
			HashMap <Object, Object> optProps) throws XmlRpcException
	{
		HashMap <Object, Object> resMap = null;
		HashMap <Object, Object> paramMap = new HashMap <Object, Object>();
		

		switch (secLevel) 
		{
		case SECURITY_LEVEL_PRIVATE:
			logger_.debug("security level set to PRIVATE");
			paramMap.put(new String("security"), new String ("private"));
			break;
			
		case SECURITY_LEVEL_PUBLIC:
			logger_.debug("security level set to PUBLIC");
			paramMap.put(new String("security"), new String ("public"));
			break;
			
		case SECURITY_LEVEL_FRIENDS:
			logger_.debug("security level set to FRIENDS_ONLY");
			paramMap.put(new String("security"), new String ("usemask"));
			paramMap.put(new String("allowmask"), new Integer (1));
			break;
			
		default:
			paramMap.put(new String("security"), new String ("private"));
			logger_.debug("security level " + secLevel + " is unknown forcing PRIVATE");
		}
		
		
		if (null != optProps)
		{
			paramMap.put(new String ("props"), new HashMap<Object, Object> (optProps));
			logger_.debug("appending options");
		}
		
				
		if (authenticate())
		{

			paramMap.put(new String("username"), new String (user_));
			paramMap.put(new String("auth_method"), new String ("challenge"));
			paramMap.put(new String("auth_challenge"), new String (authChallengeServerSupplied_));
			paramMap.put(new String("auth_response"), new String (authClientResponse_));
			paramMap.put(new String("ver"), new String ("1"));
			paramMap.put(new String("event"), new String(text));
			paramMap.put(new String("linefeedings"), new String(linefeedings));
			paramMap.put(new String("subject"), new String (subject));

			paramMap.put(new String("year"), new Integer (year));
			paramMap.put(new String("mon"), new Integer (mon));
			paramMap.put(new String("day"), new Integer (day));

			paramMap.put(new String("hour"), new Integer (hr));
			paramMap.put(new String("min"), new Integer (min));

			paramMap.put(new String("usejournal"), new String (user_));
			
			if (null != optProps)
			{
				paramMap.put(new String("props"), optProps);
			}

			try
			{
				resMap = (HashMap <Object, Object>) client_.execute("LJ.XMLRPC.postevent", new Object [] {paramMap});
				
				if (null != resMap)
				{
					for (Map.Entry <Object, Object> entry : resMap.entrySet())
					{
						logger_.debug("postevent response: " + entry.getKey().toString() + " : " + entry.getValue().toString());
					}
				}
				else
				{
					logger_.error("null response from postevent rpc call");
				}

			} catch (Exception e)
			{
				logger_.error("Postevent failed. Can't post to the LJ server. Error: " + e.getMessage());
				resMap = null;
			}

		}
		else
		{
			logger_.error("Authentication failed. Can't post.");
			resMap = null;
		}

		
		return resMap;
	}
	


}
