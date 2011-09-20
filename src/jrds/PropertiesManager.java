package jrds;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jrds.webapp.ACL;
import jrds.webapp.RolesACL;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * An less ugly class suposed to manage properties
 * should be reworked
 * @author Fabrice Bacchella
 * @version $Revision$,  $Date$
 */
public class PropertiesManager extends Properties {
	private final Logger logger = Logger.getLogger(PropertiesManager.class);

	private final FileFilter filter = new  FileFilter(){
		public boolean accept(File file) {
			return (! file.isHidden()) && (file.isFile() && file.getName().endsWith(".jar"));
		}
	};

	//The default constructor cannot build directories, canact is used to detect that
	private boolean canact = false;

	public PropertiesManager()
	{
		update();
		canact = true;
	}

	public PropertiesManager(File propFile)
	{
		canact = true;
		join(propFile);
		update();
	}

	private int parseInteger(String s) throws NumberFormatException {
		Integer integer = null;
		if (s != null) {
			if (s.startsWith("#")) {
				integer = Integer.valueOf(s.substring(1), 16);
			}
			else
				if (s.startsWith("0x")) {
					integer = Integer.valueOf(s.substring(2), 16);
				}
				else
					if (s.startsWith("0") && s.length() > 1) {
						integer = Integer.valueOf(s.substring(1), 8);
					}
					else {
						integer = Integer.valueOf(s);
					}
	        return integer.intValue();
		}
		throw new NumberFormatException("Parsing null string");
	}

	private boolean parseBoolean(String s)
	{
		s = s.toLowerCase().trim();
		boolean retValue = false;
		if("1".equals(s))
			retValue = true;
		else if("yes".equals(s))
			retValue = true;
		else if("y".equals(s))
			retValue = true;
		else if("true".equals(s))
			retValue = true;
		else if("enable".equals(s))
			retValue = true;
		else if("on".equals(s))
			retValue = true;

		return retValue;
	}

	public void join(URL url) {
		try {
			InputStream inputstream = url.openStream();
			load(inputstream);
			inputstream.close();
		}
		catch (IOException ex) {
			logger.warn("Invalid URL: " + ex.getLocalizedMessage());
		}
	}

	public void join(Properties moreProperties) {
		putAll(moreProperties);
	}

	public void join(File propFile)
	{
		logger.debug("Using propertie file " + propFile.getAbsolutePath());
		InputStream inputstream = null;
		try {
			inputstream = new FileInputStream(propFile);
			load(inputstream);
			inputstream.close();
		} catch (IOException ex) {
			logger.warn("Invalid properties file " + propFile.getAbsolutePath() + ": " + ex.getLocalizedMessage());
		} finally {
		    if(inputstream !=null) {
		        try {
                    inputstream.close();
                } catch (IOException e) {
                }
		    }
		}
	}

	public void join(InputStream propStream)
	{
		try {
			load(propStream);
		} catch (IOException ex) {
			logger.warn("Invalid properties stream " + propStream + ": " + ex);
		}
	}

	private ClassLoader doClassLoader(String extendedclasspath) {
		FileFilter filter = new  FileFilter(){
			public boolean accept(File file) {
				return  (! file.isHidden()) && file.isFile() && file.getName().endsWith(".jar");
			}
		};

		Collection<URI> urls = new HashSet<URI>();

		if(extendedclasspath != null && ! "".equals(extendedclasspath)) {
			for(String pathElement: extendedclasspath.split(";")) {
				logger.debug("Setting class directories to: " + pathElement);

				File path = new File(pathElement);

				if(path.isDirectory()) {
					for(File f: path.listFiles(filter)) {
                        urls.add(f.toURI());
					}
				}
				else if(filter.accept(path)) {
                    urls.add(path.toURI());
				}
			}
		}

		for(URI u: libspath) {
			urls.add(u);
		}

		URL[] arrayUrl = new URL[urls.size()];
		int i=0;
		for(URI u: urls) {
		    try {
                arrayUrl[i++] = u.toURL();
            } catch (MalformedURLException e) {
                logger.error("Invalid URL in libs path: " + u);
            }
		}
		if(logger.isDebugEnabled())
			logger.debug("Internal class loader will look in:" + urls);
		return  URLClassLoader.newInstance(arrayUrl, getClass().getClassLoader());
	}

