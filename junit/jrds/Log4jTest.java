package jrds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Test;

public class Log4jTest {
	static final private String TANAME = "TANAME";
	static final private List<LoggingEvent> logs = new ArrayList<LoggingEvent>();
	static final Appender ta = new AppenderSkeleton() {
		@Override
		protected void append(LoggingEvent arg0) {
			logs.add(arg0);
		}
		@Override
		public void close() {
			logs.clear();
		}
		@Override
		public boolean requiresLayout() {
			return false;
		}
	};

	@Test
	public void testConfiguration() throws IOException {
		LogManager.getLoggerRepository().resetConfiguration();
		JrdsLoggerConfiguration.initLog4J();
		Logger jrdsLogger = LogManager.getLoggerRepository().exists("jrds");
		Assert.assertNotNull(jrdsLogger);
		PropertiesManager pm = new PropertiesManager();
		JrdsLoggerConfiguration.configure(pm);
		Assert.assertEquals(pm.loglevel, jrdsLogger.getLevel());
	}
	
	@Test
	public void testOutsideConfiguration() throws IOException {
		LogManager.getLoggerRepository().resetConfiguration();
		Logger l = Logger.getLogger("jrds");
		l.setAdditivity(false);
		ta.setName(TANAME);
		JrdsLoggerConfiguration.putAppender(ta);
		Logger.getRootLogger().addAppender(ta);
		JrdsLoggerConfiguration.initLog4J();
		Assert.assertNotNull(LogManager.getLoggerRepository().exists("jrds"));
		JrdsLoggerConfiguration.configure(new PropertiesManager());
		l.error("A message");
		l.debug("A debug message");
		Assert.assertEquals(1, logs.size());
	}
}
