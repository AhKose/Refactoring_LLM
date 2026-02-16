/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.descartes.teastore.persistence.repository;

import java.util.HashMap;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for managing the EMF singleton.
 * @author Jóakim von Kistowski
 *
 */
final class EMFManager {

	private static EntityManagerFactory emf = null; 
	private static HashMap<String, String> persistenceProperties = null;
	
	private static final Logger LOG = LoggerFactory.getLogger(EMFManager.class);
	
	private static final String DRIVER_PROPERTY = "jakarta.persistence.jdbc.driver";
	private static final String IN_MEMORY_DRIVER_VALUE = "org.hsqldb.jdbcDriver";
	private static final String JDBC_URL_PROPERTY = "jakarta.persistence.jdbc.url";
	private static final String IN_MEMORY_JDBC_URL_VALUE = "jdbc:hsqldb:mem:test";
	private static final String USER_PROPERTY = "jakarta.persistence.jdbc.user";
	private static final String IN_MEMORY_USER_VALUE = "sa";
	private static final String PASSWORD_PROPERTY = "jakarta.persistence.jdbc.password";
	private static final String IN_MEMORY_PASSWORD_VALUE = "";
	
	private static final String MYSQL_URL_PREFIX = "jdbc:mysql://";
	private static final String MYSQL_URL_POSTFIX = "/teadb";
	private static final String MYSQL_DEFAULT_HOST = "localhost";
	private static final String MYSQL_DEFAULT_PORT = "3306";
	
	private EMFManager() {
		
	}
	
	/**
	 * (Re-)configure the entity manager factory using a set of persistence properties.
	 * Use to change database/user at run-time.
	 * Properties are kept, even if the database is reset.
	 * @param persistenceProperties The persistence properties.
	 */
	static void configureEMFWithProperties(HashMap<String, String> persistenceProperties) {
		EMFManager.persistenceProperties = persistenceProperties;
		clearEMF();
	}
	
	/**
	 * Get the entity manager factory.
	 * @return The entity manager factory.
	 */
	static synchronized EntityManagerFactory getEMF() {
		if (emf == null) {
			HashMap<String, String> persistenceProperties = EMFManager.persistenceProperties;
			if (persistenceProperties == null) {
				persistenceProperties = createPersistencePropertiesFromJavaEnv();
			}
			emf = Persistence.createEntityManagerFactory("tools.descartes.teastore.persistence",
					persistenceProperties);
			
		}
		return emf;
	}
	
	/**
	 * Closes and deletes EMF to be reinitialized later.
	 */
	static void clearEMF() {
		if (emf != null) {
			emf.close();
		}
		emf = null;
	}
	
	private static HashMap<String, String> createPersistencePropertiesFromJavaEnv() {
		boolean useInMemoryDB = resolveInMemoryDbFlag();
		if (useInMemoryDB) {
			return createPersistencePropertieForInMemoryDB();
		}
		return createMySqlPersistenceProperties();
	}

	private static boolean resolveInMemoryDbFlag() {
		try {
			boolean useInMemoryDB = Boolean.parseBoolean(
					(String) new InitialContext().lookup("java:comp/env/inMemoryDB"));
			if (useInMemoryDB) {
				LOG.info("Using in-memory development database. Set Java env \"inMemoryDB\" to false to use MariaDB.");
			} else {
				LOG.info("\"inMemoryDB\" set to false. Using MariaDB/MySQL.");
			}
			return useInMemoryDB;
		} catch (NamingException e) {
			LOG.info("Using MySQL/MariaDB database.");
			return false;
		}
	}

	private static HashMap<String, String> createMySqlPersistenceProperties() {
		HashMap<String, String> persistenceProperties = new HashMap<String, String>();
		String dbhost = lookupJndiProperty("java:comp/env/databaseHost", 
				"Database host not set. Falling back to default host at " + MYSQL_DEFAULT_HOST + ".");
		String dbport = lookupJndiProperty("java:comp/env/databasePort", 
				"Database port not set. Falling back to default host at " + MYSQL_DEFAULT_PORT + ".");
		
		if (dbhost != null || dbport != null) {
			String url = buildMySqlUrl(dbhost, dbport);
			LOG.info("Setting jdbc url to \"" + url + "\".");
			persistenceProperties.put("jakarta.persistence.jdbc.url", url);
		}
		return persistenceProperties;
	}

	private static String lookupJndiProperty(String jndiName, String fallbackMessage) {
		try {
			return (String) new InitialContext().lookup(jndiName);
		} catch (NamingException e) {
			LOG.info(fallbackMessage);
			return null;
		}
	}

	private static String buildMySqlUrl(String dbhost, String dbport) {
		StringBuilder url = new StringBuilder(MYSQL_URL_PREFIX);
		url.append(dbhost != null ? dbhost : MYSQL_DEFAULT_HOST);
		url.append(":");
		url.append(dbport != null ? dbport : MYSQL_DEFAULT_PORT);
		url.append(MYSQL_URL_POSTFIX);
		return url.toString();
	}
	
	/**
	 * Create a persistence property map to configure the EMFManager to use an in-memory database
	 * instead of the usual MySQL/MariaDB database.
	 * @return The configuration. Pass this to {@link #configureEMFWithProperties(HashMap)}.
	 */
	static HashMap<String, String> createPersistencePropertieForInMemoryDB() {
		HashMap<String, String> persistenceProperties = new HashMap<String, String>();
		persistenceProperties.put(DRIVER_PROPERTY, IN_MEMORY_DRIVER_VALUE);
		persistenceProperties.put(JDBC_URL_PROPERTY, IN_MEMORY_JDBC_URL_VALUE);
		persistenceProperties.put(USER_PROPERTY, IN_MEMORY_USER_VALUE);
		persistenceProperties.put(PASSWORD_PROPERTY, IN_MEMORY_PASSWORD_VALUE);
		return persistenceProperties;
	}
}