	private File prepareDir(File dir, boolean autocreate) {
		if(dir == null)
			return null;
		if( ! dir.exists()) {
			if(! autocreate) {
				logger.error(dir + " doesn't exists");
				return null;
			}
			if ( autocreate && canact && !dir.mkdirs()) {
				if(canact)
					logger.error(dir + " doesn't exists and can't be created");
				return null;
			}
		}
		else if( ! dir.isDirectory()) {
			logger.error(dir + " exists but is not a Directory");
			return null;
		}
		else if( ! dir.canWrite()) {
			logger.error(dir + " exists can not be written");
			return null;
		}
		return dir;
	}

	private File prepareDir(String path, boolean autocreate) {
		if(path == null || "".equals(path)) {
			return null;
		}
		File dir = new File(path);
		return prepareDir(dir, autocreate);
	}

	@SuppressWarnings("unchecked")
	public void importSystemProps() {
		String localPropFile = System.getProperty("jrds.propertiesFile");
		if(localPropFile != null)
			join(new File(localPropFile));

		Pattern jrdsPropPattern = Pattern.compile("jrds\\.(.+)");
		Properties p = System.getProperties();
		for(String name: Collections.list((Enumeration<String>) p.propertyNames() )) {
			Matcher m = jrdsPropPattern.matcher(name);
			if(m.matches()) {
				String prop = System.getProperty(name);
				if(prop != null)
					setProperty(m.group(1), prop);
			}
		}		
	}

