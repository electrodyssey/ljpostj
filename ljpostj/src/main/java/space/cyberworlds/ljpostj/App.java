package space.cyberworlds.ljpostj;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 *
 */
public class App 
{
	
	private static final Logger logger_ = LoggerFactory.getLogger(App.class);
	
	static LjApiClient lac;
	

	static final String SUBJ_TOKEN = "SUBJECT:";
	
	static final String ACTION_POST_STR = "post";

	static final int ACTION_UNKNOWN = 0;
	static final int ACTION_POST = 1;
	
	private static String msg = ""; 
	
	
	
	
	public static int getAction (String s)
	{
		int res = ACTION_UNKNOWN;

		if (ACTION_POST_STR.equalsIgnoreCase(ACTION_POST_STR))
		{
			res = ACTION_POST;
		}
		
		return res;
	}
	
	
	public static int getSecLevel(String s)
	{
		int res = LjApiClient.SECURITY_LEVEL_PRIVATE;
		
		if (s.equalsIgnoreCase("public"))
		{
			res = LjApiClient.SECURITY_LEVEL_PUBLIC;
			msg = "sec level PUBLIC";
			logger_.info(msg);
			System.out.println(msg);
			
		} else if (s.equalsIgnoreCase("private"))
		{
			res = LjApiClient.SECURITY_LEVEL_PRIVATE;
			msg = "sec level PRIVATE";
			logger_.info(msg);
			System.out.println(msg);

		} else if (s.equalsIgnoreCase("friends"))
		{
			res = LjApiClient.SECURITY_LEVEL_FRIENDS;
			msg = "sec level FRIENDS";
			logger_.info(msg);
			System.out.println(msg);
		}
		else
		{
			msg = "Unknown sec level " + s + " forcing PRIVATE";
			logger_.info(msg);
			res = LjApiClient.SECURITY_LEVEL_PRIVATE;
			System.out.println(msg);
			
		}
		
		return res;
	}
	
	
	/**
	 * Moves file from 'incoming' to 'processed' dir
	 * @param dirIn directory with *.txt files
	 * @param fileName short file name (no slashes)
	 * @return returns true if file was moved successfully or false otherwise
	 */
	public static boolean moveFile (String dirIn, String fileName)
	{
		boolean res = false;
		
		String dirOut = dirIn + "/processed";
		
		File file = new File(dirOut);
		if (!file.exists())
		{
			if (file.mkdir())
			{
				msg = "Directory for processed files is created!";
				System.out.println(msg);
				logger_.info(msg);
			} else
			{
				System.out.println("Failed to create  a directory for processed files");
				System.out.println(msg);
				logger_.error(msg);
			}
		}
		
		try
		{

			File afile = new File(dirIn + "/" + fileName);

			if (afile.renameTo(new File(dirOut + "/" + afile.getName())))
			{				
				msg =  "File was successfully moved: " + fileName;
				System.out.println(msg);
				logger_.info(msg);


				res = true;
			} else
			{
				msg =  "File is failed to move: " + fileName;
				System.out.println(msg);
				logger_.error(msg);

				res = false;
			}

		} catch (Exception e)
		{
			e.printStackTrace();

			msg = "Error moving file :" + fileName;
			System.out.println(msg);
			logger_.error(msg);
			res = false;
		}
		
		return res;
	}
	
