package com.datadog.profiling.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.time.Duration;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadog.profiling.controller.BadConfigurationException;
import com.datadog.profiling.controller.ProfilingSystem;
import com.datadog.profiling.controller.UnsupportedEnvironmentException;
import com.datadog.profiling.uploader.ChunkUploader;
import com.squareup.okhttp.Credentials;

/**
 * Simple agent wrapper for starting the profiling agent from the command-line, without requiring
 * the APM agent. This makes it possible to run the profiling agent stand-alone. Of course, this
 * also means no contextual events from the tracing will be present.
 */
public class ProfilingAgent {
	private static final String KEY_DURATION = "duration";
	private static final String KEY_PERIOD = "period";
	private static final String KEY_DELAY = "delay";
	private static final String KEY_URL = "url";
	private static final String KEY_API_KEY = "apikey";
	private static final String KEY_USER_NAME = "username";
	private static final String KEY_PASSWORD = "password";

	private static final int DEFAULT_DURATION = 60;
	private static final int DEFAULT_PERIOD = 3600;
	private static final int DEFAULT_DELAY = 30;
	private static final String DEFAULT_PROPERTIES = "default.properties";
	private static final String DEFAULT_URL = "http://localhost/9191";

	// Overkill to make these volatile?
	private static ProfilingSystem profiler;
	private static ChunkUploader uploader;

	/**
	 * Called when starting from the command line.
	 */
	public static void premain(String args, Instrumentation instrumentation) {
		Properties props = initProperties(args);
		initialize(props);
	}

	/**
	 * Called when loaded and run from attach. If the agent is already initialized (from either the
	 * command line, or dynamically loaded through attach, no action will be taken.
	 */
	public static void agentmain(String args, Instrumentation instrumentation) {
		Properties props = initProperties(args);
		initialize(props);
	}

	private static synchronized void initialize(Properties props) {
		if (profiler == null) {
			uploader = new ChunkUploader(getString(props, KEY_URL, DEFAULT_URL), getString(props, KEY_API_KEY, ""),
					Credentials.basic(getString(props, KEY_USER_NAME, ""), getString(props, KEY_PASSWORD, "")));
			try {
				profiler = new ProfilingSystem(uploader.getRecordingDataListener(),
						Duration.ofSeconds(getInt(props, KEY_DELAY, DEFAULT_DELAY)),
						Duration.ofSeconds(getInt(props, KEY_PERIOD, DEFAULT_PERIOD)),
						Duration.ofSeconds(getInt(props, KEY_DURATION, DEFAULT_DURATION)));
				profiler.start();
			} catch (UnsupportedEnvironmentException | IOException | BadConfigurationException e) {
				getLogger().warn("Failed to initialize profiling agent!", e);
			}
		}
	}

	private static Properties initProperties(String args) {
		Properties props = new Properties();
		if (args == null || args.trim().isEmpty()) {
			loadDefaultProperties(props);
		} else {
			File propsFile = new File(args);
			if (!propsFile.exists()) {
				getLogger().warn("The agent settings file {} could not be found! Will go with the defaults!", args);
				loadDefaultProperties(props);
			} else {
				try (FileInputStream in = new FileInputStream(propsFile)) {
					props.load(in);
				} catch (Exception e) {
					getLogger().warn(
							"Failed to load agent settings from {}. File format error? Going with the defaults.", args);
					loadDefaultProperties(props);
				}
			}
		}
		return props;
	}

	private static void loadDefaultProperties(Properties props) {
		try {
			props.load(ProfilingAgent.class.getClassLoader().getResourceAsStream(DEFAULT_PROPERTIES));
		} catch (IOException e) {
			// Should never happen! Build fail!
			getLogger().error("Failure to load default properties!", e);
		}
	}

	private static int getInt(Properties props, String key, int defaultValue) {
		String val = props.getProperty(key);
		if (val != null) {
			try {
				return Integer.valueOf(val);
			} catch (NumberFormatException e) {
				getLogger().warn("Could not parse key {}. Will go with default {}.", key, defaultValue);
				return defaultValue;
			}
		}
		getLogger().info("Could not find key {}. Will go with default {}.", key, defaultValue);
		return defaultValue;
	}

	private static String getString(Properties props, String key, String defaultValue) {
		String val = props.getProperty(key);
		if (val == null) {
			return defaultValue;
		}
		return val;
	}

	private static Logger getLogger() {
		return LoggerFactory.getLogger(ProfilingAgent.class);
	}
}