	public void update()
	{

		Locale.setDefault(new Locale("POSIX"));

		boolean nologging = parseBoolean(getProperty("nologging", "false"));
		String log4jXmlFile = getProperty("log4jxmlfile", "");
		String log4jPropFile = getProperty("log4jpropfile", "");
		if(log4jXmlFile != null && ! "".equals(log4jXmlFile.trim())) {
			org.apache.log4j.xml.DOMConfigurator.configure(log4jXmlFile.trim());
			nologging = true;
		}
		else if(log4jPropFile != null && ! "".equals(log4jPropFile.trim())) {
			org.apache.log4j.PropertyConfigurator.configure(log4jPropFile.trim());
			nologging = true;
		}

		if(! nologging) {
			for(String ls: new String[]{ "trace", "debug", "info", "error", "fatal", "warn"}) {
				Level l = Level.toLevel(ls);
				String param = getProperty("log." + ls, "");
				if(! "".equals(param)) {
					List<String> loggerList = Arrays.asList(param.split(","));
					loglevels.put(l, loggerList);
				}

			}
			loglevel = Level.toLevel(getProperty("loglevel", "info"));
			logfile = getProperty("logfile");

			//Let's configure the log fast
			try {
				jrds.JrdsLoggerConfiguration.configure(this);
			} catch (IOException e1) {
				logger.error("Unable to set log file to " + this.logfile + ": " + e1);
			}
		}
		legacymode = parseBoolean(getProperty("legacymode", "1"));

		//Directories configuration
		autocreate = parseBoolean(getProperty("autocreate", "false"));
		configdir = prepareDir(getProperty("configdir"), autocreate);
		rrddir = prepareDir(getProperty("rrddir"), autocreate);
		//Different place to find the temp directory
		tmpdir = prepareDir(getProperty("tmpdir"), autocreate);
		if(tmpdir == null)
			tmpdir = prepareDir(System.getProperty("javax.servlet.context.tempdir"), false);
		if(tmpdir == null) {
			String tmpDirPath = System.getProperty("java.io.tmpdir");
			if(tmpDirPath != null && ! "".equals(tmpDirPath))
				tmpdir = prepareDir(new File(tmpDirPath, "jrds"), true);
		}
		if(tmpdir == null) {
			throw new RuntimeException("No temp dir defined");
		}

		step = parseInteger(getProperty("step", "300"));
		timeout = parseInteger(getProperty("timeout", "30"));
		collectorThreads = parseInteger(getProperty("collectorThreads", "1"));
		dbPoolSize = parseInteger(getProperty("dbPoolSize", "10")) + collectorThreads;
		syncPeriod = parseInteger(getProperty("syncPeriod", Integer.toString(step / 2)));
		String libspathString = getProperty("libspath", "");
		if(! "".equals(libspathString)) {
			for(String libName: libspathString.split(";")) {
				File lib = new File(libName);

				boolean noJarDir = true;
				if(lib.isDirectory()) {
					File[] foundFiles = lib.listFiles(filter);
					if(foundFiles == null) {
						logger.error("Failed to search in " + lib);
						continue;
					}
					for(File f: foundFiles) {
                        libspath.add(f.toURI());
                        noJarDir = false;
					}
				}

				//If a jar was found previously, it's not a source directory, don't add it
				if(lib.isFile() || (lib.isDirectory() && noJarDir))
                    libspath.add(lib.toURI());
			}
		}
		extensionClassLoader = doClassLoader(getProperty("classpath", ""));

		rrdbackend = getProperty("rrdbackend", "NIO");
		
		// We search for the tabs list in the property tab
		// spaces are non-significant
		String tabsList = getProperty("tabs");
		if(tabsList !=null && ! "".equals(tabsList.trim())) {
		    this.tabsList = new ArrayList<String>();
		    for(String tab: tabsList.split(",")) {
		        this.tabsList.add(tab.trim());
		    }
		}

		security = parseBoolean(getProperty("security", "false"));
		if(security) {
			userfile = getProperty("userfile", "users.properties");

			adminrole = getProperty("adminrole", adminrole);
			adminACL = new ACL.AdminACL(adminrole);
			
			String  defaultRolesString = getProperty("defaultroles", "ANONYMOUS");
			defaultRoles = new HashSet<String>();
			for(String aRole:  defaultRolesString.split(",") ) {
				defaultRoles.add(aRole.trim());
			}
			defaultACL = new RolesACL(defaultRoles);
			defaultACL = defaultACL.join(adminACL);
			
			logger.debug(jrds.Util.delayedFormatString("Admin ACL is %s", adminACL));
			logger.debug(jrds.Util.delayedFormatString("Default ACL is %s", defaultACL));
		}
		
		readonly = parseBoolean(getProperty("readonly", "0"));

	}

	public File configdir;
	public File rrddir;
	public File tmpdir;
	public String urlpngroot;
	public String logfile;
	public int step;
	public int collectorThreads;
	public int dbPoolSize;
	public int syncPeriod;
	public final Set<URI> libspath = new HashSet<URI>();
	public ClassLoader extensionClassLoader;
	public final Map<Level, List<String>> loglevels = new HashMap<Level, List<String>>();
	public Level loglevel;
	public boolean legacymode;
	public boolean autocreate;
	public int timeout;
	public String rrdbackend;
	public boolean security = false;
	public String userfile = "/tmp/bidule";
	public Set<String> defaultRoles = Collections.emptySet();
	public String adminrole = "admin";
	public ACL defaultACL = ACL.ALLOWEDACL;
	public ACL adminACL = ACL.ALLOWEDACL;
	public boolean readonly = false;
	public static final String FILTERTAB = "filtertab";
    public static final String CUSTOMGRAPHTAB = "customgraph";
    public static final String SUMSTAB = "sumstab";
    public static final String SERVICESTAB = "servicestab";
    public static final String VIEWSTAB = "viewstab";
    public static final String HOSTSTAB = "hoststab";
    public static final String TAGSTAB = "tagstab";
    public static final String ADMINTAB = "adminTab";

	public List<String> tabsList = Arrays.asList(FILTERTAB, CUSTOMGRAPHTAB, "@", SUMSTAB, SERVICESTAB, VIEWSTAB, HOSTSTAB, TAGSTAB, ADMINTAB);
}