	/**
	 * Lists all of the *.txt files in dir directory
	 * @param dir directory with *.txt files
	 * @return ArrayList with a short file names
	 */
	public static ArrayList listTxtFiles (String dir)
	{
		File f = new File(dir);
		ArrayList<String> names = new ArrayList<String>(
				Arrays.asList(
						f.list(
								new FilenameFilter() 
								{
									@Override
									public boolean accept(File dir, String name) 
									{
										if(name.lastIndexOf('.')>0)
										{
											// get last index for '.' char
											int lastIndex = name.lastIndexOf('.');
											
											// get extension
											String str = name.substring(lastIndex);
											
											// match path name extension
											if(str.equals(".txt"))
											{
												return true;
											}
										}
										return false;
									}								
								}
								
								)));
		
		return names;
	}
	
	
	
	
	
	
	/**
	 * Uploads file to the server
	 * @param dir
	 * @param fileName
	 * @param secLevel
	 * @param backdated
	 * @return returns false if file upload failed, true otherwise
	 * @throws IOException
	 * @throws XmlRpcException
	 */
	public static boolean processFile (String dir, String fileName, int secLevel, boolean backdated) throws IOException, XmlRpcException
	{
		boolean res = false;
		
		
		FileInputStream fstream = new FileInputStream(dir + "/" + fileName);
		BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
		
		String strLine;
		
		String strDate = br.readLine();
		
		String [] date =  strDate.split(" ");
		
		System.out.println(date[0] + " " +  date [1] + " " + date[2]);

		String subj = "";
		String text = "";
		
		String s = br.readLine();
		if (0 == s.indexOf(SUBJ_TOKEN))
		{
			subj = s.substring(SUBJ_TOKEN.length());
		}
		else
		{
			text += s + "\n";
		}
		
		while ((strLine = br.readLine()) != null)
		{
			text += strLine + "\n";
		}
		
		br.close();
		
		
		
		HashMap <Object, Object> options = null;
		
		if (backdated)
		{
			logger_.debug("backdated entry, creating options");
			options = new HashMap <Object, Object> ();
			options.put(new String ("opt_backdated"), new Boolean(true));
		}
		

		HashMap <Object, Object> resMap = lac.postevent(subj, text, "unix",
					secLevel, 
					Integer.valueOf(date[0]),
					Integer.valueOf(date[1]),
					Integer.valueOf(date[2]),
					Integer.valueOf(date[3]),
					Integer.valueOf(date[4]),
					options);


		//check if url is returned

		if (null != resMap)
		{
			
			String url = "";
			
			for (HashMap.Entry <Object, Object> entry : resMap.entrySet())
			{
				if (entry.getKey().toString().equals("url"))
				{
					res = true;

					url = entry.getValue().toString();
					msg = " File: " + fileName + " was uploaded to url: " +  url;
					
					logger_.info(msg);
					System.out.println(msg);
					
					moveFile (dir, fileName);
				}
			}
			
			if (url.isEmpty())
			{
				msg = " File: " + fileName + " url was not found in servers response.";
				
				logger_.info(msg);
				System.out.println(msg);
			}
			
		}
		

		return res;
	}

	
    public static void main( String[] args )
    {
    	
    	
    	
        

    	String actionStr = "";
    	int action = ACTION_UNKNOWN;
    	boolean stop = false;
    	boolean backdated = false;
    	
    	String user = "";
    	String password = "";
    	
    	String dir = "";    	
    	int secLevel = -1;
    	
    	
    	    	
		Option optnUser = Option.builder("u").
				required(true)
				.hasArg(true)
				.desc("Username. Mandatory value")
				.longOpt("user")
				.build();

    	
		Option optnPassword = Option.builder("p").
				required(true)
				.hasArg(true)
				.desc("Password. Mandatory value")
				.longOpt("password")
				.build();
    	
		Option optnAction = Option.builder("a").
				required(true)
				.hasArg(true)
				.desc("Action. Accepted values: 'post'.  'post' loads data from the specialy crafted files in the upload directory")
				.longOpt("action")
				.build();    	
    	
		Option optnDir = Option.builder("d")
				.required(false)
				.hasArg(true)
				.desc("Full path to upload dir. Must not contain a trailing slash. (Required when using a 'post' action)")
				.longOpt("dir")
				.build();

		Option optnSecLevel = Option.builder("s")
				.required(false)
				.hasArg(true)
				.desc("Security level. Required for 'post' action. Accepted values are: 'public', 'private', 'friends'")
				.longOpt("secLevel")
				.build();
		
		Option optnBackdated = Option.builder("b")
				.required(false)
				.hasArg(false)
				.desc("Post is backdated. Don't add to friends feeds and RSS. (Option for 'post' action)")
				.longOpt("backdated")
				.build();
		
		Option optnHelp = Option.builder("h").
				required(false)
				.hasArg(false)
				.desc("Prints help message")
				.longOpt("help")
				.build();



    	Options options = new Options();
    	
    	options.addOption(optnDir);
    	options.addOption(optnAction);
    	options.addOption(optnSecLevel);
    	options.addOption(optnUser);
    	options.addOption(optnPassword);
    	options.addOption(optnBackdated);
    	options.addOption(optnHelp);
    	
    	
    	CommandLineParser parser = new DefaultParser();
    	try
    	{
    		CommandLine line = parser.parse( options, args );
    		
    		if (line.hasOption("help"))
    		{
    			HelpFormatter formatter = new HelpFormatter();
    			formatter.printHelp( "ljpostj", options );
    			return;
    		}

    		
    		if (line.hasOption("dir"))
    			dir = line.getOptionValue("dir");

    		if (line.hasOption("secLevel"))
    		{
    			secLevel = getSecLevel(line.getOptionValue("secLevel"));
    		}
    		
    		
    		if (line.hasOption("user"))
    			user = line.getOptionValue("user");
    		
    		
    		if (line.hasOption("password"))
    		{
    			password = line.getOptionValue("password");
    		}
    		
    		if (line.hasOption("backdated"))
    		{
    			backdated = true;
    		}
    		
    		msg = "user: '" + user + "'";
    		logger_.info(msg);
    		System.out.println(msg);
    		
    		
    		if (line.hasOption("action"))
    		{
    			actionStr = line.getOptionValue("action");
    			
    			if (ACTION_POST == getAction(actionStr))
    			{
    				System.out.println("action = post\n");
    			} else 
    			{
    				System.out.println("Unknown action: " + actionStr+"\n");
    				stop = true;
    			}
    		}
    	}
    	catch( ParseException exp )
    	{
    		msg = "Parsing failed.  Reason: " + exp.getMessage();
    		System.err.println(msg);
    		logger_.error(msg);
    		
    		stop = true;
    	}



    	if (stop)
    		return;
    	
    	
        try
		{
			lac = new LjApiClient(user, password);
			lac.login();
			
					
		} catch (MalformedURLException e)
		{
			msg = "malformed URL exception. Error: " + e.getMessage();
			System.out.println(msg);
			logger_.error(msg);
			e.printStackTrace();
			stop = true;
		} catch (XmlRpcException e)
		{
			msg = "XmlRpc exception. Error: " + e.getMessage();
			logger_.error(msg);
			System.out.println(msg);
			e.printStackTrace();
			stop = true;
		}
      

        
        if (stop)
        	return;
        
        
        
        ArrayList al = listTxtFiles (dir);
        
        for (int i  =0; i < al.size(); i++)
        {
        	if (stop)
        	{
        		msg = "Exiting.";
        		System.out.println(msg);
        		logger_.info(msg);
        		break;
        	}
        	
        	
        	
        	
        	try
			{
				try
				{
					try
					{
						msg = "Processing '"  + al.get(i) + "' sec level: " + secLevel + " backdated: " + backdated;
						logger_.info(msg);
						System.out.println(msg);

						if (!processFile(dir, (String) al.get(i), secLevel, backdated))
						{
							stop = true;
						}
						
						Thread.sleep(1000);
						
					} catch (InterruptedException e)
					{
						e.printStackTrace();
						msg = "Interrupted exception. Error: " + e.getMessage();
						logger_.error(msg);
						System.out.println(msg);

						stop = true;
					}
				} catch (XmlRpcException e)
				{
					e.printStackTrace();
					msg = "XmlRpc exception. Error: " + e.getMessage();
					logger_.error(msg);
					System.out.println(msg);
					
					stop = true;
				}
			} catch (IOException e)
			{
				e.printStackTrace();
				msg = "IO exception. Error: " + e.getMessage();
				logger_.error(msg);
				System.out.println(msg);
				
				stop = true;
			}
        }

    }
    
}
